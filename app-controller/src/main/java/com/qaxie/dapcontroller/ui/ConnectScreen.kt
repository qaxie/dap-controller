package com.qaxie.dapcontroller.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.qaxie.dapcontroller.ConnectionState
import com.qaxie.dapcontroller.PlayerViewModel
import kotlinx.coroutines.delay

@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()

    val btPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    var permissionsGranted by remember {
        mutableStateOf(btPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    var askedOnce by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.all { it.value }
        askedOnce = true
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) permLauncher.launch(btPermissions)
    }

    val isPermanentlyDenied = askedOnce && !permissionsGranted &&
        btPermissions.none {
            ActivityCompat.shouldShowRequestPermissionRationale(
                context as ComponentActivity, it
            )
        }

    val btAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    var isBtEnabled by remember { mutableStateOf(btAdapter?.isEnabled == true) }

    // Poll for BT state when it's off so the UI updates when the user enables it
    LaunchedEffect(isBtEnabled) {
        if (!isBtEnabled) {
            while (true) {
                delay(1_000)
                if (btAdapter?.isEnabled == true) {
                    isBtEnabled = true
                    break
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var shownReason by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connectionState) {
        val failed = connectionState as? ConnectionState.Failed
        if (failed != null && failed.reason != shownReason) {
            shownReason = failed.reason
            snackbarHostState.showSnackbar(failed.reason)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when {
            connectionState is ConnectionState.Connecting -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Connecting to ${(connectionState as ConnectionState.Connecting).deviceName}…")
                    }
                }
            }
            !permissionsGranted -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("Bluetooth permissions are required to connect to your DAP.")
                        if (isPermanentlyDenied) {
                            Button(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            }) { Text("Open Settings") }
                        } else {
                            Button(onClick = { permLauncher.launch(btPermissions) }) {
                                Text("Grant Permissions")
                            }
                        }
                    }
                }
            }
            !isBtEnabled -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("Bluetooth is off. Enable it to connect to your DAP.")
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }) { Text("Open Bluetooth Settings") }
                    }
                }
            }
            else -> {
                val bondedDevices = remember(permissionsGranted) {
                    btAdapter?.bondedDevices?.toList() ?: emptyList()
                }
                if (bondedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No paired devices found. Pair your DAP in Bluetooth settings.",
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                        items(bondedDevices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.connect(device) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.name ?: device.address)
                                    Text(device.address)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
