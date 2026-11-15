
package com.example.frontend

import android.Manifest //Remove
import android.content.pm.PackageManager //Remove
import android.os.Build //Remove
import android.os.Bundle //Remove
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts //Remove
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat //Remove
import com.example.frontend.ui.SensorDataScreen
import com.example.frontend.notifications.AlertNotifications //Remove
import com.example.frontend.ui.theme.FrontendTheme //Remove
import com.google.firebase.FirebaseApp //Remove

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

        // Display the frontend.
        setContent {
            FrontendTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    SensorDataScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
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
