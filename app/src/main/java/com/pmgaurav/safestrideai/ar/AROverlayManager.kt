package com.pmgaurav.safestrideai.ar

import com.google.ar.core.Config
import com.google.ar.core.Session
import com.pmgaurav.safestrideai.detection.TrackedObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.tan

@Singleton
class AROverlayManager @Inject constructor() {
    private var isSessionResumed = false

    fun onSessionResumed() { isSessionResumed = true }
    fun onSessionPaused() { isSessionResumed = false }

    fun configureSession(@Suppress("UNUSED_PARAMETER") session: Session, config: Config) {
        config.apply {
            lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            focusMode = Config.FocusMode.FIXED
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            planeFindingMode = Config.PlaneFindingMode.DISABLED 
        }
    }

    private fun get3DPosition(det: TrackedObject): io.github.sceneview.math.Position {
        val centerX = det.box.centerX()
        val normalizedX = (centerX - 0.5f) * 2f
        val x3D = (det.depthMeters * tan(normalizedX * Math.toRadians(30.0))).toFloat()
        return io.github.sceneview.math.Position(x = x3D, y = -1.2f, z = -det.depthMeters)
    }

    private val activeAnchors = mutableMapOf<Int, com.google.ar.core.Anchor>()

    fun updateOverlay(frame: com.google.ar.core.Frame, detections: List<TrackedObject>, session: Session) {
        if (!isSessionResumed) return

        try {
            val detectedIds = detections.map { it.id }.toSet()
            activeAnchors.keys.filter { it !in detectedIds }.forEach { id ->
                try {
                    activeAnchors[id]?.detach()
                } catch (_: Exception) {}
                activeAnchors.remove(id)
            }

            detections.forEach { det ->
                if (det.depthMeters < 15f && (det.ttcSeconds < 4f || det.confidence > 0.7f)) {
                    if (activeAnchors[det.id] == null) {
                        try {
                            val pos = get3DPosition(det)
                            val worldPose = frame.camera.pose.compose(
                                com.google.ar.core.Pose.makeTranslation(pos.x, pos.y, pos.z)
                            )
                            activeAnchors[det.id] = session.createAnchor(worldPose)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AROverlayManager", "Error updating overlay: ${e.message}")
        }
    }

    fun getAnchorForDetection(id: Int): com.google.ar.core.Anchor? = activeAnchors[id]
}

