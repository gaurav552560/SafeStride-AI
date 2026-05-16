package com.pmgaurav.safestrideai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hazards")
data class HazardPin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val hazardType: String,
    val severity: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String? = null,
    val sessionId: Long? = null
)

@Entity(tableName = "sessions")
data class DetectionSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var distanceTravelled: Float = 0f,
    var averageSafetyScore: Float = 100f,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    var endLatitude: Double? = null,
    var endLongitude: Double? = null
)

@Entity(tableName = "events")
data class DetectionEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double?,
    val longitude: Double?,
    val detectedObject: String,
    val confidence: Float,
    val distanceToHazard: Float? = null,
    val alertTriggered: Boolean = false
)

