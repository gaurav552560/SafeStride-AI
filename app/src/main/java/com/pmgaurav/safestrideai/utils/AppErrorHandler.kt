package com.pmgaurav.safestrideai.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AppError {
    object CameraError : AppError()
    object ModelLoadError : AppError()
    object LocationError : AppError()
    object RootedDeviceError : AppError()
    data class UnknownError(val message: String) : AppError()
}

class AppErrorHandler {
    private val _currentError = MutableStateFlow<AppError?>(null)
    val currentError: StateFlow<AppError?> = _currentError.asStateFlow()

    fun setError(error: AppError) {
        _currentError.value = error
    }

    fun clearError() {
        _currentError.value = null
    }
}

