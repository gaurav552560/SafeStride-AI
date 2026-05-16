package com.pmgaurav.safestrideai.detection

import android.graphics.RectF

data class RawDetection(
    val boundingBox: RectF,
    val classIndex:  Int,
    val className:   String,
    val confidence:  Float,
)

data class DetectionResult(
    val label:       String,
    val confidence:  Float,
    val boundingBox: RectF
)

@Suppress("unused")
enum class BoxFormat   { Y1X1Y2X2, X1Y1X2Y2 }

@Suppress("unused")
enum class NormalizeMode { MINUS1_TO_1, ZERO_TO_1, RAW_UINT8 }

@Suppress("unused")
data class ModelConfig(
    val inputSize:      Int          = 320,
    val maxDetections:  Int          = 25,
    val isQuantized:    Boolean      = false,
    val boxOutputIdx:   Int          = 0,
    val classOutputIdx: Int          = 1,
    val scoreOutputIdx: Int          = 2,
    val countOutputIdx: Int          = 3,
    val boxFormat:      BoxFormat    = BoxFormat.Y1X1Y2X2,
    val normalizeInput: NormalizeMode = NormalizeMode.MINUS1_TO_1
)

