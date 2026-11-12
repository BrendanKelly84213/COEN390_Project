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
