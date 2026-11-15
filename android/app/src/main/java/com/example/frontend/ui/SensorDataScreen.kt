package com.example.frontend.ui

<<<<<<< Updated upstream
// Compose runtime
import androidx.compose.runtime.Composable

import androidx.compose.runtime.remember

import androidx.compose.ui.Modifier

// Application data
import com.example.frontend.data.SensorRepository
@Composable

fun SensorDataScreen(modifier: Modifier = Modifier) {
    val repository = remember { SensorRepository.create() }

    if (repository == null) {
        FirebaseConfigError(modifier)
    } else {
        SensorList(repository = repository, modifier = modifier)
    }
}
=======
>>>>>>> Stashed changes
