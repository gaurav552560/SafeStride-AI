package com.pmgaurav.safestrideai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.pmgaurav.safestrideai.detection.TrackedObject
import com.pmgaurav.safestrideai.detection.RiskTier
import com.pmgaurav.safestrideai.detection.DistanceEstimator

@Composable
fun BoundingBoxOverlay(
    trackedObjects: List<TrackedObject>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier,
) {
    if (trackedObjects.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val camW = frameWidth.toFloat()
        val camH = frameHeight.toFloat()
        if (camW <= 0 || camH <= 0) return@Canvas
        
        val camAspect = camW / camH
        val canvasW = size.width
        val canvasH = size.height
        val canvasAspect = canvasW / canvasH

        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (canvasAspect > camAspect) {

            scale = canvasH / camH
            offsetX = (canvasW - camW * scale) / 2f
            offsetY = 0f
        } else {

            scale = canvasW / camW
            offsetX = 0f
            offsetY = (canvasH - camH * scale) / 2f
        }

        for (obj in trackedObjects) {
            val box = obj.box
            val left   = offsetX + (box.left   * camW * scale)
            val top    = offsetY + (box.top    * camH * scale)
            val right  = offsetX + (box.right  * camW * scale)
            val bottom = offsetY + (box.bottom * camH * scale)

            val boxW   = right - left
            val boxH   = bottom - top

            if (boxW < 4f || boxH < 4f) continue

            val color = when (obj.riskTier) {
                RiskTier.DANGER  -> Color(0xFFEF233C)
                RiskTier.CAUTION -> Color(0xFFFFD166)
                RiskTier.ADVISORY -> Color(0xFFFFD166)
                RiskTier.SAFE    -> Color(0xFF06D6A0)
            }
            val strokeW = when (obj.riskTier) {
                RiskTier.DANGER -> 5.dp.toPx()
                else            -> 3.dp.toPx()
            }

            drawRect(
                color     = color,
                topLeft   = Offset(left, top),
                size      = Size(boxW, boxH),
                style     = Stroke(width = strokeW)
            )

            val cornerLen = minOf(boxW, boxH) * 0.15f
            drawLine(color, Offset(left, top), Offset(left + cornerLen, top), strokeW * 2)
            drawLine(color, Offset(left, top), Offset(left, top + cornerLen), strokeW * 2)
            drawLine(color, Offset(right, top), Offset(right - cornerLen, top), strokeW * 2)
            drawLine(color, Offset(right, top), Offset(right, top + cornerLen), strokeW * 2)
            drawLine(color, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW * 2)
            drawLine(color, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW * 2)
            drawLine(color, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeW * 2)
            drawLine(color, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeW * 2)

            drawIntoCanvas { canvas ->
                val labelText = buildString {
                    append(obj.label.uppercase())
                    append(" ")
                    append("${"%.0f".format(obj.confidence * 100)}%")
                }

                val ttcText = if (obj.ttcSeconds < Float.MAX_VALUE && obj.ttcSeconds < 10.0f) {
                    "âš¡${"%.1f".format(obj.ttcSeconds)}s"
                } else null

                val textPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize   = 12.dp.toPx()
                    isFakeBoldText = true
                    isAntiAlias = true

                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                val textWidth  = textPaint.measureText(labelText) + 16.dp.toPx()
                val labelHeight = 22.dp.toPx()

                val labelTop = if (top > 30.dp.toPx()) {
                    top - labelHeight - 2.dp.toPx()
                } else {
                    top + 2.dp.toPx()
                }
                val labelLeft  = left.coerceIn(0f, (size.width - textWidth).coerceAtLeast(0f))
                val labelRight = (labelLeft + textWidth).coerceAtMost(size.width)

                val bgPaint = android.graphics.Paint().apply {
                    this.color = color.copy(alpha = 0.90f).toArgb()
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.nativeCanvas.drawRoundRect(
                    labelLeft, labelTop, labelRight, labelTop + labelHeight,
                    6.dp.toPx(), 6.dp.toPx(), bgPaint
                )

                canvas.nativeCanvas.drawText(
                    labelText,
                    labelLeft + 6.dp.toPx(),
                    labelTop + labelHeight - 5.dp.toPx(),
                    textPaint
                )

                if (obj.speedKmph > 1.0f) {
                    val speedText = "${"%.0f".format(obj.speedKmph)}km/h"
                    val speedPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = 11.dp.toPx()
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val speedW = speedPaint.measureText(speedText) + 12.dp.toPx()
                    val speedLeft = (right - speedW).coerceAtLeast(left)
                    val speedTop = labelTop
                    
                    val speedBgPaint = android.graphics.Paint().apply {
                        this.color = "#CC000000".toColorInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    canvas.nativeCanvas.drawRoundRect(
                        speedLeft, speedTop, right, speedTop + labelHeight,
                        4.dp.toPx(), 4.dp.toPx(), speedBgPaint
                    )
                    canvas.nativeCanvas.drawText(
                        speedText,
                        speedLeft + 6.dp.toPx(),
                        speedTop + labelHeight - 5.dp.toPx(),
                        speedPaint
                    )
                }

                if (ttcText != null) {
                    val ttcPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = 11.dp.toPx()
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                    val ttcW = ttcPaint.measureText(ttcText) + 12.dp.toPx()
                    val ttcTop = bottom - 22.dp.toPx()
                    val ttcLeft = (right - ttcW).coerceAtLeast(left)

                    val ttcBgPaint = android.graphics.Paint().apply {
                        this.color = "#CC000000".toColorInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    canvas.nativeCanvas.drawRoundRect(
                        ttcLeft, ttcTop, right, bottom,
                        4.dp.toPx(), 4.dp.toPx(), ttcBgPaint
                    )
                    canvas.nativeCanvas.drawText(
                        ttcText,
                        ttcLeft + 4.dp.toPx(),
                        bottom - 5.dp.toPx(),
                        ttcPaint
                    )
                }

                if (obj.depthMeters < 50f) {
                    val distText = DistanceEstimator.formatDistance(obj.depthMeters)
                    val distPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = 11.dp.toPx()
                        isAntiAlias = true
                    }
                    val distBgPaint = android.graphics.Paint().apply {
                        this.color = "#AA0055AA".toColorInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val distW = distPaint.measureText(distText) + 12.dp.toPx()
                    canvas.nativeCanvas.drawRoundRect(
                        left, bottom - 22.dp.toPx(),
                        left + distW, bottom,
                        4.dp.toPx(), 4.dp.toPx(), distBgPaint
                    )
                    canvas.nativeCanvas.drawText(
                        distText,
                        left + 5.dp.toPx(),
                        bottom - 5.dp.toPx(),
                        distPaint
                    )
                }
            }
        }
    }
}

