
package com.example.frontend

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*

import androidx.core.content.ContextCompat

import com.example.frontend.notifications.AlertNotifications
import com.example.frontend.screens.DashboardScreen
import com.example.frontend.ui.SensorDataScreen
import com.example.frontend.ui.theme.FrontendTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {

    // Requests notification permission from the user.
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // Alerts still appear in the app when notifications are denied.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure notifications.
        AlertNotifications.ensureChannel(this)

        if (FirebaseApp.getApps(this).isNotEmpty()) {
            AlertNotifications.subscribeToAlerts()
        }

        requestNotificationPermission()

        enableEdgeToEdge()

        setContent {
            FrontendTheme {

                // Current screen.
                var currentScreen by remember {
                    mutableStateOf("dashboard")
                }

                // Navigate back to the dashboard.
                BackHandler(
                    enabled = currentScreen != "dashboard"
                ) {
                    currentScreen = "dashboard"
                }

                when (currentScreen) {

                    "dashboard" -> {
                        DashboardScreen(
                            onTerrariumClick = { terrariumId ->
                                currentScreen = "sensorData"
                            }
                        )
                    }

                    "sensorData" -> {
                        SensorDataScreen(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Handles the Android notification permission request.
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }
}
