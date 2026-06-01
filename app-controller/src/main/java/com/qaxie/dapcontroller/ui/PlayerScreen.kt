package com.qaxie.dapcontroller.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.qaxie.dapcontroller.ConnectionState
import com.qaxie.dapcontroller.PlayerViewModel
import com.qaxie.dapcontroller.core.Command
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onNavigateToConnect: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()
    val trackInfo by viewModel.trackInfo.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val playbackAnchor by viewModel.playbackAnchor.collectAsState()

    val isConnected = connectionState is ConnectionState.Connected
    val controlsEnabled = trackInfo != null && isConnected

    val albumArtBitmap = remember(trackInfo?.albumArtBase64) {
        trackInfo?.albumArtBase64?.let { base64 ->
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) { null }
        }
    }

    var displayPositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playbackState, playbackAnchor) {
        while (true) {
            val anchor = playbackAnchor
            val position = if (playbackState?.isPlaying == true && anchor != null) {
                anchor.positionMs + (System.currentTimeMillis() - anchor.anchoredAt)
            } else {
                playbackState?.positionMs ?: 0L
            }
            displayPositionMs = position.coerceIn(0L, trackInfo?.durationMs ?: 0L)
            delay(500)
        }
    }

    val durationMs = trackInfo?.durationMs?.takeIf { it > 0 } ?: 1L
    val progressFraction = (displayPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Connection indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                                shape = CircleShape
                            )
                    )
                    val deviceName = (connectionState as? ConnectionState.Connected)?.deviceName ?: ""
                    Text(
                        if (isConnected) "Connected: $deviceName" else "Connection lost",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(24.dp))

                if (trackInfo == null) {
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Waiting for playback…")
                    }
                } else {
                    // Album art
                    if (albumArtBitmap != null) {
                        Image(
                            bitmap = albumArtBitmap,
                            contentDescription = "Album art",
                            modifier = Modifier.size(200.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(trackInfo!!.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${trackInfo!!.artist} · ${trackInfo!!.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    // Progress bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(displayPositionMs.toTimeString(), style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text((trackInfo?.durationMs ?: 0L).toTimeString(), style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.sendCommand(Command.Previous) },
                            enabled = controlsEnabled,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                        }
                        IconButton(
                            onClick = { viewModel.sendCommand(Command.PlayPause) },
                            enabled = controlsEnabled,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (playbackState?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.sendCommand(Command.Next) },
                            enabled = controlsEnabled,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next")
                        }
                    }
                }
            }

            // Connection lost banner
            if (!isConnected) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Connection lost",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = {
                        viewModel.disconnect()
                        onNavigateToConnect()
                    }) {
                        Text("Reconnect")
                    }
                }
            }
        }
    }
}

private fun Long.toTimeString(): String {
    val totalSecs = this / 1000
    return "%d:%02d".format(totalSecs / 60, totalSecs % 60)
}
