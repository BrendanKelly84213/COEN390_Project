
package com.example.frontend.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.example.frontend.components.TopBar
import com.example.frontend.data.SensorReading
import com.example.frontend.data.SensorRepository
import com.example.frontend.ui.theme.sageGreen
import com.example.frontend.ui.theme.FrontendTheme

import com.google.firebase.firestore.ListenerRegistration

@Composable
fun TerrariumScreen(
    terrariumId: String,
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onLivestreamClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSensorClick: (String) -> Unit = {}
) {
    var sensors by remember {
        mutableStateOf<List<SensorReading>>(emptyList())
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    // Listen for sensor updates from Firestore
    DisposableEffect(terrariumId) {
        var listener: ListenerRegistration? = null

        val repository = SensorRepository.create()

        if (repository != null) {
            listener = repository.listenToLatest(
                onUpdate = { readings ->
                    sensors = readings.filter {
                        it.terrariumId == terrariumId
                    }
                    errorMessage = null
                },
                onError = { exception ->
                    errorMessage = exception.message
                }
            )
        } else {
            errorMessage = "Unable to connect to Firestore."
        }

        onDispose {
            listener?.remove()
        }
    }

    Scaffold(
        containerColor = sageGreen,
        topBar = {
            TopBar(
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick,
                onBackClick = onBackClick,
                onProfileClick = onProfileClick
            )
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Terrarium title
            item {
                Text(
                    text = terrariumId,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // Livestream thumbnail placeholder
            item {
                Text(
                    text = "Livestream",
                    style = MaterialTheme.typography.titleMedium
                )

                Card(
                    onClick = onLivestreamClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Livestream Thumbnail\nTap to view stream",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // Sensor section title
            item {
                Text(
                    text = "Sensors",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // Error message
            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Sensor cards
            if (sensors.isEmpty() && errorMessage == null) {
                item {
                    Text("No sensors found for this terrarium.")
                }
            }

            items(
                items = sensors,
                key = { it.id }
            ) { sensor ->

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSensorClick(sensor.id)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            text = sensor.id,
                            style = MaterialTheme.typography.titleMedium
                        )

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Temperature")
                            Text("%.1f °C".format(sensor.temperatureC))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Humidity")
                            Text("%.1f %%".format(sensor.humidityPct))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Address")
                            Text(sensor.address)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bus")
                            Text(sensor.busNum.toString())
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TerrariumScreenPreview() {
    FrontendTheme {
        TerrariumScreen(
            terrariumId = "Terrarium 1"
        )
    }
}
