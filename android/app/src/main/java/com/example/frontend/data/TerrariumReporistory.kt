
package com.example.frontend.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

data class Terrarium(
    val id: String,
    val name: String
)

class TerrariumRepository(
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {
    private val collection = firestore.collection("terrariums")

    fun createTerrarium(
        name: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val document = collection.document()

        val data = hashMapOf(
            "name" to name,
            "created_at" to FieldValue.serverTimestamp()
        )

        document.set(data)
            .addOnSuccessListener {
                onSuccess(document.id)
            }
            .addOnFailureListener { error ->
                onError(error)
            }
    }


    fun listenToTerrariums(
        onUpdate: (List<Terrarium>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return collection
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    android.util.Log.e(
                        "TerrariumRepository",
                        "Failed to retrieve terrariums",
                        error
                    )
                    onError(error)
                    return@addSnapshotListener
                }

                val terrariums = snapshot?.documents.orEmpty()
                    .mapNotNull { document ->

                        val name = document.getString("name")

                        if (name.isNullOrBlank()) {
                            android.util.Log.w(
                                "TerrariumRepository",
                                "Terrarium ${document.id} has no valid name"
                            )
                            return@mapNotNull null
                        }

                        Terrarium(
                            id = document.id,
                            name = name
                        )
                    }

                android.util.Log.d(
                    "TerrariumRepository",
                    "Retrieved ${terrariums.size} terrariums"
                )

                onUpdate(terrariums)
            }
    }

    fun getTerrarium(
        terrariumId: String,
        onSuccess: (Terrarium) -> Unit,
        onError: (Exception) -> Unit
    ) {
        collection.document(terrariumId)
            .get()
            .addOnSuccessListener { document ->

                val name = document.getString("name")

                if (document.exists() && !name.isNullOrBlank()) {
                    onSuccess(
                        Terrarium(
                            id = document.id,
                            name = name
                        )
                    )
                } else {
                    onError(
                        Exception("Terrarium not found")
                    )
                }
            }
            .addOnFailureListener { error ->
                onError(error)
            }
    }


}

