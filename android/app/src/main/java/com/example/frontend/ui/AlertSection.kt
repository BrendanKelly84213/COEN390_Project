package com.example.frontend.ui

<<<<<<< Updated upstream
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

import com.example.frontend.data.SensorAlert
import java.util.Locale



@Composable
public fun AlertsSection(repository: SensorRepository) {
    var alerts by remember { mutableStateOf<List<SensorAlert>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    DisposableEffect(repository, retryKey) {
        isLoading = true
        errorMessage = null

        val registration = repository.listenToAlerts(
            onUpdate = { recentAlerts ->
                alerts = recentAlerts
                isLoading = false
                errorMessage = null
            },
            onError = { error ->
                errorMessage = error.localizedMessage ?: "Failed to load alerts"
                isLoading = false
            },
        )

        onDispose { registration.remove() }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (alerts.isNotEmpty()) {
                    Text(
                        text = "${alerts.size} recent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                            text = "Loading alerts…",
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

                alerts.isEmpty() -> {
                    Text(
                        text = "No alerts yet. Alerts appear here when a reading crosses a configured threshold.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    alerts.forEachIndexed { index, alert ->
                        AlertRow(alert)
                        if (index != alerts.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: SensorAlert) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = alertTitle(alert),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = alert.sensorId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = alertDetail(alert),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Recorded ${formatTimestamp(alert.recordedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun alertTitle(alert: SensorAlert): String {
    val metric = when (alert.type.lowercase(Locale.US)) {
        "temperature" -> "temperature"
        "humidity" -> "humidity"
        else -> alert.type
    }
    val direction = when (alert.condition.lowercase(Locale.US)) {
        "below" -> "Low"
        "above" -> "High"
        else -> alert.condition.replaceFirstChar { it.uppercase() }
    }
    return "$direction $metric alert"
}

private fun alertDetail(alert: SensorAlert): String {
    if (alert.message.isNotBlank()) {
        return alert.message
    }

    val reading = formatAlertValue(alert.value, alert.unit)
    val threshold = formatAlertValue(alert.threshold, alert.unit)
    val thresholdLabel = when (alert.condition.lowercase(Locale.US)) {
        "below" -> "minimum"
        "above" -> "maximum"
        else -> "threshold"
    }
    return "Reading $reading • $thresholdLabel $threshold"
}

private fun formatAlertValue(value: Double, unit: String): String =
    if (unit.isBlank()) {
        String.format(Locale.getDefault(), "%.2f", value)
    } else {
        String.format(Locale.getDefault(), "%.2f %s", value, unit)
    }
=======
>>>>>>> Stashed changes
