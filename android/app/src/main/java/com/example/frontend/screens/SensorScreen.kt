
package com.example.frontend.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

import com.example.frontend.components.TopBar
import com.example.frontend.data.SensorReading
import com.example.frontend.data.SensorRepository
import com.example.frontend.ui.theme.sageGreen
import com.example.frontend.ui.theme.FrontendTheme

import com.google.firebase.firestore.ListenerRegistration

data class SensorSettings(
    val sensorId: String,
    val minTemperature: Double,
    val maxTemperature: Double,
    val minHumidity: Double,
    val maxHumidity: Double
)

@Composable
fun SensorScreen(
    sensorId: String,
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSaveSettings: (SensorSettings) -> Unit = {}
) {
    var sensor by remember {
        mutableStateOf<SensorReading?>(null)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    // Editable sensor thresholds
    var minTemperature by remember(sensorId) {
        mutableStateOf("18.0")
    }

    var maxTemperature by remember(sensorId) {
        mutableStateOf("30.0")
    }

    var minHumidity by remember(sensorId) {
        mutableStateOf("50.0")
    }

    var maxHumidity by remember(sensorId) {
        mutableStateOf("80.0")
    }

    var validationError by remember {
        mutableStateOf<String?>(null)
    }

    // Listen for updates to the selected sensor
    DisposableEffect(sensorId) {
        var listener: ListenerRegistration? = null

        val repository = SensorRepository.create()

        if (repository != null) {
            listener = repository.listenToLatest(
                onUpdate = { readings ->
                    sensor = readings.firstOrNull {
                        it.id == sensorId
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Sensor title
            Text(
                text = sensorId,
                style = MaterialTheme.typography.headlineMedium
            )

            // Live readings
            Text(
                text = "Live Sensor Readings",
                style = MaterialTheme.typography.titleLarge
            )

            if (sensor != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ReadingCard(
                        title = "Temperature",
                        value = "%.1f °C".format(sensor!!.temperatureC),
                        modifier = Modifier.weight(1f)
                    )

                    ReadingCard(
                        title = "Humidity",
                        value = "%.1f %%".format(sensor!!.humidityPct),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Sensor hardware information
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Sensor Information",
                            style = MaterialTheme.typography.titleMedium
                        )

                        HorizontalDivider()

                        InfoRow("Sensor ID", sensor!!.id)
                        InfoRow("I²C Address", sensor!!.address)
                        InfoRow("Bus Number", sensor!!.busNum.toString())
                    }
                }
            } else {
                Text(
                    text = errorMessage ?: "Loading sensor readings..."
                )
            }


// Editable settings
            Text(
                text = "Sensor Settings",
                style = MaterialTheme.typography.titleLarge
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = sageGreen
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // Temperature thresholds
                    Text(
                        text = "Temperature Thresholds",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThresholdField(
                            label = "Min (°C)",
                            value = minTemperature,
                            onValueChange = { minTemperature = it },
                            modifier = Modifier.weight(1f)
                        )

                        ThresholdField(
                            label = "Max (°C)",
                            value = maxTemperature,
                            onValueChange = { maxTemperature = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    // Humidity thresholds
                    Text(
                        text = "Humidity Thresholds",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThresholdField(
                            label = "Min (%)",
                            value = minHumidity,
                            onValueChange = { minHumidity = it },
                            modifier = Modifier.weight(1f)
                        )

                        ThresholdField(
                            label = "Max (%)",
                            value = maxHumidity,
                            onValueChange = { maxHumidity = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Validation error
                    if (validationError != null) {
                        Text(
                            text = validationError ?: "",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Save button
                    Button(
                        onClick = {
                            val minTemp = minTemperature.toDoubleOrNull()
                            val maxTemp = maxTemperature.toDoubleOrNull()
                            val minHum = minHumidity.toDoubleOrNull()
                            val maxHum = maxHumidity.toDoubleOrNull()

                            if (
                                minTemp == null ||
                                maxTemp == null ||
                                minHum == null ||
                                maxHum == null
                            ) {
                                validationError = "Please enter valid numbers."
                            } else if (
                                minTemp >= maxTemp ||
                                minHum >= maxHum ||
                                minHum < 0 ||
                                maxHum > 100
                            ) {
                                validationError =
                                    "Check minimum and maximum values. Humidity must be between 0 and 100."
                            } else {
                                validationError = null

                                onSaveSettings(
                                    SensorSettings(
                                        sensorId = sensorId,
                                        minTemperature = minTemp,
                                        maxTemperature = maxTemp,
                                        minHumidity = minHum,
                                        maxHumidity = maxHum
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E5939)
                        )
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}


@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label)
        Text(text = value)
    }
}


@Composable
private fun ThresholdField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        )
    )
}

@Preview(showBackground = true)
@Composable
fun SensorScreenPreview() {
    FrontendTheme {
        SensorScreen(
            sensorId = "sensor_1"
        )
    }
}