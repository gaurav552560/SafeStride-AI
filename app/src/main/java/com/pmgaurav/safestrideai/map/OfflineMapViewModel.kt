package com.pmgaurav.safestrideai.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflineMapViewModel @Inject constructor(
    private val offlineTileManager: OfflineTileManager,
    private val tileDao: TileDao
) : ViewModel() {

    private val _tileCount = MutableStateFlow(0)
    val tileCount: StateFlow<Int> = _tileCount.asStateFlow()

    val downloadProgress = offlineTileManager.downloadProgress

    init {
        updateTileCount()
    }

    fun updateTileCount() {
        viewModelScope.launch {
            _tileCount.value = tileDao.getTileCount()
        }
    }

    fun downloadArea(lat: Double, lng: Double) {
        viewModelScope.launch {

            offlineTileManager.prefetchArea(lat, lng, 1.0, listOf(15, 16, 17))
            updateTileCount()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            offlineTileManager.clearCache()
            updateTileCount()
        }
    }
}

