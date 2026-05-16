package com.pmgaurav.safestrideai.utils 
 
import android.graphics.Bitmap 
import androidx.camera.core.ImageProxy 
 
object FrameUtils { 
 
    @androidx.camera.core.ExperimentalGetImage
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? { 
        return ImageUtils.imageProxyToBitmap(imageProxy)
    } 
 
    @Suppress("Unused")
    fun scaleBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap = 
        ImageUtils.scaleBitmap(source, targetWidth, targetHeight)
 
    @Suppress("Unused")
    fun denormalize(normBox: FloatArray, frameW: Int, frameH: Int): RectF {

        val rect = RectF(
            left = normBox[1],
            top = normBox[0],
            right = normBox[3],
            bottom = normBox[2],
        )
        return denormalize(rect, frameW, frameH)
    }

    @Suppress("Unused")
    fun denormalize(normBox: RectF, frameW: Int, frameH: Int): RectF {
        return RectF(
            left = normBox.left * frameW,
            top = normBox.top * frameH,
            right = normBox.right * frameW,
            bottom = normBox.bottom * frameH
        )
    }

    fun iou(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        
        if ((interRight <= interLeft) || (interBottom <= interTop)) return 0f
        
        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val aArea = a.width * a.height
        val bArea = b.width * b.height
        val unionArea = aArea + bArea - interArea

        return if (unionArea <= 0f) 0f else interArea / unionArea
    }
}

