package com.example.frontend.data

import java.util.Date

/** Latest reading stored at `sensors/{sensorId}`. */
data class SensorReading(
    val id: String,
    val busNum: Int,
    val address: String,
    val temperatureC: Double,
    val humidityPct: Double,
    val recordedAt: Date?,
)

/** One historical document stored at `sensors/{sensorId}/measurements/{id}`. */
data class Measurement(
    val id: String,
    val temperatureC: Double,
    val humidityPct: Double,
    val recordedAt: Date?,
)

/** One alert document stored at `alerts/{alertId}`. */
data class SensorAlert(
    val id: String,
    val sensorId: String,
    val type: String,
    val condition: String,
    val value: Double,
    val threshold: Double,
    val unit: String,
    val message: String,
    val recordedAt: Date?,
)
