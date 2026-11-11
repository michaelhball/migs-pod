package com.migspod

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.migspod.player.PlayerController
import com.migspod.ui.MigsPodApp
import com.migspod.ui.theme.MigsPodTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; nothing to do either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark bars with light icons whatever the system theme: the app is dark only.
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        maybeRequestNotificationPermission()
        setContent {
            MigsPodTheme {
                MigsPodApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PlayerController.connect(this)
    }

    override fun onStop() {
        // Keep the connection across rotation; drop it when actually leaving.
        if (!isChangingConfigurations) PlayerController.disconnect()
        super.onStop()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
