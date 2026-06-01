# Core Module Spec

The `:core` module is an Android library used by both `app-controller` and `app-companion`. It defines the shared BT protocol, data models, and serialization logic.

---

## 1. RFCOMM UUID

A single constant declared in `:core`. Both apps reference this value — never hardcode it in either app module.

```kotlin
val DAP_BT_UUID: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
```

---

## 2. Data Models

```kotlin
data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtBase64: String?   // null when Auxio has no art or encoding failed
)

data class PlaybackState(
    val isPlaying: Boolean,
    val positionMs: Long          // milliseconds from the start of the track
)

sealed class Command {
    object PlayPause : Command()
    object Next : Command()
    object Previous : Command()
    object VolumeUp : Command()
    object VolumeDown : Command()
}

sealed class Update {
    data class Track(val info: TrackInfo) : Update()
    data class State(val state: PlaybackState) : Update()
    data class Volume(val level: Int, val maxLevel: Int) : Update()
}
```

---

## 3. Wire Protocol

All messages are **newline-delimited JSON** (`\n`). Each message is exactly one line. Readers split on `\n` and parse each line independently.

### 3.1 Message Types

**Commands** (phone → DAP):

| `type` | Meaning |
|---|---|
| `PLAY_PAUSE` | Toggle play/pause |
| `NEXT` | Skip to next track |
| `PREVIOUS` | Skip to previous track |
| `VOLUME_UP` | Raise DAP music stream volume by one step |
| `VOLUME_DOWN` | Lower DAP music stream volume by one step |

**Updates** (DAP → phone):

| `type` | Fields |
|---|---|
| `TRACK_INFO` | `title`, `artist`, `album`, `durationMs`, `albumArtBase64` |
| `PLAYBACK_STATE` | `isPlaying`, `positionMs` |
| `VOLUME` | `level`, `maxLevel` |

### 3.2 Wire Examples

```
{"type":"PLAY_PAUSE"}
{"type":"NEXT"}
{"type":"PREVIOUS"}
{"type":"VOLUME_UP"}
{"type":"VOLUME_DOWN"}
{"type":"TRACK_INFO","title":"Retrograde","artist":"James Blake","album":"Overgrown","durationMs":268000,"albumArtBase64":null}
{"type":"PLAYBACK_STATE","isPlaying":true,"positionMs":42300}
{"type":"VOLUME","level":8,"maxLevel":15}
```

---

## 4. MessageSerializer Contract

`MessageSerializer.serialize(command: Command): String`

| Input | Output (including trailing `\n`) |
|---|---|
| `PlayPause` | `{"type":"PLAY_PAUSE"}\n` |
| `Next` | `{"type":"NEXT"}\n` |
| `Previous` | `{"type":"PREVIOUS"}\n` |
| `VolumeUp` | `{"type":"VOLUME_UP"}\n` |
| `VolumeDown` | `{"type":"VOLUME_DOWN"}\n` |

`MessageSerializer.serialize(update: Update): String`

| Input | Output (including trailing `\n`) |
|---|---|
| `Track(TrackInfo("A","B","C",1000,null))` | `{"type":"TRACK_INFO","title":"A","artist":"B","album":"C","durationMs":1000,"albumArtBase64":null}\n` |
| `State(PlaybackState(true,500))` | `{"type":"PLAYBACK_STATE","isPlaying":true,"positionMs":500}\n` |
| `Volume(8,15)` | `{"type":"VOLUME","level":8,"maxLevel":15}\n` |

---

## 5. MessageParser Contract

`MessageParser.parseCommand(line: String): Command?`

| Input | Result |
|---|---|
| `{"type":"PLAY_PAUSE"}` | `Command.PlayPause` |
| `{"type":"NEXT"}` | `Command.Next` |
| `{"type":"PREVIOUS"}` | `Command.Previous` |
| `{"type":"VOLUME_UP"}` | `Command.VolumeUp` |
| `{"type":"VOLUME_DOWN"}` | `Command.VolumeDown` |
| `{"type":"UNKNOWN"}` | `null` |
| `not json` | `null` |
| `""` (empty) | `null` |

`MessageParser.parseUpdate(line: String): Update?`

| Input | Result |
|---|---|
| `{"type":"TRACK_INFO","title":"A","artist":"B","album":"C","durationMs":1000,"albumArtBase64":null}` | `Update.Track(TrackInfo("A","B","C",1000,null))` |
| `{"type":"PLAYBACK_STATE","isPlaying":false,"positionMs":0}` | `Update.State(PlaybackState(false,0))` |
| `{"type":"VOLUME","level":8,"maxLevel":15}` | `Update.Volume(8,15)` |
| `{"type":"TRACK_INFO"}` (missing required fields) | `null` |
| `not json` | `null` |
| `""` (empty) | `null` |

---

## 6. Parsing Rules (both ends)

These rules apply on both the phone and the DAP so neither side can crash the other:

1. A line that is not valid JSON → drop silently, log `WARN`, continue reading.
2. A JSON object with an unrecognised `type` → drop silently, log `WARN`, continue reading.
3. A JSON object with a known `type` but missing required fields → drop silently, log `WARN`.
4. A null or empty line → drop silently, continue reading.
5. None of the above cases close the socket or change any state.
