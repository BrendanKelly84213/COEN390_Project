
package com.example.frontend.screens

import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


import com.example.frontend.components.TopBar
import com.example.frontend.components.TerrariumCard
import com.example.frontend.data.SensorReading
import com.example.frontend.data.SensorRepository
import com.example.frontend.data.Terrarium
import com.example.frontend.data.TerrariumRepository
import com.example.frontend.ui.theme.lightGreen

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

    var terrariums by remember {
        mutableStateOf<List<Terrarium>>(emptyList())
    }

    var showCreateDialog by remember {
        mutableStateOf(false)
    }

    var terrariumName by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var isCreating by remember {
        mutableStateOf(false)
    }

    val terrariumRepository = remember {
        TerrariumRepository()
    }

    // Listen to terrariums and sensors from Firestore
    if (previewSensors == null) {
        DisposableEffect(Unit) {
            val terrariumListener =
                terrariumRepository.listenToTerrariums(
                    onUpdate = { updatedTerrariums ->
                        terrariums = updatedTerrariums
                    },
                    onError = { error ->
                        errorMessage = error.message
                    }
                )

            onDispose {
                terrariumListener.remove()
            }
        }

        DisposableEffect(Unit) {
            val repository = SensorRepository.create()

            val sensorListener = repository?.listenToLatest(
                onUpdate = { readings ->
                    sensors = readings

                    Log.d(
                        "DashboardScreen",
                        "Received ${readings.size} sensors"
                    )

                    readings.forEach { sensor ->
                        Log.d(
                            "DashboardScreen",
                            "Sensor ${sensor.id}, terrariumId: ${sensor.terrariumId}"
                        )
                    }
                },
                onError = { error ->
                    Log.e(
                        "DashboardScreen",
                        "Sensor listener failed",
                        error
                    )
                    errorMessage = error.message
                }
            )

            onDispose {
                sensorListener?.remove()
            }
        }
    }

    // Preview data
    val displayedSensors = previewSensors ?: sensors

    val displayedTerrariums = if (previewSensors != null) {
        displayedSensors
            .groupBy { it.terrariumId }
            .map { (id, _) ->
                Terrarium(id = id, name = id)
            }
    } else {
        terrariums
    }

    Scaffold(
        modifier = Modifier.background(lightGreen),

        topBar = {
            TopBar(
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick,
                onProfileClick = onProfileClick
            )
        },

        bottomBar = {
            Surface(
                color = lightGreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        terrariumName = ""
                        errorMessage = null
                        showCreateDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(55.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E5939),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "+ Create Terrarium",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
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
                items = displayedTerrariums,
                key = { it.id }
            ) { terrarium ->

                var showDeleteConfirmation by remember(terrarium.id) {
                    mutableStateOf(false)
                }

                var isDeleting by remember(terrarium.id) {
                    mutableStateOf(false)
                }

                Log.d(
                    "DashboardScreen",
                    "Terrarium: ${terrarium.name}, ID: ${terrarium.id}"
                )

                val terrariumSensors = displayedSensors.filter {
                    it.terrariumId == terrarium.id
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {

                    // Existing terrarium card
                    TerrariumCard(
                        terrariumName = terrarium.name,
                        readings = terrariumSensors,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onTerrariumClick(terrarium.id)
                        },
                        onDeleteClick = {
                            showDeleteConfirmation = true
                        }
                    )

                }

                // Delete confirmation dialog
                if (showDeleteConfirmation) {
                    AlertDialog(
                        onDismissRequest = {
                            if (!isDeleting) {
                                showDeleteConfirmation = false
                            }
                        },
                        title = {
                            Text("Delete Terrarium")
                        },
                        text = {
                            Text(
                                "Are you sure you want to delete \"${terrarium.name}\"? " +
                                        "This action cannot be undone."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !isDeleting,
                                onClick = {
                                    isDeleting = true

                                    terrariumRepository.deleteTerrarium(
                                        terrariumId = terrarium.id,
                                        onSuccess = {
                                            isDeleting = false
                                            showDeleteConfirmation = false
                                        },
                                        onError = { exception ->
                                            isDeleting = false
                                            errorMessage = exception.message
                                                ?: "Failed to delete terrarium."
                                            showDeleteConfirmation = false
                                        }
                                    )
                                }
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        "Delete",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !isDeleting,
                                onClick = {
                                    showDeleteConfirmation = false
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

    // Create Terrarium dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isCreating) {
                    showCreateDialog = false
                }
            },

            title = {
                Text("Create Terrarium")
            },

            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Enter a name for your new terrarium.")

                    OutlinedTextField(
                        value = terrariumName,
                        onValueChange = {
                            terrariumName = it
                            errorMessage = null
                        },
                        label = {
                            Text("Terrarium Name")
                        },
                        singleLine = true,
                        enabled = !isCreating
                    )

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    enabled = terrariumName.isNotBlank() && !isCreating,
                    onClick = {
                        isCreating = true
                        errorMessage = null

                        terrariumRepository.createTerrarium(
                            name = terrariumName.trim(),

                            onSuccess = {
                                isCreating = false
                                showCreateDialog = false
                                terrariumName = ""
                            },

                            onError = { error ->
                                isCreating = false
                                errorMessage =
                                    error.message ?: "Failed to create terrarium."
                            }
                        )
                    }
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Create")
                    }
                }
            },

            dismissButton = {
                TextButton(
                    enabled = !isCreating,
                    onClick = {
                        showCreateDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
