# Overview

## What It Does

Two Android apps that let a phone control music playback on a Hiby R4 DAP running Auxio, connected over Bluetooth Classic (RFCOMM).

| App | Runs on | Role |
|---|---|---|
| `app-controller` | Phone | UI — shows now-playing info, sends playback commands |
| `app-companion` | Hiby R4 DAP | Minimal launcher screen (start/stop) + background service that bridges Auxio's MediaSession to the BT socket |
| `core` | Both (library) | Shared BT protocol definitions and data models |

---

## Architecture

```
[Phone]                              [Hiby R4 DAP]
app-controller                       app-companion
      │                                    │
      │   Bluetooth RFCOMM (JSON/newline)  │
      │ ◄─────────────────────────────────►│
      │   Commands →                       │──► MediaController ──► Auxio
      │              ← Track/State updates │◄────────────────────────────
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

1. **DAP:** Install `app-companion` APK on the Hiby R4.
2. **DAP:** Open the DAP Companion app → go to Settings → Apps → Special app access → Notification access → enable *DAP Companion*.
3. **Both:** Pair the phone and DAP via Android Bluetooth settings.
4. **Phone:** Install `app-controller` APK, open it, grant Bluetooth permissions.

### Daily Use

1. **DAP:** Open DAP Companion app → tap *Start*.
2. **Phone:** Open controller app → tap *Hiby R4* in the paired device list.
3. App connects (typically under 2 seconds when both devices are awake).
4. Player screen appears showing whatever Auxio is currently playing.
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
