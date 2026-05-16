package com.pmgaurav.safestrideai.utils

import android.graphics.*
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream

object ImageUtils {
    @androidx.camera.core.ExperimentalGetImage
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            when (imageProxy.format) {
                ImageFormat.YUV_420_888 -> yuvToBitmap(imageProxy)
                ImageFormat.JPEG        -> jpegToBitmap(imageProxy)
                else -> {

                    val buffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun yuvToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
        val jpegBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            bitmap = rotated
        }
        return bitmap
    }

    private fun jpegToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun scaleBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return source.scale(targetWidth, targetHeight, true)
    }
    fun fitAndPad(source: Bitmap, targetSize: Int): Bitmap {
        val width = source.width
        val height = source.height
        val scale = targetSize.toFloat() / maxOf(width, height)
        val scaledW = (width * scale).toInt()
        val scaledH = (height * scale).toInt()

        val scaled = source.scale(scaledW, scaledH, true)
        val padded = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        
        val left = (targetSize - scaledW) / 2f
        val top = (targetSize - scaledH) / 2f
        canvas.drawBitmap(scaled, left, top, null)
        
        if (scaled != source) scaled.recycle()
        return padded
    }
}

