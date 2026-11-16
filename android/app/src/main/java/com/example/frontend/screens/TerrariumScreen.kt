
package com.example.frontend.screens

import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import com.example.frontend.components.TopBar
import com.example.frontend.data.SensorReading
import com.example.frontend.data.SensorRepository
import com.example.frontend.data.TerrariumRepository
import com.example.frontend.ui.theme.sageGreen
import com.example.frontend.ui.theme.FrontendTheme
import androidx.compose.material3.OutlinedButton

import com.example.frontend.data.ControlRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

    var terrariumName by remember {
        mutableStateOf("Loading terrarium...")
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    // All sensors retrieved from Firestore
    var allSensors by remember {
        mutableStateOf<List<SensorReading>>(emptyList())
    }

// Controls the Add Sensors dialog
    var showAddSensorDialog by remember {
        mutableStateOf(false)
    }

// Selected sensors in the dialog
    var selectedSensorIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

// Assignment status
    var assignmentMessage by remember {
        mutableStateOf<String?>(null)
    }

    var assignmentError by remember {
        mutableStateOf<String?>(null)
    }

    // Environmental control states
    var lightingEnabled by remember {
        mutableStateOf(false)
    }

    var heaterEnabled by remember {
        mutableStateOf(false)
    }

    var humidifierEnabled by remember {
        mutableStateOf(false)
    }
    var automaticControlEnabled by remember {
        mutableStateOf(false)
    }

    val controlRepository = remember {
        ControlRepository()
    }

    // Retrieve the terrarium's display name using its document ID.
    LaunchedEffect(terrariumId) {
        TerrariumRepository().getTerrarium(
            terrariumId = terrariumId,
            onSuccess = { terrarium ->
                terrariumName = terrarium.name
            },
            onError = { exception ->
                terrariumName = "Terrarium"
                errorMessage = exception.message
            }
        )
    }

    // Listen for sensor updates from Firestore.
    DisposableEffect(terrariumId) {
        var listener: ListenerRegistration? = null

        val repository = SensorRepository.create()

        if (repository != null) {
            listener = repository.listenToLatest(
                onUpdate = { readings ->
                    Log.d(
                        "TerrariumScreen",
                        "Requested terrarium ID: $terrariumId"
                    )

                    Log.d(
                        "TerrariumScreen",
                        "Received sensor terrarium IDs: ${
                            readings.map { it.terrariumId }
                        }"
                    )

                    allSensors = readings

                    sensors = readings.filter {
                        it.terrariumId == terrariumId
                    }

                    Log.d(
                        "TerrariumScreen",
                        "Matching sensors: ${sensors.map { it.id }}"
                    )
                },
                onError = { exception ->
                    errorMessage = exception.message
                    Log.e(
                        "TerrariumScreen",
                        "Failed to retrieve sensors",
                        exception
                    )
                }
            )
        } else {
            errorMessage = "Unable to connect to Firestore."
        }

        onDispose {
            listener?.remove()
        }
    }

    DisposableEffect(terrariumId) {
        val listener = controlRepository.listenToControls(
            terrariumId = terrariumId,

            onUpdate = { controls ->
                lightingEnabled =
                    controls["lightingEnabled"] as? Boolean ?: false

                heaterEnabled =
                    controls["heaterEnabled"] as? Boolean ?: false

                humidifierEnabled =
                    controls["humidifierEnabled"] as? Boolean ?: false

                automaticControlEnabled =
                    controls["automaticControlEnabled"] as? Boolean ?: false

                Log.d(
                    "TerrariumScreen",
                    "Controls updated from Firebase: $controls"
                )
            },
            onError = { error ->
                Log.e("TerrariumScreen", "Failed to listen to controls", error)
            }
        )

        onDispose {
            listener.remove()
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
        },
        bottomBar = {
            Surface(
                color = sageGreen,
                shadowElevation = 4.dp
            ) {
                Button(
                    onClick = {
                        selectedSensorIds = emptySet()
                        assignmentMessage = null
                        assignmentError = null
                        showAddSensorDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E5939)
                    )
                ) {
                    Text("Add Sensors")
                }
            }
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Terrarium display name.
            item {
                Text(
                    text = terrariumName,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // Livestream thumbnail placeholder.
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


            // Environmental controls
            item {

                EnvironmentalControls(
                    lightingEnabled = lightingEnabled,
                    onLightingChange = {
                        lightingEnabled = it

                        controlRepository.updateControl(
                            terrariumId = terrariumId,
                            controlName = "lightingEnabled",
                            enabled = it,
                            onError = { error ->
                                Log.e("TerrariumControls", "Lighting update failed", error)
                            }
                        )
                    },

                    heaterEnabled = heaterEnabled,
                    onHeaterChange = {
                        heaterEnabled = it

                        controlRepository.updateControl(
                            terrariumId = terrariumId,
                            controlName = "heaterEnabled",
                            enabled = it,
                            onError = { error ->
                                Log.e("TerrariumControls", "Heater update failed", error)
                            }
                        )
                    },

                    humidifierEnabled = humidifierEnabled,
                    onHumidifierChange = {
                        humidifierEnabled = it

                        controlRepository.updateControl(
                            terrariumId = terrariumId,
                            controlName = "humidifierEnabled",
                            enabled = it,
                            onError = { error ->
                                Log.e("TerrariumControls", "Humidifier update failed", error)
                            }
                        )
                    },

                    automaticControlEnabled = automaticControlEnabled,

                    onAutomaticControlChange = { enabled ->
                        controlRepository.updateControl(
                            terrariumId = terrariumId,
                            controlName = "automaticControlEnabled",
                            enabled = enabled,
                            onSuccess = {
                                Log.d(
                                    "TerrariumControls",
                                    "Automatic control updated to $enabled"
                                )
                            },
                            onError = { error ->
                                Log.e(
                                    "TerrariumControls",
                                    "Automatic control update failed",
                                    error
                                )
                            }
                        )
                    }
                )



            }

            // Sensor section title.
            item {
                Text(
                    text = "Sensors",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // Error message.
            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Empty sensor message.
            if (sensors.isEmpty() && errorMessage == null) {
                item {
                    Text("No sensors found for this terrarium.")
                }
            }

            // Sensor cards.
// Sensor cards.
            items(items = sensors, key = { it.id }) { sensor ->

                var showRemoveConfirmation by remember(sensor.id) {
                    mutableStateOf(false)
                }

                var isRemoving by remember(sensor.id) {
                    mutableStateOf(false)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        onSensorClick(sensor.id)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {

                        // Sensor title and delete button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sensor.id,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    showRemoveConfirmation = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove Sensor",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider()

                        // Temperature and humidity side by side
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Temperature: %.1f °C".format(sensor.temperatureC),
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = "Humidity: %.1f %%".format(sensor.humidityPct),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Hardware information
                        Text(
                            text = "Address: ${sensor.address} | Bus: ${sensor.busNum}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Remove sensor confirmation dialog
                if (showRemoveConfirmation) {
                    AlertDialog(
                        onDismissRequest = {
                            if (!isRemoving) {
                                showRemoveConfirmation = false
                            }
                        },
                        title = {
                            Text("Remove Sensor")
                        },
                        text = {
                            Text(
                                "Are you sure you want to remove ${sensor.id} " +
                                        "from this terrarium?"
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !isRemoving,
                                onClick = {
                                    isRemoving = true

                                    val repository = SensorRepository.create()

                                    if (repository != null) {
                                        repository.removeSensorFromTerrarium(
                                            sensorId = sensor.id,
                                            onSuccess = {
                                                isRemoving = false
                                                showRemoveConfirmation = false
                                            },
                                            onError = { exception ->
                                                isRemoving = false
                                                errorMessage = exception.message
                                                    ?: "Failed to remove sensor."
                                            }
                                        )
                                    } else {
                                        isRemoving = false
                                        errorMessage = "Unable to connect to Firestore."
                                        showRemoveConfirmation = false
                                    }
                                }
                            ) {
                                if (isRemoving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        "Remove",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !isRemoving,
                                onClick = {
                                    showRemoveConfirmation = false
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
    // Add Sensors Dialog
    if (showAddSensorDialog) {

        // Sensors not already assigned to this terrarium
        val availableSensors = allSensors.filter {
            it.terrariumId != terrariumId
        }

        AlertDialog(
            onDismissRequest = {
                showAddSensorDialog = false
            },

            title = {
                Text("Add Sensors")
            },

            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        text = "Select the sensors you want to add to $terrariumName."
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (availableSensors.isEmpty()) {

                        Text(
                            text = "No additional sensors are available."
                        )

                    } else {

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            items(
                                items = availableSensors,
                                key = { it.id }
                            ) { sensor ->

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    Checkbox(
                                        checked = sensor.id in selectedSensorIds,
                                        onCheckedChange = { checked ->

                                            selectedSensorIds =
                                                if (checked) {
                                                    selectedSensorIds + sensor.id
                                                } else {
                                                    selectedSensorIds - sensor.id
                                                }
                                        }
                                    )

                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {

                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {

                                            Text(
                                                text = sensor.id,
                                                style = MaterialTheme.typography.titleSmall
                                            )

                                            HorizontalDivider()

                                            Text(
                                                text = "Temperature: %.1f °C".format(
                                                    sensor.temperatureC
                                                )
                                            )

                                            Text(
                                                text = "Humidity: %.1f %%".format(
                                                    sensor.humidityPct
                                                )
                                            )

                                            Text(
                                                text = "Address: ${sensor.address}"
                                            )

                                            Text(
                                                text = "Bus: ${sensor.busNum}"
                                            )





                                        }
                                    }
                                }
                            }
                            item {
                                if (assignmentMessage != null) {
                                    Text(
                                        text = assignmentMessage ?: "",
                                        color = Color(0xFF2E5939)
                                    )
                                }

                                if (assignmentError != null) {
                                    Text(
                                        text = assignmentError ?: "",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },

            confirmButton = {
                Button(
                    enabled = selectedSensorIds.isNotEmpty(),
                    onClick = {

                        val repository = SensorRepository.create()

                        if (repository != null) {

                            repository.assignSensorsToTerrarium(
                                sensorIds = selectedSensorIds.toList(),
                                terrariumId = terrariumId,

                                onSuccess = {
                                    assignmentMessage =
                                        "Sensors successfully added."

                                    showAddSensorDialog = false
                                },

                                onError = { exception ->
                                    assignmentError =
                                        exception.message
                                            ?: "Unable to assign sensors."
                                }
                            )

                        } else {
                            assignmentError =
                                "Unable to connect to Firestore."
                        }
                    }
                ) {
                    Text("Add Selected")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showAddSensorDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}



@Composable
fun EnvironmentalControls(
    lightingEnabled: Boolean,
    onLightingChange: (Boolean) -> Unit,
    heaterEnabled: Boolean,
    onHeaterChange: (Boolean) -> Unit,
    humidifierEnabled: Boolean,
    onHumidifierChange: (Boolean) -> Unit,
    automaticControlEnabled: Boolean,
    onAutomaticControlChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Environmental Controls",
                style = MaterialTheme.typography.titleSmall
            )

            HorizontalDivider()

            ControlSwitchRow(
                title = "Lighting",
                checked = lightingEnabled,
                onCheckedChange = onLightingChange
            )

            ControlSwitchRow(
                title = "Heater",
                checked = heaterEnabled,
                onCheckedChange = onHeaterChange,
                enabled = !automaticControlEnabled
            )

            ControlSwitchRow(
                title = "Humidifier",
                checked = humidifierEnabled,
                onCheckedChange = onHumidifierChange,
                enabled = !automaticControlEnabled
            )

            HorizontalDivider()

            ControlSwitchRow(
                title = "Automatic Control",
                checked = automaticControlEnabled,
                onCheckedChange = onAutomaticControlChange
            )
        }
    }
}

@Composable
fun ControlSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.height(28.dp)
        )
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
