package com.qaxie.dapcontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.qaxie.dapcontroller.ui.ConnectScreen
import com.qaxie.dapcontroller.ui.PlayerScreen
import com.qaxie.dapcontroller.ui.theme.DapControllerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            DapControllerTheme {
                val connectionState by viewModel.connectionState.collectAsState()
                var showPlayer by remember { mutableStateOf(false) }

                LaunchedEffect(connectionState) {
                    if (connectionState is ConnectionState.Connected) showPlayer = true
                }

                if (showPlayer) {
                    PlayerScreen(
                        viewModel = viewModel,
                        onNavigateToConnect = { showPlayer = false }
                    )
                } else {
                    ConnectScreen(viewModel = viewModel)
                }
            }
        }
    }
}
