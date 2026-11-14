
package com.example.frontend.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.example.frontend.screens.DashboardScreen
import com.example.frontend.screens.TerrariumScreen
import com.example.frontend.screens.SensorScreen
import com.example.frontend.screens.ProfileScreen

@Composable
fun AppNavigation() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {

        // Dashboard Screen
        composable("dashboard") {
            DashboardScreen(
                onTerrariumClick = { terrariumId ->
                    navController.navigate(
                        "terrarium/${Uri.encode(terrariumId)}"
                    )
                },
                onProfileClick = {
                    navController.navigate("profile")
                }
            )
        }

        // Terrarium Screen
        composable("terrarium/{terrariumId}") { backStackEntry ->

            val terrariumId =
                backStackEntry.arguments?.getString("terrariumId") ?: ""

            TerrariumScreen(
                terrariumId = terrariumId,

                onBackClick = {
                    navController.popBackStack()
                },

                onSensorClick = { sensorId ->
                    navController.navigate(
                        "sensor/${Uri.encode(sensorId)}"
                    )
                },

                onProfileClick = {
                    navController.navigate("profile")
                }
            )
        }

        // Sensor Screen
        composable("sensor/{sensorId}") { backStackEntry ->

            val sensorId =
                backStackEntry.arguments?.getString("sensorId") ?: ""

            SensorScreen(
                sensorId = sensorId,

                onBackClick = {
                    navController.popBackStack()
                },

                onProfileClick = {
                    navController.navigate("profile")
                }
            )
        }

        // Profile Screen
        composable("profile") {
            ProfileScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
