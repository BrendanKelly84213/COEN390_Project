
package com.example.frontend.ui

// Compose runtime
import androidx.compose.runtime.*

// Compose layout
import androidx.compose.foundation.layout.*

// Compose shapes
import androidx.compose.foundation.shape.RoundedCornerShape

// Compose Material 3
import androidx.compose.material3.*

// Compose UI
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable

// Application data
import com.example.frontend.data.SensorReading
import com.example.frontend.ui.theme.sageGreen

// Java utilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun SensorCard(
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

fun formatTimestamp(date: Date?): String =
    date?.let { timestampFormatter.format(it) } ?: "unknown time"

fun formatTemperature(value: Double): String =
    String.format(Locale.getDefault(), "%.2f °C", value)

fun formatHumidity(value: Double): String =
    String.format(Locale.getDefault(), "%.2f %%", value)

val timestampFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())


@Composable
fun Metric(label: String, value: String) {
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
fun FirebaseConfigError(modifier: Modifier = Modifier) {
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


@Composable
fun ErrorBanner(
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    onProfileClick: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(text = "SidWatch")
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Text(
                        text = "←",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        },
        actions = {
            TextButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("Profile")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("My Profile") },
                    onClick = {
                        expanded = false
                        onProfileClick()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Logout") },
                    onClick = {
                        expanded = false
                        onLogoutClick()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF2E5939),
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}


@Composable
fun TerrariumCard(
    terrariumName: String,
    readings: List<SensorReading>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLivestreamClick: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = sageGreen
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = terrariumName,
                style = MaterialTheme.typography.titleMedium
            )

            Card(
                onClick = onLivestreamClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2E5939)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "▶",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Text(
                            text = "Live Stream",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (readings.isEmpty()) {
                Text(
                    text = "No sensors connected yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                readings.forEach { sensor ->
                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            sensor.id,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("%.1f°C".format(sensor.temperatureC))
                            Text("%.1f%%".format(sensor.humidityPct))
                        }
                    }
                }
            }
        }
    }
}
