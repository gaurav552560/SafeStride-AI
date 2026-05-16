package com.pmgaurav.safestrideai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HazardDao {
    @Query("SELECT * FROM hazards")
    fun getAllHazards(): Flow<List<HazardPin>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHazard(hazard: HazardPin): Long

    @Query("SELECT * FROM hazards WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng")
    suspend fun getHazardsInArea(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<HazardPin>

    @Query("SELECT * FROM hazards WHERE ABS(latitude - :lat) < (:radius / 111111.0) AND ABS(longitude - :lng) < (:radius / 111111.0)")
    fun getNearbyHazards(lat: Double, lng: Double, radius: Double): Flow<List<HazardPin>>

    @Query("UPDATE hazards SET severity = :severity WHERE id = :id")
    suspend fun updateSeverity(id: Long, severity: Int)

    @Query("DELETE FROM hazards WHERE id = :id")
    suspend fun deleteHazardById(id: Long)
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DetectionSession): Long

    @Update
    suspend fun updateSession(session: DetectionSession)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<DetectionSession>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): DetectionSession?

    @Query("UPDATE sessions SET endTime = :time WHERE endTime IS NULL")
    suspend fun closeOpenSessions(time: Long)

    @Query("UPDATE sessions SET endTime = :endTime, endLatitude = :endLat, endLongitude = :endLng, averageSafetyScore = :score, distanceTravelled = :dist WHERE id = :sessionId")
    suspend fun updateSessionEnd(sessionId: Long, endTime: Long, endLat: Double?, endLng: Double?, score: Float, dist: Float)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: DetectionEvent)

    @Query("SELECT * FROM events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getEventsForSession(sessionId: Long): Flow<List<DetectionEvent>>
}

