package com.example.frontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.frontend.data.Measurement
import com.example.frontend.data.SensorReading
import com.example.frontend.data.SensorRepository
import com.example.frontend.ui.theme.FrontendTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrontendTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SensorDataScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SensorDataScreen(modifier: Modifier = Modifier) {
    val repository = remember { SensorRepository.create() }

    if (repository == null) {
        FirebaseConfigError(modifier)
    } else {
        SensorList(repository = repository, modifier = modifier)
    }
}

@Composable
private fun SensorList(repository: SensorRepository, modifier: Modifier = Modifier) {
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

@Composable
private fun SensorCard(
    sensor: SensorReading,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sensor.id,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (selected) "Hide history" else "View history",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = "Bus ${sensor.busNum} • ${sensor.address}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(label = "Temperature", value = formatTemperature(sensor.temperatureC))
                Metric(label = "Humidity", value = formatHumidity(sensor.humidityPct))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Recorded ${formatTimestamp(sensor.recordedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun HistorySection(repository: SensorRepository, sensor: SensorReading) {
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

@Composable
private fun ErrorBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun FirebaseConfigError(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Firebase isn't configured",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Add android/app/google-services.json from the Firebase console, then rebuild the app.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "The service-account.json file belongs on the Raspberry Pi and must not be added to the Android app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val timestampFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

private fun formatTimestamp(date: Date?): String =
    date?.let { timestampFormatter.format(it) } ?: "unknown time"

private fun formatTemperature(value: Double): String =
    String.format(Locale.getDefault(), "%.2f °C", value)

private fun formatHumidity(value: Double): String =
    String.format(Locale.getDefault(), "%.2f %%", value)
