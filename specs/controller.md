# app-controller Spec

The controller is the phone-side Android app. It has two screens — Connect and Player — and a foreground service that owns and maintains the Bluetooth connection so it survives switching apps.

---

## 1. Components

| Component | Type | Role |
|---|---|---|
| `MainActivity` | `ComponentActivity` | Single-activity host for all screens |
| `ConnectScreen` | Composable | Paired device list and connect flow |
| `PlayerScreen` | Composable | Now-playing UI with controls |
| `ControllerService` | Foreground `Service` | Owns `BtClient`, maintains the RFCOMM connection, updates `ConnectionRepository`, shows notification |
| `BtClient` | class | Owns the RFCOMM socket, exposes `Flow<Update>` and `send()` |
| `ConnectionRepository` | Singleton object | Shared `StateFlow`s written by `ControllerService`, read by `PlayerViewModel` |
| `PlayerViewModel` | `ViewModel` | Reads from `ConnectionRepository`; sends commands to `ControllerService` via Intent |

---

## 2. Connection State Machine

```
            ┌──────────────────────────────────────┐
            │  connection refused / timeout        │
            ▼                                      │
       Disconnected ── user taps device ──► Connecting
            ▲                                      │
            │                                      │ socket open
            │  socket IOException / user disconnects│
            └────────────────────────────────── Connected
```

```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Failed(val deviceName: String, val reason: String) : ConnectionState()
}
```

`Failed` is transient. The UI shows the error; once the user taps *Reconnect*, state returns to `Disconnected` and the service stops.

---

## 3. ConnectionRepository

A singleton object in `app-controller`. `ControllerService` writes to it; `PlayerViewModel` reads from it. This decouples the service lifecycle from the UI lifecycle.

```kotlin
object ConnectionRepository {
    val connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(Disconnected)
    val trackInfo:       MutableStateFlow<TrackInfo?>      = MutableStateFlow(null)
    val playbackState:   MutableStateFlow<PlaybackState?>  = MutableStateFlow(null)
    val playbackAnchor:  MutableStateFlow<PlaybackAnchor?> = MutableStateFlow(null)
}

data class PlaybackAnchor(
    val positionMs: Long,
    val anchoredAt: Long    // System.currentTimeMillis() when this was recorded
)
```

---

## 4. ControllerService

### 4.1 Service Lifecycle

- **Started** by `PlayerViewModel.connect()` via `startForegroundService(Intent(ACTION_CONNECT, ...))`.
- **Stopped** by calling `stopSelf()` after a disconnect or connection failure. Also stopped when the user taps *Disconnect* on the notification.
- Survives the user pressing home, switching apps, or swiping the app from recents.
- The persistent notification is the user's signal that a connection is active.

### 4.2 Intent Actions (received via onStartCommand)

| Action constant | Extras | Behavior |
|---|---|---|
| `ACTION_CONNECT` | `EXTRA_DEVICE_ADDRESS`, `EXTRA_DEVICE_NAME` | Start `BtClient.connect()` to the given device |
| `ACTION_PLAY_PAUSE` | — | Forward `Command.PlayPause` to `BtClient.send()` |
| `ACTION_NEXT` | — | Forward `Command.Next` to `BtClient.send()` |
| `ACTION_PREVIOUS` | — | Forward `Command.Previous` to `BtClient.send()` |
| `ACTION_DISCONNECT` | — | Call `BtClient.close()`, reset `ConnectionRepository`, call `stopSelf()` |

### 4.3 onCreate

1. Post the initial foreground notification (content determined by current `ConnectionRepository.connectionState`).
2. Call `startForeground()` immediately — Android 12+ requires this within 5 seconds of `startForegroundService`.

### 4.4 onDestroy

1. Cancel all coroutines.
2. Call `BtClient.close()`.
3. Reset `ConnectionRepository` to `Disconnected` and null track/state.
4. Cancel the notification.

### 4.5 Connection Behavior (ACTION_CONNECT)

1. Write `Connecting(deviceName)` to `ConnectionRepository.connectionState`.
2. Update notification.
3. Launch a coroutine: call `BtClient.connect(device)`.
   - On success: write `Connected(deviceName)` to repository. Update notification. Start collecting `BtClient.updates`.
   - On `IOException` or timeout: write `Failed(deviceName, reason)` to repository. Update notification. Call `stopSelf()`.

### 4.6 Collecting BtClient.updates

| Received update | Action |
|---|---|
| `Update.Track(info)` | Write `trackInfo = info` to repository. Write `playbackAnchor = null`. Update notification. |
| `Update.State(state)` | Write `playbackState = state` to repository. Write `playbackAnchor = PlaybackAnchor(state.positionMs, currentTimeMs)`. Update notification. |
| Flow closes (IOException) | Write `Failed(deviceName, "Connection lost")` to repository. Update notification. Call `stopSelf()`. |

### 4.7 Command Forwarding (ACTION_PLAY_PAUSE / NEXT / PREVIOUS)

- If not `Connected`: no-op.
- Call `BtClient.send(command)`.
- If `send` throws `IOException`: write `Failed(deviceName, "Connection lost")` to repository. Call `stopSelf()`.

---

## 5. Notification

The notification is visible whenever `ControllerService` is running. It is the user's indicator that the connection is active and their control point when the app is not on screen.

### 5.1 Content by State

| `connectionState` | Title | Text | Actions |
|---|---|---|---|
| `Connecting(name)` | *DAP Controller* | *Connecting to \<name\>…* | — |
| `Connected`, no track | *DAP Controller* | *Connected · Waiting for playback…* | Disconnect |
| `Connected`, playing | *\<title\>* | *\<artist\>* | ⏮  ⏸  ⏭  Disconnect |
| `Connected`, paused | *\<title\>* | *\<artist\>* | ⏮  ▶  ⏭  Disconnect |
| `Failed(name, reason)` | *DAP Controller* | *\<reason\>* | Dismiss |

### 5.2 Notification Actions

Each action button is a `PendingIntent` that calls `startService()` on `ControllerService` with the corresponding action:

| Button | Intent action |
|---|---|
| ⏮ | `ACTION_PREVIOUS` |
| ▶ / ⏸ | `ACTION_PLAY_PAUSE` |
| ⏭ | `ACTION_NEXT` |
| Disconnect | `ACTION_DISCONNECT` |
| Dismiss (on Failed) | `ACTION_DISCONNECT` |

### 5.3 Update Timing

The notification is updated whenever:
- `connectionState` changes.
- `trackInfo` changes (track name in title).
- `playbackState.isPlaying` toggles (play ↔ pause icon).

---

## 6. BtClient

### 6.1 connect(device: BluetoothDevice) *(suspend)*

1. Create an RFCOMM socket via `device.createRfcommSocketToServiceRecord(DAP_BT_UUID)`.
2. Call `socket.connect()` inside `withTimeout(10_000)`.
3. If `TimeoutCancellationException`: throw `IOException("Connection timed out")`.
4. On success: start the read coroutine that populates `updates`.

### 6.2 updates: Flow\<Update\>

- Emits each successfully parsed `Update` from the socket.
- Drops malformed or unrecognised lines per the core parsing rules — the flow does **not** close on a bad line.
- Closes (completes) when the socket is closed or a read `IOException` occurs.

### 6.3 send(command: Command) *(suspend)*

- Serializes `command` with `MessageSerializer.serialize(command)`.
- Writes to the socket's output stream.
- Throws `IOException` on write failure.

### 6.4 close()

- Closes the socket. Idempotent.
- Causes `updates` to complete.

---

## 7. PlayerViewModel

The ViewModel is now thin — it reads from `ConnectionRepository` and delegates all actions to `ControllerService` via Intent.

### 7.1 Exposed StateFlows

Collected directly from `ConnectionRepository`:

| Name | Type |
|---|---|
| `connectionState` | `StateFlow<ConnectionState>` |
| `trackInfo` | `StateFlow<TrackInfo?>` |
| `playbackState` | `StateFlow<PlaybackState?>` |
| `playbackAnchor` | `StateFlow<PlaybackAnchor?>` |

### 7.2 connect(device: BluetoothDevice)

- If `connectionState` is already `Connecting` or `Connected`: no-op.
- Calls `startForegroundService(Intent(ACTION_CONNECT).putExtra(EXTRA_DEVICE_ADDRESS, ...).putExtra(EXTRA_DEVICE_NAME, ...))`.

### 7.3 disconnect()

- Calls `startService(Intent(ACTION_DISCONNECT))`.

### 7.4 sendCommand(command: Command)

- If `connectionState` is not `Connected`: no-op.
- Maps command to the corresponding Intent action and calls `startService()`.

---

## 8. ConnectScreen

### 8.1 Entry Sequence

1. Check `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions.
   - Not granted → show rationale and request both together.
   - Permanently denied → show *"Open Settings"* button (links to app permission settings). Device list not shown.
2. Check `BluetoothAdapter.isEnabled`.
   - Off → show *"Bluetooth is off"* + button that opens `ACTION_BLUETOOTH_SETTINGS`. Device list not shown.
3. Both pass → load `BluetoothAdapter.bondedDevices` and show the list.

### 8.2 Device List

- Source: `BluetoothAdapter.bondedDevices` (no active scan).
- Each row: device name + address.
- Tapping a row calls `viewModel.connect(device)`.
- Empty list → show *"No paired devices found. Pair your DAP in Bluetooth settings."*

### 8.3 State Reactions

| `connectionState` | ConnectScreen behavior |
|---|---|
| `Connecting(name)` | Replace list with a centered spinner and *"Connecting to \<name\>…"* |
| `Connected` | Navigate to `PlayerScreen` |
| `Failed(name, reason)` | Hide spinner, show snackbar with `reason`, return to device list |

---

## 9. PlayerScreen

### 9.1 Layout

```
┌────────────────────────────────┐
│  ● Connected: My DAP           │  ← connection indicator + device name
│                                │
│    [ Album Art — 200×200 dp ]  │  ← placeholder icon if albumArtBase64 is null
│                                │
│    Song Title                  │
│    Artist · Album              │
│                                │
│    1:23 ──────────────── 4:00  │
│         ████████░░░░░░░        │  ← LinearProgressIndicator (not interactive)
│                                │
│    [⏮]        [⏯]       [⏭]  │  ← 48 dp icon buttons, equal spacing
│                                │
└────────────────────────────────┘
```

### 9.2 Waiting State

When `trackInfo` is `null`: show *"Waiting for playback…"* centered in place of the album art, title, artist, and controls. Connection indicator stays visible.

### 9.3 Progress Bar

Computed locally every 500 ms:

```
if playbackState.isPlaying AND anchor != null:
    displayPositionMs = anchor.positionMs + (currentTimeMs - anchor.anchoredAt)
else:
    displayPositionMs = playbackState?.positionMs ?: 0

displayPositionMs = clamp(displayPositionMs, 0, trackInfo.durationMs)
fraction = displayPositionMs / trackInfo.durationMs
```

A new `PLAYBACK_STATE` message replaces `playbackAnchor` and resyncs the bar immediately. Time labels show `mm:ss` format.

### 9.4 Play/Pause Button Icon

- `isPlaying == true` → show pause icon.
- `isPlaying == false` or `playbackState == null` → show play icon.

### 9.5 Button Behavior

| Button | Command | Disabled when |
|---|---|---|
| ⏮ | `Command.Previous` | `trackInfo == null` |
| ⏯ | `Command.PlayPause` | `trackInfo == null` |
| ⏭ | `Command.Next` | `trackInfo == null` |

Each tap fires exactly one command. No debounce.

### 9.6 Disconnect Handling

When `connectionState` becomes `Failed` or `Disconnected` while on `PlayerScreen`:

1. Show a non-dismissible banner: *"Connection lost"*.
2. Show a *"Reconnect"* button that calls `viewModel.disconnect()` and navigates back to `ConnectScreen`.
3. Control buttons are disabled (not hidden) while the banner is visible.

---

## 10. Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

`BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` are runtime permissions on Android 12+. Requested together at the start of `ConnectScreen`. `FOREGROUND_SERVICE` permissions are granted at install time.
