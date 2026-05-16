package com.pmgaurav.safestrideai.v2x

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*

class BleV2xChannel(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val advertiser by lazy { bluetoothAdapter?.bluetoothLeAdvertiser }
    private val scanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    companion object {
        private val V2X_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private const val TAG = "BleV2xChannel"
    }

    private var isScanning = false
    private var isAdvertising = false
    private var currentScanCallback: ScanCallback? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            Log.d(TAG, "BLE Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.e(TAG, "BLE Advertising failed: $errorCode")
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun broadcast(payload: ByteArray) {
        val adv = advertiser ?: return
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
                Log.e(TAG, "BLUETOOTH_ADVERTISE permission missing")
                return
            }
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(V2X_SERVICE_UUID))
            .addServiceData(ParcelUuid(V2X_SERVICE_UUID), payload)
            .build()

        try {
            if (isAdvertising) {
                adv.stopAdvertising(advertiseCallback)
            }
            adv.startAdvertising(settings, data, advertiseCallback)
        } catch (_: SecurityException) {
            Log.e(TAG, "SecurityException during advertising")
        }
    }

    fun startScanning(onVehicleDetected: (V2XCommunicationEngine.BasicSafetyMessage) -> Unit) {
        val scn = scanner ?: return
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || isScanning) return


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                Log.e(TAG, "BLUETOOTH_SCAN permission missing")
                return
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(V2X_SERVICE_UUID))
                .build()
        )
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(V2X_SERVICE_UUID)) ?: return
                val bsm = deserializeBSM(serviceData, result.rssi) ?: return
                
                onVehicleDetected(bsm)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                isScanning = false
            }
        }

        try {
            currentScanCallback = scanCallback
            scn.startScan(filters, scanSettings, scanCallback)
            isScanning = true
        } catch (_: SecurityException) {
            Log.e(TAG, "SecurityException during scan")
            isScanning = false
        }
    }

    fun stop() {
        val scn = scanner
        val adv = advertiser
        try {
            if (isAdvertising && adv != null) {
                adv.stopAdvertising(advertiseCallback)
                isAdvertising = false
            }
            if (isScanning && scn != null) {
                currentScanCallback?.let { scn.stopScan(it) }
                isScanning = false
                currentScanCallback = null
            }
        } catch (_: SecurityException) {
            Log.e(TAG, "SecurityException during stop")
        }
    }

    private fun deserializeBSM(data: ByteArray, rssi: Int): V2XCommunicationEngine.BasicSafetyMessage? {
        return try {
            val parts = String(data).split(",")
            if (parts.size < 2) return null
            
            V2XCommunicationEngine.BasicSafetyMessage(
                vehicleId = "VEH-${parts.hashCode()}", 
                timestamp = System.currentTimeMillis(),
                latitude = parts[0].toDouble(),
                longitude = parts[1].toDouble(),
                speed = 10f, 
                heading = 0f,
                acceleration = 0f,
                vehicleLength = 4.5f,
                vehicleWidth = 1.8f,
                brakingStatus = false,
                hazardLights = false,
                rssi = rssi
            )
        } catch (_: Exception) {
            null
        }
    }
}

