package com.qaxie.dapcontroller.core

data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtBase64: String?
)

data class PlaybackState(
    val isPlaying: Boolean,
    val positionMs: Long
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
