package com.pmgaurav.safestrideai.gesture

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class GestureControlEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GestureDetector.SimpleOnGestureListener() {

    private val gestureDetector = GestureDetector(context, this)
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            Log.d("GestureControlEngine", "Scaling: ${detector.scaleFactor}")
            return true
        }
    })

    var onDoubleTapListener: (() -> Unit)? = null
    var onLongPressListener: (() -> Unit)? = null
    var onSwipeLeftListener: (() -> Unit)? = null
    var onSwipeRightListener: (() -> Unit)? = null
    var onSwipeUpListener: (() -> Unit)? = null
    var onSwipeDownListener: (() -> Unit)? = null

    fun handleTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        onDoubleTapListener?.invoke()
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        onLongPressListener?.invoke()
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null) return false
        val diffX = e2.x - e1.x
        val diffY = e2.y - e1.y
        
        if (abs(diffX) > abs(diffY)) {
            if (abs(diffX) > 100 && abs(velocityX) > 100) {
                if (diffX > 0) onSwipeRightListener?.invoke()
                else onSwipeLeftListener?.invoke()
                return true
            }
        } else {
            if (abs(diffY) > 100 && abs(velocityY) > 100) {
                if (diffY > 0) onSwipeDownListener?.invoke()
                else onSwipeUpListener?.invoke()
                return true
            }
        }
        return false
    }
}

