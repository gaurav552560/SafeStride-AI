package com.pmgaurav.safestrideai.detection

import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import com.pmgaurav.safestrideai.utils.FrameUtils
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ImageProcessor"

@Singleton
class ImageProcessor @Inject constructor() {

    @androidx.camera.core.ExperimentalGetImage
    fun toBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            when (imageProxy.format) {
                PixelFormat.RGBA_8888 -> {

                    val buffer = imageProxy.planes[0].buffer
                    val bitmap = createBitmap(
                        imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    applyRotation(bitmap, imageProxy.imageInfo.rotationDegrees)
                }
                ImageFormat.YUV_420_888 -> FrameUtils.imageProxyToBitmap(imageProxy)
                ImageFormat.JPEG        -> FrameUtils.imageProxyToBitmap(imageProxy)
                else -> FrameUtils.imageProxyToBitmap(imageProxy)
            }
        } catch (e: Exception) {
            Log.e(TAG, "toBitmap failed: ${e.message}")
            null
        } finally {
            imageProxy.close()
        }
    }

    private fun applyRotation(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}

