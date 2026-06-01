package com.qaxie.dapcontroller.companion

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState as AndroidPlaybackState
import com.qaxie.dapcontroller.core.Command
import com.qaxie.dapcontroller.core.PlaybackState
import com.qaxie.dapcontroller.core.TrackInfo
import com.qaxie.dapcontroller.core.Update

class MediaSessionBridge(
    private val context: Context,
    private val btServer: BtServer,
    private val onStatusChanged: (String) -> Unit,
    private val onSessionLost: () -> Unit
) {
    private var mediaController: MediaController? = null
    private var lastTrackInfo: TrackInfo? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val info = metadata?.toTrackInfo() ?: return
            lastTrackInfo = info
            btServer.send(Update.Track(info))
            updateStatusFromCurrentState()
        }

        override fun onPlaybackStateChanged(state: AndroidPlaybackState?) {
            val coreState = state?.toCorePlaybackState() ?: return
            btServer.send(Update.State(coreState))
            updateStatusFromCurrentState()
        }

        override fun onSessionDestroyed() {
            detach()
            onSessionLost()
        }
    }

    fun attach(): Boolean {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listenerComponent = ComponentName(context, CompanionNotificationListener::class.java)
        val sessions = try {
            manager.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            return false
        }
        val session = sessions.firstOrNull { it.playbackState?.state == AndroidPlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
            ?: return false
        mediaController = session
        session.registerCallback(callback)
        return true
    }

    fun detach() {
        mediaController?.unregisterCallback(callback)
        mediaController = null
        lastTrackInfo = null
    }

    fun executeCommand(command: Command) {
        val controls = mediaController?.transportControls ?: return
        when (command) {
            is Command.PlayPause -> {
                val state = mediaController?.playbackState?.state
                if (state == AndroidPlaybackState.STATE_PLAYING) controls.pause() else controls.play()
            }
            is Command.Next -> controls.skipToNext()
            is Command.Previous -> controls.skipToPrevious()
        }
    }

    fun getCurrentTrackInfo(): TrackInfo? =
        mediaController?.metadata?.toTrackInfo()

    fun getCurrentPlaybackState(): PlaybackState? =
        mediaController?.playbackState?.toCorePlaybackState()

    private fun updateStatusFromCurrentState() {
        val track = lastTrackInfo ?: return
        val isPlaying = mediaController?.playbackState?.state == AndroidPlaybackState.STATE_PLAYING
        val prefix = if (isPlaying) "Connected" else "Paused"
        onStatusChanged("$prefix · ${track.title} — ${track.artist}")
    }

    private fun MediaMetadata.toTrackInfo(): TrackInfo {
        val bitmap = getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
        return TrackInfo(
            title = getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            album = getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            durationMs = getLong(MediaMetadata.METADATA_KEY_DURATION),
            albumArtBase64 = AlbumArtEncoder.encodeOrNull(bitmap)
        )
    }

    private fun AndroidPlaybackState.toCorePlaybackState() = PlaybackState(
        isPlaying = state == AndroidPlaybackState.STATE_PLAYING,
        positionMs = position
    )

}
