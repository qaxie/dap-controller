# app-companion Spec

The companion runs on the Android DAP DAP. It has a minimal one-screen UI for starting and stopping the service, and a foreground service that bridges the active `MediaSession` on the DAP to a Bluetooth RFCOMM socket. It works with any app that publishes a `MediaSession` (Auxio, Spotify, YouTube, etc.).

The service **only runs when explicitly started by the user**. It never starts automatically. When it is running, a persistent notification is always visible so the user knows it is active. The user has full control to start and stop it at any time.

---

## 1. Components

| Component | Type | Role |
|---|---|---|
| `MainActivity` | `ComponentActivity` | Minimal launcher screen — shows service status, start/stop button |
| `CompanionService` | Foreground `Service` | Top-level owner of the BT server and Auxio session lifecycle |
| `BtServer` | class | Owns the `BluetoothServerSocket`, manages the read/write loops |
| `MediaSessionBridge` | class | Finds the active `MediaSession`, wraps `MediaController`, translates callbacks to protocol messages |
| `AlbumArtEncoder` | object | Scales and JPEG-encodes a `Bitmap` to a base64 string |
| `CompanionNotificationListener` | `NotificationListenerService` | Grants permission to call `MediaSessionManager.getActiveSessions()` |

---

## 2. Service Lifecycle

### 2.1 How to Start

**Only one way:** open the DAP Companion app on the Android DAP and tap *Start*. The service never starts on its own — not on boot, not in the background, not for any other reason.

### 2.2 How to Stop

Two ways, both explicit:

1. **Via the launcher activity** — open the DAP Companion app and tap *Stop*.
2. **Via the foreground notification** — tap the *Stop* action button directly on the notification in the DAP's notification shade. No need to open the app.

### 2.3 State After Install

- Service is **not running**.
- No background activity of any kind.
- User opens the app, completes Notification Access setup if not done, taps *Start*.

### 2.4 State After the DAP Reboots

- Service is **not running**. There is no boot receiver.
- User must open the app and tap *Start* again if they want to use it.

### 2.5 State When the Phone Disconnects

- Service **keeps running**, waiting for the next phone connection.
- The notification updates to *"Waiting for connection…"*.
- The user can leave the service running if they plan to reconnect soon, or stop it via the notification if they are done.

---

## 3. MainActivity (Launcher Screen)

The only purpose of this screen is service control and status visibility.

```
┌────────────────────────────────┐
│  DAP Companion                 │
│                                │
│  Status:  ● Running            │  ← or ○ Stopped
│  ────────────────────────────  │
│  Waiting for connection…       │  ← mirrors the notification text
│                                │
│  ⚠ Notification access not     │  ← shown only when access is missing
│    granted. Tap to fix.        │
│                                │
│           [ Stop ]             │  ← label toggles to [Start] when stopped
│                                │
└────────────────────────────────┘
```

### 3.1 Status Indicator

- Reads whether `CompanionService` is currently running (via a bound service connection or a `StateFlow` in a shared object).
- Green dot + *"Running"* when the service is active.
- Grey dot + *"Stopped"* when it is not.

### 3.2 Detail Line

Mirrors the current notification text so the user can see the same status information without pulling down the shade.

### 3.3 Notification Access Warning

- On entry, check `NotificationManagerCompat.isNotificationListenerServiceEnabled(context, CompanionNotificationListener)`.
- If not enabled: show the warning banner. Tapping it opens `ACTION_NOTIFICATION_LISTENER_SETTINGS`.
- If enabled: hide the banner.

### 3.4 Start / Stop Button

- **When stopped:** label is *"Start"*. Tapping calls `startForegroundService(Intent(context, CompanionService::class.java))`.
- **When running:** label is *"Stop"*. Tapping calls `stopService(Intent(context, CompanionService::class.java))`.
- The button is **disabled** while Notification Access is not granted (starting the service without it would make the session bridge non-functional).

---

## 4. Foreground Notification

The notification is the primary way to observe companion status without opening the app. It is updated whenever the service state changes.

### 4.1 Notification Content

| Service state | Notification title | Notification text |
|---|---|---|
| Listening, no phone connected | *DAP Companion* | *Waiting for connection…* |
| Phone connected, no active media session | *DAP Companion* | *Connected — waiting for playback…* |
| Phone connected, media playing | *DAP Companion* | *Connected · \<title\> — \<artist\>* |
| Phone connected, media paused | *DAP Companion* | *Paused · \<title\> — \<artist\>* |
| Media session lost (phone still connected) | *DAP Companion* | *Connected — waiting for playback…* |

### 4.2 Notification Action

A single *Stop* action button is shown on the notification. Tapping it stops `CompanionService`.

### 4.3 Update Timing

The notification is updated:
- When a phone connects or disconnects.
- When `onMetadataChanged` fires (track change).
- When `onPlaybackStateChanged` fires (play ↔ pause toggle).
- When the Auxio session is found or destroyed.

---

## 5. CompanionService

### 5.1 onCreate

1. Post the initial foreground notification (*"Waiting for connection…"*).
2. Call `startForeground()`.
3. Instantiate `BtServer` and call `BtServer.open()` to create the `BluetoothServerSocket`.
4. Launch a coroutine that runs `BtServer.acceptLoop()`.

### 5.2 onDestroy

1. Update notification text to *"Stopped"* briefly, then cancel the notification.
2. Cancel all coroutines owned by the service.
3. Call `BtServer.close()`.
4. Call `MediaSessionBridge.detach()` if currently attached.

### 5.3 Accept Loop

`BtServer.acceptLoop()` runs until the server socket is closed:

1. Block on `serverSocket.accept()`.
2. When a client connects:
   a. Close any previously active client socket.
   b. Pass the new socket to the active connection handler.
   c. Update notification to *"Connected — waiting for Auxio…"*.
   d. Call `MediaSessionBridge.attach()`.
      - If it returns `true`: send current `TRACK_INFO` and `PLAYBACK_STATE` immediately (skip if null). Update notification to reflect current track/state.
      - If it returns `false` (no active media session): enter a 2-second retry loop — call `MediaSessionBridge.attach()` every 2 seconds until it returns `true` or the client socket closes.
3. Start the command read loop on the client socket.

### 5.4 Command Read Loop

Reads newline-delimited lines from the client socket and handles each:

| Received command | Action |
|---|---|
| `Command.PlayPause` | Call `play()` if currently paused; call `pause()` if currently playing |
| `Command.Next` | Call `mediaController.transportControls.skipToNext()` |
| `Command.Previous` | Call `mediaController.transportControls.skipToPrevious()` |

On `IOException` from the read: close the client socket, call `MediaSessionBridge.detach()`, update notification to *"Waiting for connection…"*, return to `acceptLoop()`.

---

## 6. MediaSessionBridge

### 6.1 attach(): Boolean

1. Call `MediaSessionManager.getActiveSessions(ComponentName(context, CompanionNotificationListener::class.java))`.
2. Pick the best session: prefer the first session whose playback state is `STATE_PLAYING`; fall back to the first session in the list (most recently active).
3. If a session is found:
   - Create a `MediaController` for that session.
   - Register a `Callback` (see §6.3).
   - Store the controller reference.
   - Return `true`.
4. If no sessions exist: return `false`. No callback is registered.

### 6.2 detach()

- Unregister the `MediaController.Callback`.
- Clear the stored `MediaController` reference.
- No-op if not currently attached.

### 6.3 MediaController.Callback Reactions

| Callback | Action |
|---|---|
| `onMetadataChanged(metadata)` | Build `TrackInfo` from metadata; call `AlbumArtEncoder.encodeOrNull()` for art; send `Update.Track` via `BtServer.send()`; update notification. |
| `onPlaybackStateChanged(state)` | Build `PlaybackState(isPlaying, positionMs)` from state; send `Update.State` via `BtServer.send()`; update notification. |
| `onSessionDestroyed()` | Call `detach()`; notify `CompanionService` to begin the 2-second retry loop; update notification to *"Connected — waiting for playback…"*. |

### 6.4 getCurrentTrackInfo(): TrackInfo?

Returns a `TrackInfo` built from `mediaController.metadata`, or `null` if metadata is null.

### 6.5 getCurrentPlaybackState(): PlaybackState?

Returns a `PlaybackState` built from `mediaController.playbackState`, or `null` if state is null.

---

## 7. AlbumArtEncoder

### 7.1 encode(bitmap: Bitmap): String

1. Scale the bitmap so its longest edge is ≤ 256 px, preserving aspect ratio.
2. Compress to JPEG at quality 80 into a `ByteArrayOutputStream`.
3. Return `Base64.encodeToString(bytes, Base64.NO_WRAP)`.

### 7.2 encodeOrNull(bitmap: Bitmap?): String?

- If `bitmap` is `null`: return `null`.
- Otherwise call `encode(bitmap)`.
- If `encode` throws any exception: log the error, return `null`.

---

## 8. BtServer

### 8.1 open()

Creates a `BluetoothServerSocket` via `BluetoothAdapter.listenUsingRfcommWithServiceRecord("DapController", DAP_BT_UUID)`. Throws `IOException` if the adapter is unavailable or the socket cannot be created.

### 8.2 send(update: Update)

1. Serialize `update` with `MessageSerializer.serialize(update)`.
2. Write the resulting string (including the trailing `\n`) to the active client socket's output stream.
3. On `IOException`: close the client socket, notify the service of disconnect. Does **not** throw.

### 8.3 close()

- Closes the active client socket (if any).
- Closes the server socket.
- Both closes are wrapped in try/catch; exceptions are swallowed.
- Idempotent — safe to call multiple times.

---

## 9. Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

`BIND_NOTIFICATION_LISTENER_SERVICE` is declared as a `permission` attribute on the `CompanionNotificationListener` `<service>` tag, not as a top-level `<uses-permission>`.
