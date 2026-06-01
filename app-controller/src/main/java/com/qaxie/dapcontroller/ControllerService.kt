package com.qaxie.dapcontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Base64
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
            ACTION_VOLUME_UP -> handleCommand(Command.VolumeUp)
            ACTION_VOLUME_DOWN -> handleCommand(Command.VolumeDown)
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
                        is Update.Volume -> {
                            ConnectionRepository.volume.value = update
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
        ConnectionRepository.volume.value = null
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
        ConnectionRepository.volume.value = null
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

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(contentIntent)
            .setOngoing(state is ConnectionState.Connected || state is ConnectionState.Connecting)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        track?.albumArtBase64?.let { base64 ->
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { builder.setLargeIcon(it) }
            } catch (_: Exception) {}
        }

        when {
            state is ConnectionState.Connected && track != null -> {
                val isPlaying = playback?.isPlaying == true
                builder.addAction(makeAction("Previous", ACTION_PREVIOUS, R.drawable.ic_skip_previous))
                builder.addAction(makeAction(
                    if (isPlaying) "Pause" else "Play", ACTION_PLAY_PAUSE,
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                ))
                builder.addAction(makeAction("Next", ACTION_NEXT, R.drawable.ic_skip_next))
                builder.addAction(makeAction("Disconnect", ACTION_DISCONNECT, android.R.drawable.ic_menu_close_clear_cancel))
                builder.setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            }
            state is ConnectionState.Connected -> {
                builder.addAction(makeAction("Disconnect", ACTION_DISCONNECT, android.R.drawable.ic_menu_close_clear_cancel))
                builder.setStyle(Notification.MediaStyle())
            }
            state is ConnectionState.Failed -> {
                builder.addAction(makeAction("Dismiss", ACTION_DISCONNECT, android.R.drawable.ic_menu_close_clear_cancel))
            }
        }

        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun makeAction(label: String, action: String, iconRes: Int): Notification.Action {
        val intent = Intent(this, ControllerService::class.java).apply { this.action = action }
        val pi = PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(iconRes, label, pi).build()
    }

    companion object {
        const val ACTION_CONNECT = "com.qaxie.dapcontroller.ACTION_CONNECT"
        const val ACTION_PLAY_PAUSE = "com.qaxie.dapcontroller.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.qaxie.dapcontroller.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.qaxie.dapcontroller.ACTION_PREVIOUS"
        const val ACTION_VOLUME_UP = "com.qaxie.dapcontroller.ACTION_VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "com.qaxie.dapcontroller.ACTION_VOLUME_DOWN"
        const val ACTION_DISCONNECT = "com.qaxie.dapcontroller.ACTION_DISCONNECT"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "controller_service"
    }
}
