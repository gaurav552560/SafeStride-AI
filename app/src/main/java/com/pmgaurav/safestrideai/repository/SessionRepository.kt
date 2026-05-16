package com.pmgaurav.safestrideai.repository

import com.pmgaurav.safestrideai.data.AppDatabase
import com.pmgaurav.safestrideai.data.DetectionEvent
import com.pmgaurav.safestrideai.data.DetectionSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(private val db: AppDatabase) {

    val allSessions: Flow<List<DetectionSession>> = db.sessionDao().getAllSessions()

    suspend fun startSession(lat: Double?, lng: Double?): Long {
        val session = DetectionSession(
            startTime = System.currentTimeMillis(),
            startLatitude = lat,
            startLongitude = lng,
        )
        return db.sessionDao().insertSession(session)
    }

    suspend fun endSession(sessionId: Long, endLat: Double?, endLng: Double?, score: Float, dist: Float) {
        db.sessionDao().updateSessionEnd(
            sessionId, 
            System.currentTimeMillis(), 
            endLat, 
            endLng,
            score,
            dist
        )
    }

    suspend fun logEvent(event: DetectionEvent) {
        db.eventDao().insertEvent(event)
    }

    suspend fun cleanupSessions() {
        db.sessionDao().deleteAllSessions()
    }
}

