package com.pmgaurav.safestrideai.repository

import com.pmgaurav.safestrideai.blockchain.BlockchainHazardRegistry
import com.pmgaurav.safestrideai.data.AppDatabase
import com.pmgaurav.safestrideai.data.HazardPin
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HazardRepository @Inject constructor(
    private val db: AppDatabase,
    private val blockchainRegistry: BlockchainHazardRegistry,
) {

    fun getAllHazards(): Flow<List<HazardPin>> = db.hazardDao().getAllHazards()

    suspend fun getHazardsInArea(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<HazardPin> {

        syncBlockchainHazards(minLat, maxLat, minLng, maxLng)
        return db.hazardDao().getHazardsInArea(minLat, maxLat, minLng, maxLng)
    }

    private suspend fun syncBlockchainHazards(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double) {
        try {
            val verified = blockchainRegistry.getVerifiedHazards(minLat, maxLat, minLng, maxLng)
            verified.forEach { pin ->
                db.hazardDao().insertHazard(
                    HazardPin(
                        id = pin.id,
                        latitude = pin.latitude,
                        longitude = pin.longitude,
                        hazardType = pin.hazardType,
                        severity = pin.dangerTier,
                        description = "Community Verified (Trust: ${pin.trustScore}%)",
                    )
                )
            }
        } catch (_: Exception) {

        }
    }

    suspend fun insertHazard(hazard: HazardPin) {
        db.hazardDao().insertHazard(hazard)
        

        try {
            blockchainRegistry.reportHazard(
                hazard.latitude, 
                hazard.longitude,
                hazard.severity,
                hazard.hazardType,
                "0x${hazard.timestamp}"
            )
        } catch (_: Exception) {}
    }

    fun observeNearbyPins(lat: Double, lng: Double, radiusM: Int): Flow<List<HazardPin>> {
        return db.hazardDao().getNearbyHazards(lat, lng, radiusM.toDouble())
    }

    suspend fun confirmPin(id: Long) {
        db.hazardDao().updateSeverity(id, 1)
        blockchainRegistry.confirmHazard(id)
    }

    suspend fun disputePin(id: Long) {
        db.hazardDao().deleteHazardById(id)
    }
}

