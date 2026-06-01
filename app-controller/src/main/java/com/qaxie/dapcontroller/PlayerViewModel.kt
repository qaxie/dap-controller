package com.qaxie.dapcontroller

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.qaxie.dapcontroller.core.Command
import com.qaxie.dapcontroller.core.PlaybackState
import com.qaxie.dapcontroller.core.TrackInfo
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    val connectionState: StateFlow<ConnectionState> = ConnectionRepository.connectionState
    val trackInfo: StateFlow<TrackInfo?> = ConnectionRepository.trackInfo
    val playbackState: StateFlow<PlaybackState?> = ConnectionRepository.playbackState
    val playbackAnchor: StateFlow<PlaybackAnchor?> = ConnectionRepository.playbackAnchor

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val state = connectionState.value
        if (state is ConnectionState.Connecting || state is ConnectionState.Connected) return
        val intent = Intent(getApplication(), ControllerService::class.java).apply {
            action = ControllerService.ACTION_CONNECT
            putExtra(ControllerService.EXTRA_DEVICE_ADDRESS, device.address)
            putExtra(ControllerService.EXTRA_DEVICE_NAME, device.name ?: device.address)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun disconnect() {
        getApplication<Application>().startService(
            Intent(getApplication(), ControllerService::class.java).apply {
                action = ControllerService.ACTION_DISCONNECT
            }
        )
    }

    fun sendCommand(command: Command) {
        if (connectionState.value !is ConnectionState.Connected) return
        val action = when (command) {
            Command.PlayPause -> ControllerService.ACTION_PLAY_PAUSE
            Command.Next -> ControllerService.ACTION_NEXT
            Command.Previous -> ControllerService.ACTION_PREVIOUS
        }
        getApplication<Application>().startService(
            Intent(getApplication(), ControllerService::class.java).apply {
                this.action = action
            }
        )
    }
}
