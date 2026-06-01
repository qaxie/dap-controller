package com.qaxie.dapcontroller

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Failed(val deviceName: String, val reason: String) : ConnectionState()
}

data class PlaybackAnchor(
    val positionMs: Long,
    val anchoredAt: Long
)
