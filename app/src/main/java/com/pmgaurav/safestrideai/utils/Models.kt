package com.pmgaurav.safestrideai.utils 
 
data class DetectedObject( 
    val id: Int, 
    val label: String, 
    val confidence: Float, 
    val boundingBox: RectF,
    val ttc: Float = Float.MAX_VALUE,
    val distanceMeters: Float = 0f,
    val speedKmph: Float = 0f
) 
 
data class RectF( 
    val left: Float, 
    val top: Float, 
    val right: Float, 
    val bottom: Float,
) { 
    val width: Float get() = right - left 
    val height: Float get() = bottom - top 
} 
 
data class OverallStats(
    val totalSessions: Int = 0,
    val avgSafetyScore: Int = 0,
    val totalDistanceKm: Float = 0f,
    val totalAlerts: Int = 0,
    val tier3Total: Int = 0,
    val topClasses: List<ClassStat> = emptyList()
)

data class ClassStat(
    val label: String,
    val count: Int,
    val frequency: Float
)

data class DayData(
    val label: String,
    val sessionCount: Int,
    val isToday: Boolean
)

data class SettingsState(
    val audioEnabled: Boolean = true,
    val hapticFeedback: Boolean = true,
    val arOverlays: Boolean = true,
    val v2xEnabled: Boolean = true,
    val cloudSync: Boolean = false,
    val batterySaver: Boolean = false,
    val forceCpu: Boolean = false,
    val userType: String = "PEDESTRIAN",
    val detectionConfidence: Float = 0.5f
)

