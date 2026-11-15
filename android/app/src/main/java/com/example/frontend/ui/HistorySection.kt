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
import com.example.frontend.data.Measurement

@Composable
fun HistorySection(repository: SensorRepository, sensor: SensorReading) {
    var measurements by remember(sensor.id) {
        mutableStateOf<List<Measurement>>(emptyList())
    }
    var isLoading by remember(sensor.id) { mutableStateOf(true) }
    var errorMessage by remember(sensor.id) { mutableStateOf<String?>(null) }
    var retryKey by remember(sensor.id) { mutableStateOf(0) }

    DisposableEffect(repository, sensor.id, retryKey) {
        isLoading = true
        errorMessage = null

        val registration = repository.listenToHistory(
            sensorId = sensor.id,
            onUpdate = { history ->
                measurements = history
                isLoading = false
                errorMessage = null
            },
            onError = { error ->
                errorMessage = error.localizedMessage ?: "Failed to load history"
                isLoading = false
            },
        )

        onDispose { registration.remove() }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recent history — ${sensor.id}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Loading history…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                errorMessage != null -> {
                    ErrorBanner(
                        message = errorMessage ?: "",
                        actionLabel = "Retry",
                        onAction = { retryKey++ },
                    )
                }

                measurements.isEmpty() -> {
                    Text(
                        text = "No measurements stored yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    measurements.forEachIndexed { index, measurement ->
                        HistoryRow(measurement)
                        if (index != measurements.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(measurement: Measurement) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = "${formatTemperature(measurement.temperatureC)}  •  ${formatHumidity(measurement.humidityPct)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = formatTimestamp(measurement.recordedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

