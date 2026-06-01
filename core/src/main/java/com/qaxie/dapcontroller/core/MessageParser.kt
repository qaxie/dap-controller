package com.qaxie.dapcontroller.core

import org.json.JSONException
import org.json.JSONObject

object MessageParser {

    fun parseCommand(line: String): Command? {
        if (line.isBlank()) return null
        return try {
            val json = JSONObject(line)
            when (json.optString("type")) {
                "PLAY_PAUSE" -> Command.PlayPause
                "NEXT" -> Command.Next
                "PREVIOUS" -> Command.Previous
                "VOLUME_UP" -> Command.VolumeUp
                "VOLUME_DOWN" -> Command.VolumeDown
                else -> null
            }
        } catch (e: JSONException) {
            null
        }
    }

    fun parseUpdate(line: String): Update? {
        if (line.isBlank()) return null
        return try {
            val json = JSONObject(line)
            when (json.optString("type")) {
                "TRACK_INFO" -> parseTrackInfo(json)
                "PLAYBACK_STATE" -> parsePlaybackState(json)
                "VOLUME" -> parseVolume(json)
                else -> null
            }
        } catch (e: JSONException) {
            null
        }
    }

    private fun parseTrackInfo(json: JSONObject): Update.Track? {
        if (!json.has("title") || !json.has("artist") || !json.has("album") || !json.has("durationMs")) {
            return null
        }
        val albumArtBase64 = if (json.isNull("albumArtBase64")) null else json.optString("albumArtBase64").ifEmpty { null }
        return Update.Track(
            TrackInfo(
                title = json.getString("title"),
                artist = json.getString("artist"),
                album = json.getString("album"),
                durationMs = json.getLong("durationMs"),
                albumArtBase64 = albumArtBase64
            )
        )
    }

    private fun parsePlaybackState(json: JSONObject): Update.State? {
        if (!json.has("isPlaying") || !json.has("positionMs")) return null
        return Update.State(
            PlaybackState(
                isPlaying = json.getBoolean("isPlaying"),
                positionMs = json.getLong("positionMs")
            )
        )
    }

    private fun parseVolume(json: JSONObject): Update.Volume? {
        if (!json.has("level") || !json.has("maxLevel")) return null
        return Update.Volume(json.getInt("level"), json.getInt("maxLevel"))
    }
}
