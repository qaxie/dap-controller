package com.qaxie.dapcontroller.core

import org.json.JSONObject

object MessageSerializer {

    fun serialize(command: Command): String {
        val json = JSONObject()
        json.put("type", when (command) {
            is Command.PlayPause -> "PLAY_PAUSE"
            is Command.Next -> "NEXT"
            is Command.Previous -> "PREVIOUS"
            is Command.VolumeUp -> "VOLUME_UP"
            is Command.VolumeDown -> "VOLUME_DOWN"
        })
        return json.toString() + "\n"
    }

    fun serialize(update: Update): String {
        val json = JSONObject()
        when (update) {
            is Update.Track -> {
                json.put("type", "TRACK_INFO")
                json.put("title", update.info.title)
                json.put("artist", update.info.artist)
                json.put("album", update.info.album)
                json.put("durationMs", update.info.durationMs)
                json.put("albumArtBase64", update.info.albumArtBase64)
            }
            is Update.State -> {
                json.put("type", "PLAYBACK_STATE")
                json.put("isPlaying", update.state.isPlaying)
                json.put("positionMs", update.state.positionMs)
            }
            is Update.Volume -> {
                json.put("type", "VOLUME")
                json.put("level", update.level)
                json.put("maxLevel", update.maxLevel)
            }
        }
        return json.toString() + "\n"
    }
}
