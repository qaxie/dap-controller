# Overview

## What It Does

Two Android apps that let a phone control music playback on a Android DAP DAP, connected over Bluetooth Classic (RFCOMM). Works with any media app that publishes a MediaSession (Auxio, Spotify, YouTube, etc.).

| App | Runs on | Role |
|---|---|---|
| `app-controller` | Phone | UI — shows now-playing info, sends playback commands |
| `app-companion` | Android DAP DAP | Minimal launcher screen (start/stop) + background service that bridges the active MediaSession to the BT socket |
| `core` | Both (library) | Shared BT protocol definitions and data models |

---

## Architecture

```
[Phone]                              [Android DAP DAP]
app-controller                       app-companion
      │                                    │
      │   Bluetooth RFCOMM (JSON/newline)  │
      │ ◄─────────────────────────────────►│
      │   Commands →                       │──► MediaController ──► active media app
      │              ← Track/State updates │◄──────────────────────────────────────
      │                                    │
      └──────────────── :core ─────────────┘
```

- The phone always initiates the RFCOMM connection.
- The companion always listens (server role).
- Communication is full-duplex over a single socket — commands go one way, updates the other.
- Audio stays on the DAP. Bluetooth here is for control only, not audio streaming.

---

## User Flows

### First-Time Setup

1. **DAP:** Install `app-companion` APK on the Android DAP.
2. **DAP:** Open the DAP Companion app → go to Settings → Apps → Special app access → Notification access → enable *DAP Companion*.
3. **Both:** Pair the phone and DAP via Android Bluetooth settings.
4. **Phone:** Install `app-controller` APK, open it, grant Bluetooth permissions.

### Daily Use

1. **DAP:** Open DAP Companion app → tap *Start*.
2. **Phone:** Open controller app → tap *Android DAP* in the paired device list.
3. App connects (typically under 2 seconds when both devices are awake).
4. Player screen appears showing whatever is currently playing on the DAP.
5. Use play/pause, next, and previous as needed.
6. **When done:** Stop the companion from the app or its notification *Stop* button. Or leave it running if you plan to reconnect soon.

---

## Out of Scope

- Seek (scrubbing the progress bar to a position)
- Volume control
- Library browsing or queue management
- Automatic reconnection after a dropped connection
- Multiple simultaneous phone connections
- iOS support
