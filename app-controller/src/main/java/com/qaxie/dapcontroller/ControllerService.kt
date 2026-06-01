package com.qaxie.dapcontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.qaxie.dapcontroller.core.Command
import com.qaxie.dapcontroller.core.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ControllerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val btClient = BtClient()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: address
                handleConnect(address, name)
            }
            ACTION_PLAY_PAUSE -> handleCommand(Command.PlayPause)
            ACTION_NEXT -> handleCommand(Command.Next)
            ACTION_PREVIOUS -> handleCommand(Command.Previous)
            ACTION_DISCONNECT -> handleDisconnect()
        }
        return START_NOT_STICKY
    }

    private fun handleConnect(address: String, name: String) {
        val state = ConnectionRepository.connectionState.value
        if (state is ConnectionState.Connecting || state is ConnectionState.Connected) return

        ConnectionRepository.connectionState.value = ConnectionState.Connecting(name)
        updateNotification()

        scope.launch(Dispatchers.IO) {
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val device = adapter.getRemoteDevice(address)
            try {
                btClient.connect(device)
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    ConnectionRepository.connectionState.value =
                        ConnectionState.Failed(name, e.message ?: "Connection failed")
                    updateNotification()
                    stopSelf()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                ConnectionRepository.connectionState.value = ConnectionState.Connected(name)
                updateNotification()
            }
            // Collect updates until socket closes
            btClient.updates.collect { update ->
                withContext(Dispatchers.Main) {
                    when (update) {
                        is Update.Track -> {
                            ConnectionRepository.trackInfo.value = update.info
                            ConnectionRepository.playbackAnchor.value = null
                            updateNotification()
                        }
                        is Update.State -> {
                            ConnectionRepository.playbackState.value = update.state
                            ConnectionRepository.playbackAnchor.value = PlaybackAnchor(
                                positionMs = update.state.positionMs,
                                anchoredAt = System.currentTimeMillis()
                            )
                            updateNotification()
                        }
                    }
                }
            }
            // Flow completed = socket closed
            withContext(Dispatchers.Main) {
                if (ConnectionRepository.connectionState.value is ConnectionState.Connected) {
                    ConnectionRepository.connectionState.value =
                        ConnectionState.Failed(name, "Connection lost")
                    updateNotification()
                    stopSelf()
                }
            }
        }
    }

    private fun handleCommand(command: Command) {
        val state = ConnectionRepository.connectionState.value
        if (state !is ConnectionState.Connected) return
        scope.launch {
            try {
                btClient.send(command)
            } catch (e: IOException) {
                ConnectionRepository.connectionState.value =
                    ConnectionState.Failed(state.deviceName, "Connection lost")
                updateNotification()
                stopSelf()
            }
        }
    }

    private fun handleDisconnect() {
        btClient.close()
        ConnectionRepository.connectionState.value = ConnectionState.Disconnected
        ConnectionRepository.trackInfo.value = null
        ConnectionRepository.playbackState.value = null
        ConnectionRepository.playbackAnchor.value = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        btClient.close()
        ConnectionRepository.connectionState.value = ConnectionState.Disconnected
        ConnectionRepository.trackInfo.value = null
        ConnectionRepository.playbackState.value = null
        ConnectionRepository.playbackAnchor.value = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Notification ---

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "DAP Controller", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val state = ConnectionRepository.connectionState.value
        val track = ConnectionRepository.trackInfo.value
        val playback = ConnectionRepository.playbackState.value

        val title: String
        val text: String
        when (state) {
            is ConnectionState.Connecting -> {
                title = "DAP Controller"
                text = "Connecting to ${state.deviceName}…"
            }
            is ConnectionState.Connected -> {
                title = track?.title ?: "DAP Controller"
                text = track?.artist ?: "Connected · Waiting for playback…"
            }
            is ConnectionState.Failed -> {
                title = "DAP Controller"
                text = state.reason
            }
            ConnectionState.Disconnected -> {
                title = "DAP Controller"
                text = "Disconnected"
            }
        }

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(state is ConnectionState.Connected || state is ConnectionState.Connecting)

        when {
            state is ConnectionState.Connected && track != null -> {
                builder.addAction(makeAction("⏮", ACTION_PREVIOUS))
                builder.addAction(makeAction(if (playback?.isPlaying == true) "⏸" else "▶", ACTION_PLAY_PAUSE))
                builder.addAction(makeAction("⏭", ACTION_NEXT))
                builder.addAction(makeAction("Disconnect", ACTION_DISCONNECT))
            }
            state is ConnectionState.Connected -> {
                builder.addAction(makeAction("Disconnect", ACTION_DISCONNECT))
            }
            state is ConnectionState.Failed -> {
                builder.addAction(makeAction("Dismiss", ACTION_DISCONNECT))
            }
        }

        return builder.build()
    }

    private fun makeAction(label: String, action: String): Notification.Action {
        val intent = Intent(this, ControllerService::class.java).apply { this.action = action }
        val pi = PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(null, label, pi).build()
    }

    companion object {
        const val ACTION_CONNECT = "com.qaxie.dapcontroller.ACTION_CONNECT"
        const val ACTION_PLAY_PAUSE = "com.qaxie.dapcontroller.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.qaxie.dapcontroller.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.qaxie.dapcontroller.ACTION_PREVIOUS"
        const val ACTION_DISCONNECT = "com.qaxie.dapcontroller.ACTION_DISCONNECT"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "controller_service"
    }
}
