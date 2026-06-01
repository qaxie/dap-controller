package com.qaxie.dapcontroller.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.qaxie.dapcontroller.core.MessageParser
import com.qaxie.dapcontroller.core.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class CompanionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var btServer: BtServer
    private lateinit var mediaSessionBridge: MediaSessionBridge
    private var connectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true

        btServer = BtServer(this)
        mediaSessionBridge = MediaSessionBridge(
            context = this,
            btServer = btServer,
            onStatusChanged = { text -> updateStatus(text) },
            onSessionLost = { updateStatus(STATUS_CONNECTED_WAITING) }
        )

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            btServer.open()
        } catch (e: IOException) {
            stopSelf()
            return
        }

        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val socket = try {
                    btServer.accept()
                } catch (e: IOException) {
                    break
                }
                connectionJob?.cancel()
                connectionJob = scope.launch {
                    handleConnection(socket)
                }
            }
        }
    }

    private suspend fun handleConnection(socket: android.bluetooth.BluetoothSocket) {
        btServer.setClientSocket(socket)
        updateStatus(STATUS_CONNECTED_WAITING)

        // Retry attaching to Auxio every 2 s until found or coroutine cancelled
        while (currentCoroutineContext().isActive && !mediaSessionBridge.attach()) {
            delay(2000)
        }
        if (!currentCoroutineContext().isActive) return

        // Push current state immediately so the phone doesn't wait for the next change
        mediaSessionBridge.getCurrentTrackInfo()?.let { btServer.send(Update.Track(it)) }
        mediaSessionBridge.getCurrentPlaybackState()?.let { btServer.send(Update.State(it)) }

        // Read loop — blocks on IO until client disconnects
        val reader = withContext(Dispatchers.IO) {
            socket.inputStream.bufferedReader()
        }
        try {
            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) {
                    try { reader.readLine() } catch (e: IOException) { null }
                } ?: break
                MessageParser.parseCommand(line)?.let { mediaSessionBridge.executeCommand(it) }
            }
        } finally {
            mediaSessionBridge.detach()
            btServer.closeClientSocket()
            // Only reset status if the coroutine ended naturally (client disconnected),
            // not if it was cancelled to make room for a new incoming connection.
            if (currentCoroutineContext().isActive) updateStatus(STATUS_WAITING)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        btServer.close()
        mediaSessionBridge.detach()
        _isRunning.value = false
        _statusText.value = STATUS_WAITING
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Notification ---

    private fun updateStatus(text: String) {
        _statusText.value = text
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, CompanionService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(_statusText.value)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.notification_action_stop),
                    stopPendingIntent
                ).build()
            )
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.qaxie.dapcontroller.companion.ACTION_STOP"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "companion_service"

        const val STATUS_WAITING = "Waiting for connection…"
        const val STATUS_CONNECTED_WAITING = "Connected — waiting for Auxio…"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _statusText = MutableStateFlow(STATUS_WAITING)
        val statusText: StateFlow<String> = _statusText
    }
}
