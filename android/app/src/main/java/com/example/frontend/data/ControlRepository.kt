
package com.example.frontend.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class ControlRepository(
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "ControlRepository"
    }

    private fun getControlDocument(terrariumId: String) =
        firestore.collection("terrariums")
            .document(terrariumId)
            .collection("controls")
            .document("devices")

    fun updateControl(
        terrariumId: String,
        controlName: String,
        enabled: Boolean,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val allowedControls = setOf(
            "lightingEnabled",
            "heaterEnabled",
            "humidifierEnabled",
            "automaticControlEnabled"
        )

        if (controlName !in allowedControls) {
            onError(
                IllegalArgumentException(
                    "Invalid control name: $controlName"
                )
            )
            return
        }

        val document = getControlDocument(terrariumId)

        val updates = mapOf(
            controlName to enabled,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        Log.d(TAG, "Writing to: terrariums/$terrariumId/controls/devices")
        Log.d(TAG, "Updating $controlName to $enabled")

        document.set(updates, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "SUCCESS: $controlName updated to $enabled")
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "FAILED to update $controlName", exception)
                onError(exception)
            }
    }

    fun listenToControls(
        terrariumId: String,
        onUpdate: (Map<String, Any>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {

        return getControlDocument(terrariumId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Log.e(TAG, "Error listening to controls", error)
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Received controls: ${snapshot.data}")
                    onUpdate(snapshot.data ?: emptyMap())
                }
            }
    }
}
