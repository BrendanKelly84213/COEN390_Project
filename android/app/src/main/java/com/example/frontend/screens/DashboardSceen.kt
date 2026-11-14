
package com.example.frontend.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.example.frontend.components.TopBar
import com.example.frontend.components.TerrariumCard
import com.example.frontend.data.SensorReading
import com.example.frontend.data.SensorRepository
import com.example.frontend.ui.theme.lightGreen
import com.example.frontend.ui.theme.FrontendTheme

@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onTerrariumClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    previewSensors: List<SensorReading>? = null
) {
    var sensors by remember {
        mutableStateOf<List<SensorReading>>(emptyList())
    }

    if (previewSensors == null) {
        DisposableEffect(Unit) {
            val repository = SensorRepository.create()

            val listener = repository?.listenToLatest(
                onUpdate = { readings ->
                    sensors = readings
                },
                onError = { error ->
                    // Handle error
                }
            )

            onDispose {
                listener?.remove()
            }
        }
    }

    val displayedSensors = previewSensors ?: sensors
    val groupedSensors = displayedSensors.groupBy { it.terrariumId }

    Scaffold(
        modifier = Modifier.background(lightGreen),
        topBar = {
            TopBar(
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick,
                onProfileClick = onProfileClick
            )
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),

            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = groupedSensors.toList(),
                key = { it.first }
            ) { (terrariumId, readings) ->

                TerrariumCard(
                    terrariumId = terrariumId,
                    readings = readings,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onTerrariumClick(terrariumId)
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {

    val sampleSensors = listOf(
        SensorReading(
            id = "sensor_1",
            terrariumId = "Terrarium 1",
            busNum = 1,
            address = "0x44",
            temperatureC = 24.5,
            humidityPct = 65.0,
            recordedAt = null
        ),
        SensorReading(
            id = "sensor_2",
            terrariumId = "Terrarium 1",
            busNum = 1,
            address = "0x45",
            temperatureC = 25.0,
            humidityPct = 68.0,
            recordedAt = null
        )

    )

    FrontendTheme {
        DashboardScreen(previewSensors = sampleSensors)
    }
}
