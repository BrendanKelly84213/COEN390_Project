package com.example.frontend.ui

// Compose runtime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// Compose layout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// Compose Material 3
import androidx.compose.material3.*

// Compose UI
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Application data
import com.example.frontend.data.SensorRepository
import com.example.frontend.data.SensorReading

@Composable
 fun SensorList(repository: SensorRepository, modifier: Modifier = Modifier) {
    var sensors by remember { mutableStateOf<List<SensorReading>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedSensorId by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    DisposableEffect(repository, retryKey) {
        isLoading = true
        errorMessage = null

        val registration = repository.listenToLatest(
            onUpdate = { readings ->
                sensors = readings
                isLoading = false
                errorMessage = null
                if (selectedSensorId != null && readings.none { it.id == selectedSensorId }) {
                    selectedSensorId = null
                }
            },
            onError = { error ->
                errorMessage = error.localizedMessage ?: "Failed to load sensor data"
                isLoading = false
            },
        )

        onDispose { registration.remove() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = "Terrarium Sensors",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Live readings from Firebase Firestore",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "alerts") {
            AlertsSection(repository = repository)
        }

        if (errorMessage != null && sensors.isNotEmpty()) {
            item(key = "warning") {
                ErrorBanner(
                    message = errorMessage ?: "",
                    actionLabel = "Retry",
                    onAction = { retryKey++ },
                )
            }
        }

        when {
            isLoading && sensors.isEmpty() -> {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Connecting to Firestore…")
                        }
                    }
                }
            }

            errorMessage != null && sensors.isEmpty() -> {
                item(key = "error") {
                    ErrorBanner(
                        message = errorMessage ?: "",
                        actionLabel = "Retry",
                        onAction = { retryKey++ },
                    )
                }
            }

            sensors.isEmpty() -> {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No sensor readings yet. Start the Raspberry Pi backend to upload data.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            else -> {
                items(sensors, key = { it.id }) { sensor ->
                    SensorCard(
                        sensor = sensor,
                        selected = sensor.id == selectedSensorId,
                        onClick = {
                            selectedSensorId =
                                if (selectedSensorId == sensor.id) null else sensor.id
                        },
                    )
                }

                val selected = sensors.firstOrNull { it.id == selectedSensorId }
                if (selected != null) {
                    item(key = "history-${selected.id}") {
                        HistorySection(repository = repository, sensor = selected)
                    }
                }
            }
        }
    }
}