package com.example.frontend.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

data class SensorThresholds(
    val minTemperature: Double,
    val maxTemperature: Double,
    val minHumidity: Double,
    val maxHumidity: Double
)

class SensorRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // Listen for the latest sensor readings
    fun listenToLatest(
        onUpdate: (List<SensorReading>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {

        return firestore.collection(SENSORS_COLLECTION)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val readings = snapshot?.documents
                    ?.mapNotNull { toSensorReading(it) }
                    ?: emptyList()

                onUpdate(readings)
            }
    }

    // Listen for historical measurements from a specific sensor
    fun listenToHistory(
        sensorId: String,
        onUpdate: (List<Measurement>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {

        return firestore.collection(SENSORS_COLLECTION)
            .document(sensorId)
            .collection(MEASUREMENTS_SUBCOLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(DEFAULT_HISTORY_LIMIT)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val measurements = snapshot?.documents
                    ?.mapNotNull { toMeasurement(it) }
                    ?: emptyList()

                onUpdate(measurements)
            }
    }

    // Listen for alerts
    fun listenToAlerts(
        onUpdate: (List<SensorAlert>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {

        return firestore.collection(ALERTS_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(DEFAULT_ALERT_LIMIT)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val alerts = snapshot?.documents
                    ?.mapNotNull { toSensorAlert(it) }
                    ?: emptyList()

                onUpdate(alerts)
            }
    }

    // Convert a Firestore sensor document into SensorReading
    private fun toSensorReading(
        document: DocumentSnapshot
    ): SensorReading? {

        val id = document.id

        return SensorReading(
            id = id,
            terrariumId = document.getString("terrarium_id")
                ?: return null,
            busNum = document.getLong("bus_num")?.toInt() ?: 1,
            address = document.getString("address") ?: "0",
            temperatureC = doubleField(document, "temperature_c"),
            humidityPct = doubleField(document, "humidity_pct"),
            recordedAt = document.getTimestamp("timestamp")?.toDate()
        )
    }

    // Convert a Firestore measurement document into Measurement
    private fun toMeasurement(
        document: DocumentSnapshot
    ): Measurement {

        return Measurement(
            id = document.id,
            temperatureC = doubleField(document, "temperature_c"),
            humidityPct = doubleField(document, "humidity_pct"),
            recordedAt = document.getTimestamp("timestamp")?.toDate()
        )
    }

    // Convert a Firestore alert document into SensorAlert
    private fun toSensorAlert(
        document: DocumentSnapshot
    ): SensorAlert? {

        val type = document.getString("type") ?: return null
        val condition = document.getString("condition") ?: return null

        val value = document.getDouble("value") ?: return null
        val threshold = document.getDouble("threshold") ?: return null

        return SensorAlert(
            id = document.id,
            sensorId = document.getString("sensor_id") ?: "unknown",
            type = type,
            condition = condition,
            value = value,
            threshold = threshold,
            unit = document.getString("unit") ?: "",
            message = document.getString("message") ?: "",
            recordedAt = (
                    document.getTimestamp("timestamp")
                        ?: document.getTimestamp("created_at")
                    )?.toDate()
        )
    }

    // Safely retrieve numeric Firestore fields
    private fun doubleField(
        document: DocumentSnapshot,
        field: String
    ): Double {

        return document.getDouble(field)
            ?: document.getLong(field)?.toDouble()
            ?: 0.0
    }

    // Assign selected sensors to a terrarium
    fun assignSensorsToTerrarium(
        sensorIds: List<String>,
        terrariumId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val batch = firestore.batch()

        sensorIds.forEach { sensorId ->

            val sensorReference = firestore
                .collection(SENSORS_COLLECTION)
                .document(sensorId)

            batch.update(
                sensorReference,
                "terrarium_id",
                terrariumId
            )
        }

        batch.commit()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    // Remove a sensor from its assigned terrarium
    fun removeSensorFromTerrarium(
        sensorId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(SENSORS_COLLECTION)
            .document(sensorId)
            .update("terrarium_id", "")
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }


    // Save threshold settings for a sensor
    fun saveSensorSettings(
        sensorId: String,
        settings: SensorThresholds,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val updates = mapOf(
            "settings.minTemperature" to settings.minTemperature,
            "settings.maxTemperature" to settings.maxTemperature,
            "settings.minHumidity" to settings.minHumidity,
            "settings.maxHumidity" to settings.maxHumidity
        )

        firestore.collection(SENSORS_COLLECTION)
            .document(sensorId)
            .update(updates)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    // Listen for threshold settings from a specific sensor
    fun listenToSensorSettings(
        sensorId: String,
        onUpdate: (SensorThresholds?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {

        return firestore.collection(SENSORS_COLLECTION)
            .document(sensorId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val settings = snapshot?.get("settings") as? Map<*, *>

                if (settings == null) {
                    onUpdate(null)
                    return@addSnapshotListener
                }

                fun getNumber(key: String): Double? {
                    return (settings[key] as? Number)?.toDouble()
                }

                val minTemp = getNumber("minTemperature")
                val maxTemp = getNumber("maxTemperature")
                val minHum = getNumber("minHumidity")
                val maxHum = getNumber("maxHumidity")

                if (
                    minTemp != null &&
                    maxTemp != null &&
                    minHum != null &&
                    maxHum != null
                ) {
                    onUpdate(
                        SensorThresholds(
                            minTemperature = minTemp,
                            maxTemperature = maxTemp,
                            minHumidity = minHum,
                            maxHumidity = maxHum
                        )
                    )
                } else {
                    onUpdate(null)
                }
            }
    }


    companion object {

        private const val SENSORS_COLLECTION = "sensors"
        private const val MEASUREMENTS_SUBCOLLECTION = "measurements"
        private const val ALERTS_COLLECTION = "alerts"

        private const val DEFAULT_HISTORY_LIMIT = 20L
        private const val DEFAULT_ALERT_LIMIT = 20L

        fun create(): SensorRepository? {
            return runCatching {
                SensorRepository()
            }.getOrNull()
        }
    }
}

