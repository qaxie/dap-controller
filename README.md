# dap-controller

Control music playback and volume on an Android DAP from your phone over Bluetooth Classic.

Works with any media app that publishes a `MediaSession` — Auxio, Spotify, YouTube Music, etc.

---

## Apps

| App | Runs on | Role |
|---|---|---|
| `app-controller` | Phone | Now-playing UI with playback and volume controls |
| `app-companion` | Android DAP | Background service bridging the active MediaSession to a Bluetooth RFCOMM socket |

The `:core` module is a shared library used by both apps — it defines the wire protocol, data models, and serialization logic.

---

## Features

- Now-playing info: track title, artist, album, album art
- Playback controls: play/pause, next, previous
- Volume control: raise/lower DAP media volume with live level display
- Progress bar with local interpolation (advances smoothly between updates)
- MediaStyle notification on the phone (works from the lock screen and notification shade)
- Trusted device allowlist: the first phone to connect is saved as the only trusted device — all other paired devices are rejected

---

## Architecture

```
[Phone]                              [Android DAP]
app-controller                       app-companion
      │                                    │
      │   Bluetooth RFCOMM (JSON/newline)  │
      │ ◄─────────────────────────────────►│
      │   Commands (play/pause/vol/…) →    │──► MediaController / AudioManager
      │      ← Track / State / Volume      │◄──────────────────────────────────
      │                                    │
      └──────────────── :core ─────────────┘
```

- The phone always initiates the RFCOMM connection.
- The companion always listens (server role).
- Audio stays on the DAP. Bluetooth here is for control only, not audio streaming.

---

## Setup

### Requirements

- Phone running Android 12+
- DAP running Android (tested on Hiby R4 with Android 12)
- Both devices paired via Bluetooth settings

### Install

1. Build and install `app-companion` on the DAP.
2. Build and install `app-controller` on the phone.

Using ADB:
```
./gradlew :app-companion:installDebug   # with DAP connected
./gradlew :app-controller:installDebug  # with phone connected
```

### First-Time Setup

1. **DAP:** Open DAP Companion → go to *Settings → Apps → Special app access → Notification access* → enable *DAP Companion*.
2. **DAP:** Tap *Start* in the companion app.
3. **Phone:** Open the controller app, grant Bluetooth permissions, tap the DAP in the device list.
4. The first phone to connect is automatically saved as the trusted device.

---

## Specs

Detailed specs for each component are in the [`specs/`](specs/) directory:

- [`specs/overview.md`](specs/overview.md) — architecture and user flows
- [`specs/core.md`](specs/core.md) — wire protocol and data models
- [`specs/controller.md`](specs/controller.md) — phone app
- [`specs/companion.md`](specs/companion.md) — DAP app
