package com.example.frontend.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Reads the Firestore documents written by the Raspberry Pi backend.
 *
 * The Pi writes `sensors/{sensorId}` (latest reading),
 * `sensors/{sensorId}/measurements/{id}` (history), and `alerts/{id}` (threshold
 * violations). The Android client is read-only; Firestore security rules should
 * deny writes from the app.
 */
class SensorRepository private constructor(
    private val firestore: FirebaseFirestore,
) {
    /**
     * Emits the latest reading for every sensor whenever Firestore changes.
     */
    fun listenToLatest(
        onUpdate: (List<SensorReading>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        firestore.collection(SENSORS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val readings = snapshot?.documents
                    .orEmpty()
                    .mapNotNull { it.toSensorReading() }
                    .sortedBy { it.id }

                onUpdate(readings)
            }

    /**
     * Emits the newest [limit] measurements for one sensor, newest first.
     */
    fun listenToHistory(
        sensorId: String,
        limit: Long = DEFAULT_HISTORY_LIMIT,
        onUpdate: (List<Measurement>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        firestore.collection(SENSORS_COLLECTION)
            .document(sensorId)
            .collection(MEASUREMENTS_SUBCOLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val measurements = snapshot?.documents
                    .orEmpty()
                    .mapNotNull { it.toMeasurement() }

                onUpdate(measurements)
            }

    /**
     * Emits the newest [limit] alerts, newest first.
     */
    fun listenToAlerts(
        limit: Long = DEFAULT_ALERT_LIMIT,
        onUpdate: (List<SensorAlert>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        firestore.collection(ALERTS_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val alerts = snapshot?.documents
                    .orEmpty()
                    .mapNotNull { it.toSensorAlert() }

                onUpdate(alerts)
            }

    private fun DocumentSnapshot.toSensorReading(): SensorReading? {
        val temperature = doubleField("temperature_c") ?: return null
        val humidity = doubleField("humidity_pct") ?: return null
        val documentId = id

        return SensorReading(
            id = getString("id") ?: documentId,
            busNum = (get("bus_num") as? Number)?.toInt() ?: 0,
            address = getString("address") ?: "unknown",
            temperatureC = temperature,
            humidityPct = humidity,
            recordedAt = (getTimestamp("timestamp") ?: getTimestamp("updated_at"))?.toDate(),
        )
    }

    private fun DocumentSnapshot.toMeasurement(): Measurement? {
        val temperature = doubleField("temperature_c") ?: return null
        val humidity = doubleField("humidity_pct") ?: return null

        return Measurement(
            id = id,
            temperatureC = temperature,
            humidityPct = humidity,
            recordedAt = (getTimestamp("timestamp") ?: getTimestamp("uploaded_at"))?.toDate(),
        )
    }

    private fun DocumentSnapshot.toSensorAlert(): SensorAlert? {
        val type = getString("type") ?: return null
        val condition = getString("condition") ?: return null
        val value = doubleField("value") ?: return null
        val threshold = doubleField("threshold") ?: return null

        return SensorAlert(
            id = id,
            sensorId = getString("sensor_id") ?: "unknown",
            type = type,
            condition = condition,
            value = value,
            threshold = threshold,
            unit = getString("unit").orEmpty(),
            message = getString("message").orEmpty(),
            recordedAt = (getTimestamp("timestamp") ?: getTimestamp("created_at"))?.toDate(),
        )
    }

    private fun DocumentSnapshot.doubleField(name: String): Double? =
        when (val value = get(name)) {
            is Number -> value.toDouble()
            else -> null
        }

    companion object {
        private const val SENSORS_COLLECTION = "sensors"
        private const val MEASUREMENTS_SUBCOLLECTION = "measurements"
        private const val ALERTS_COLLECTION = "alerts"
        const val DEFAULT_HISTORY_LIMIT = 20L
        const val DEFAULT_ALERT_LIMIT = 20L

        /**
         * Returns null when Firebase is not configured in the app, so the UI can
         * show setup instructions instead of crashing.
         */
        fun create(): SensorRepository? =
            runCatching { SensorRepository(FirebaseFirestore.getInstance()) }.getOrNull()
    }
}
