package com.qaxie.dapcontroller.companion

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var textStatus: TextView
    private lateinit var textDetail: TextView
    private lateinit var textWarning: TextView
    private lateinit var textTrustedDevice: TextView
    private lateinit var buttonForget: Button
    private lateinit var buttonToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textStatus = findViewById(R.id.textStatus)
        textDetail = findViewById(R.id.textDetail)
        textWarning = findViewById(R.id.textWarning)
        textTrustedDevice = findViewById(R.id.textTrustedDevice)
        buttonForget = findViewById(R.id.buttonForget)
        buttonToggle = findViewById(R.id.buttonToggle)

        textWarning.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        buttonForget.setOnClickListener {
            TrustedDeviceStore.clear(this)
            CompanionService._trustedAddress.value = null
            CompanionService._trustedName.value = null
            if (CompanionService.isRunning.value) {
                startService(Intent(this, CompanionService::class.java).apply {
                    action = CompanionService.ACTION_FORGET
                })
            }
        }

        buttonToggle.setOnClickListener {
            if (CompanionService.isRunning.value) {
                stopService(Intent(this, CompanionService::class.java))
            } else {
                requestBluetoothPermissionIfNeeded()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CompanionService.statusText.collect { text ->
                    textDetail.text = text
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CompanionService.isRunning.collect { running ->
                    updateUI(running)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CompanionService.trustedName.collect { name ->
                    updateTrustedDeviceUI(name)
                }
            }
        }

        // Show current trusted device on first load (service may not be running)
        updateTrustedDeviceUI(TrustedDeviceStore.getTrustedName(this))
    }

    override fun onResume() {
        super.onResume()
        // Re-check notification access each time the screen comes back (user may have just enabled it)
        updateWarning()
    }

    private fun updateUI(running: Boolean) {
        textStatus.text = getString(if (running) R.string.status_running else R.string.status_stopped)
        buttonToggle.text = getString(if (running) R.string.action_stop else R.string.action_start)
        updateWarning()
    }

    private fun updateTrustedDeviceUI(name: String?) {
        if (name == null) {
            textTrustedDevice.text = getString(R.string.trusted_device_none)
            buttonForget.visibility = View.GONE
        } else {
            textTrustedDevice.text = getString(R.string.trusted_device_prefix) + name
            buttonForget.visibility = View.VISIBLE
        }
    }

    private fun updateWarning() {
        val accessGranted = isNotificationListenerEnabled()
        textWarning.visibility = if (accessGranted) View.GONE else View.VISIBLE
        // Disable Start if notification access is missing (service would be non-functional)
        buttonToggle.isEnabled = CompanionService.isRunning.value || accessGranted
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val component = ComponentName(this, CompanionNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(component.flattenToString())
    }

    private fun requestBluetoothPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCompanionService()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BT_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BT_PERMISSION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCompanionService()
        }
    }

    private fun startCompanionService() {
        startForegroundService(Intent(this, CompanionService::class.java))
    }

    companion object {
        private const val REQUEST_BT_PERMISSION = 1
    }
}
