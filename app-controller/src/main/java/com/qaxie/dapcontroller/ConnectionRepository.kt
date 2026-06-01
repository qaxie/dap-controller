package com.qaxie.dapcontroller

import com.qaxie.dapcontroller.core.PlaybackState
import com.qaxie.dapcontroller.core.TrackInfo
import kotlinx.coroutines.flow.MutableStateFlow

object ConnectionRepository {
    val connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
    val trackInfo: MutableStateFlow<TrackInfo?> = MutableStateFlow(null)
    val playbackState: MutableStateFlow<PlaybackState?> = MutableStateFlow(null)
    val playbackAnchor: MutableStateFlow<PlaybackAnchor?> = MutableStateFlow(null)
}
