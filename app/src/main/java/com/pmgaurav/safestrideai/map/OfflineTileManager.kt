package com.pmgaurav.safestrideai.map

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class OfflineTileManager @Inject constructor(
    @ApplicationContext context: Context,
    private val tileDao: TileDao,
    private val okHttpClient: OkHttpClient
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val tileServerUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    private fun isNetworkAvailable(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null && (
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        )
    }

    suspend fun getTile(z: Int, x: Int, y: Int): ByteArray? {
        val tileId = "$z/$x/$y"
        val cachedTile = tileDao.getTile(tileId)
        
        if (cachedTile != null) {
            return cachedTile.data
        }

        if (!isNetworkAvailable()) {
            return null
        }


        return try {
            val data = downloadTile(z, x, y)
            if (data != null) {
                tileDao.insertTile(MapTile(tileId, z, x, y, data))
            }
            data
        } catch (e: Exception) {
            Log.e("OfflineTileManager", "Error downloading tile: $tileId", e)
            null
        }
    }

    private suspend fun downloadTile(z: Int, x: Int, y: Int): ByteArray? = withContext(Dispatchers.IO) {
        val url = tileServerUrl
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SafeStrideAI/1.0 (Android; Contact: pmgaurav@example.com)")
            .build()
            
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                null
            }
        }
    }

    suspend fun prefetchArea(centerLat: Double, centerLng: Double, radiusKm: Double, zoomLevels: List<Int>) {
        val allTiles = mutableListOf<Triple<Int, Int, Int>>()

        for (zoom in zoomLevels) {
            val centerTileX = lon2tile(centerLng, zoom)
            val centerTileY = lat2tile(centerLat, zoom)
            Log.d("OfflineTile", "Prefetching center tile: $zoom/$centerTileX/$centerTileY")
            



            val latDegreeRadius = radiusKm / 111.0
            val lonDegreeRadius = radiusKm / (111.0 * cos(Math.toRadians(centerLat)))
            
            val minLat = centerLat - latDegreeRadius
            val maxLat = centerLat + latDegreeRadius
            val minLon = centerLng - lonDegreeRadius
            val maxLon = centerLng + lonDegreeRadius

            val minX = lon2tile(minLon, zoom)
            val maxX = lon2tile(maxLon, zoom)
            val minY = lat2tile(maxLat, zoom)
            val maxY = lat2tile(minLat, zoom)

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    allTiles.add(Triple(zoom, x, y))
                }
            }
        }

        val total = allTiles.size
        var downloaded = 0
        
        allTiles.forEach { (z, x, y) ->
            getTile(z, x, y)
            downloaded++
            _downloadProgress.value = downloaded.toFloat() / total
        }
        
        _downloadProgress.value = 1.0f
    }

    private fun lon2tile(lon: Double, zoom: Int): Int {
        return floor((lon + 180) / 360 * (1 shl zoom)).toInt()
    }

    private fun lat2tile(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        return floor((1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * (1 shl zoom)).toInt()
    }

    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            tileDao.clearAll()
        }
    }
}

