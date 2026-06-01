# Error Scenarios

All expected error conditions and the required behavior in each case.

---

## Phone-Side Errors (app-controller)

| Scenario | Where detected | Required behavior |
|---|---|---|
| Bluetooth adapter is off | `ConnectScreen` entry | Show *"Bluetooth is off"* message and a button that opens `ACTION_BLUETOOTH_SETTINGS`. Device list is not shown. |
| `BLUETOOTH_CONNECT` or `BLUETOOTH_SCAN` permission denied | `ConnectScreen` entry | Show permission rationale and re-request. If permanently denied, show *"Open Settings"* button to app permission settings. Device list is not shown. |
| No paired Bluetooth devices | `ConnectScreen` after permission check | Show *"No paired devices found. Pair your DAP in Bluetooth settings."* |
| RFCOMM connection refused (companion not running on DAP) | `BtClient.connect` — socket connect throws | After 10-second timeout: `Failed("Could not connect. Open DAP Companion on your DAP and tap Start.")` |
| RFCOMM connection timeout | `BtClient.connect` — `withTimeout` fires | `Failed("Connection timed out.")` |
| Auxio not running on DAP at connect time | No `TRACK_INFO` received after connecting | `PlayerScreen` stays in *"Waiting for playback…"* state. No error shown. No timeout. |
| BT link drops during a session | `IOException` on socket read in `BtClient.updates` | Flow completes; `ControllerService` writes `Failed("Connection lost")` to `ConnectionRepository` and calls `stopSelf()`. `PlayerScreen` shows the disconnect banner. |
| Socket write failure (send command) | `BtClient.send` throws `IOException` | `ControllerService` writes `Failed("Connection lost")` to `ConnectionRepository` and calls `stopSelf()`. |
| Malformed or unrecognised JSON received | `MessageParser` returns `null` | Drop the line silently. Log at `WARN`. No state change. Socket stays open. |

---

## DAP-Side Errors (app-companion)

| Scenario | Where detected | Required behavior |
|---|---|---|
| Auxio not running when phone connects | `MediaSessionBridge.attach()` returns `false` | Enter a 2-second retry loop. Keep the client socket open. Send no updates until Auxio is found. |
| Auxio session destroyed mid-session | `onSessionDestroyed()` callback | Call `MediaSessionBridge.detach()`. Start the 2-second retry loop. Phone will show *"Waiting for playback…"* because no new `TRACK_INFO` arrives. |
| Album art encoding fails | `AlbumArtEncoder.encodeOrNull` catches exception | Log the error. Set `albumArtBase64 = null`. Send `TRACK_INFO` with null art. Phone shows placeholder icon. |
| Socket write failure (sending update) | `BtServer.send` catches `IOException` | Close the client socket. Notify `CompanionService`. Return to accepting the next connection. Do not throw. |
| Phone disconnects cleanly | `IOException` on socket read loop | Close the client socket. Call `MediaSessionBridge.detach()`. Return to `acceptLoop()`. |
| Bluetooth adapter unavailable on DAP | `BtServer.open()` throws | Log the error. `CompanionService` should stop itself gracefully. |

---

## General Rules

1. **No crashes from protocol errors.** Malformed messages are always dropped, never cause exceptions to propagate.
2. **Socket errors always cause a clean disconnect**, never a retry on the same socket.
3. **The companion never stops trying to accept connections** (unless explicitly stopped), so the phone can always reconnect by tapping the device again.
4. **The companion never stops retrying to find Auxio** (while a client is connected), so if Auxio is started after the phone connects, the companion will find it within 2 seconds.
