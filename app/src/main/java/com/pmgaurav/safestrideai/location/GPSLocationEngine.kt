package com.pmgaurav.safestrideai.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class GPSLocationEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private var locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        500L
    ).apply {
        setMinUpdateIntervalMillis(250L)
        setMaxUpdateDelayMillis(1000L)
        setMinUpdateDistanceMeters(0.2f)
        setWaitForAccurateLocation(true)
    }.build()

    private val _currentLocation = MutableLiveData<LocationData>()
    val currentLocation: LiveData<LocationData> = _currentLocation

    private var totalDistanceMetres = 0f
    private var lastLocation: android.location.Location? = null

    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val speed: Float,
        val bearing: Float,
        val altitude: Double,
        val timestamp: Long,
        val locationQuality: LocationQuality
    )

    enum class LocationQuality {
        HIGH, MEDIUM, LOW, NO_SIGNAL
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->

                lastLocation?.let { last ->
                    if (location.accuracy < 20f && last.accuracy < 20f) {
                        val dist = last.distanceTo(location)
                        if (dist > 1.0f && dist < 50f) {
                            totalDistanceMetres += dist
                        }
                    }
                }
                lastLocation = location

                val quality = when {
                    location.accuracy < 5f -> LocationQuality.HIGH
                    location.accuracy < 15f -> LocationQuality.MEDIUM
                    else -> LocationQuality.LOW
                }
                _currentLocation.postValue(
                    LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        speed = location.speed,
                        bearing = location.bearing,
                        altitude = location.altitude,
                        timestamp = location.time,
                        locationQuality = quality
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        totalDistanceMetres = 0f
        lastLocation = null
        try {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            android.util.Log.e("GPSEngine", "Location permission missing", e)
        }
    }

    fun stopTracking() {
        _currentLocation.postValue(
            LocationData(0.0, 0.0, 0f, 0f, 0f, 0.0, System.currentTimeMillis(), LocationQuality.NO_SIGNAL)
        )
        fusedClient.removeLocationUpdates(locationCallback)
    }

    fun updateRequest(priority: Int, interval: Long) {
        locationRequest = LocationRequest.Builder(priority, interval).build()
        fusedClient.removeLocationUpdates(locationCallback)
        startTracking()
    }

    fun getTotalDistanceKm(): Float = totalDistanceMetres / 1000f

    fun estimateUserSpeedKmph(speed: Float): String {
        return when {
            speed < 0.5f -> "Stationary"
            speed < 1.5f -> "Walking slowly"
            speed < 2.5f -> "Walking"
            speed < 4.0f -> "Walking fast"
            speed < 7.0f -> "Jogging"
            else -> "Running / Vehicle"
        }
    }
}

