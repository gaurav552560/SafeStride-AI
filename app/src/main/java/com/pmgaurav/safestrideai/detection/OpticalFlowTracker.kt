package com.pmgaurav.safestrideai.detection

import android.graphics.Bitmap
import com.pmgaurav.safestrideai.utils.AppConstants
import com.pmgaurav.safestrideai.utils.FrameUtils
import com.pmgaurav.safestrideai.utils.RectF
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class TrackedObject(
    val id: Int,
    val label: String,
    val confidence: Float,
    val box: android.graphics.RectF,
    val motionDx: Float,
    val motionDy: Float,
    val speedPxPerFrame: Float,
    val headingDeg: Float,
    val depthMeters: Float,
    val speedKmph: Float,
    val ttcSeconds: Float,
    val riskTier: RiskTier,
    val missedFrames: Int = 0,
    val velocityHistory: List<Pair<Float, Float>> = emptyList(),
)

enum class RiskTier(val priority: Int) { 
    SAFE(0), ADVISORY(1), CAUTION(2), DANGER(3)
}

@Singleton
class OpticalFlowTracker @Inject constructor(
    private val distanceEstimator: DistanceEstimator
) {

    private var prevGray: Mat? = null
    private val tracks = mutableMapOf<Int, TrackedObject>()
    private var nextId = 0
    private var lastUpdateTime = 0L

    companion object {
        private const val MIN_FEATURES_FOR_FLOW = 3
        private const val MAX_MISSED_FRAMES = AppConstants.MAX_MISSING_FRAMES
        private const val IOU_MATCH_THRESHOLD = AppConstants.TRACKER_IOU_THRESHOLD

        private const val FPS = 15f
        private const val COLLISION_RADIUS_M= 1.0f
        private const val PREDICT_FRAMES    = 300
        private const val TTC_DANGER_SEC    = AppConstants.TTC_DANGER_THRESHOLD_S
        private const val TTC_CAUTION_SEC   = AppConstants.TTC_WARNING_THRESHOLD_S
        private const val TTC_MAX_SEC       = AppConstants.TTC_ADVISORY_THRESHOLD_S
        private const val FOCAL_LENGTH_PX   = AppConstants.FOCAL_LENGTH_PX
        private const val IMAGE_HEIGHT_PX   = 480f
        const val IMAGE_WIDTH_PX  = DistanceEstimator.IMAGE_WIDTH_PX
        private const val MIN_SPEED_FOR_TTC = 0.8f
        private const val MOTION_NOISE_THRESHOLD = 0.5f 
        private const val MAX_FEATURES_HIGH_FPS = 15
        private const val MAX_FEATURES_LOW_FPS  = 8
    }
    private var adaptiveMaxFeatures = MAX_FEATURES_HIGH_FPS

    fun setAdaptiveQuality(fps: Float) {
        adaptiveMaxFeatures = if (fps >= 12f) MAX_FEATURES_HIGH_FPS
        else MAX_FEATURES_LOW_FPS
    }

    fun update(frame: Bitmap, rawDetections: List<DetectionResult>): List<TrackedObject> {
        val now = System.currentTimeMillis()
        val deltaTimeSec = if (lastUpdateTime == 0L) 1f / FPS else (now - lastUpdateTime) / 1000f
        lastUpdateTime = now

        val currentGray = Mat()
        val rgbaMat = Mat()
        Utils.bitmapToMat(frame, rgbaMat)
        Imgproc.cvtColor(rgbaMat, currentGray, Imgproc.COLOR_RGBA2GRAY)
        rgbaMat.release()

        val preliminaryObjects = mutableListOf<TrackedObject>()
        val unmatchedDetections = rawDetections.toMutableList()

        for ((id, existing) in tracks) {
            val best = unmatchedDetections.maxByOrNull {
                iou(existing.box, it.boundingBox)
            }
            if ((best != null) && (iou(existing.box, best.boundingBox) >= IOU_MATCH_THRESHOLD)) {
                unmatchedDetections.remove(best)

                val (dx, dy) = if (prevGray != null) {
                    val (rawDx, rawDy) = computeOpticalFlow(prevGray!!, currentGray, existing.box)
                    if (abs(rawDx) < MOTION_NOISE_THRESHOLD && abs(rawDy) < MOTION_NOISE_THRESHOLD) Pair(0f, 0f) 
                    else Pair(rawDx, rawDy)
                } else Pair(0f, 0f)

                val speed = sqrt(dx * dx + dy * dy)
                val heading = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                val depth = distanceEstimator.estimate(best.boundingBox, best.label)
                val metersPerPixel = depth / FOCAL_LENGTH_PX
                val instantSpeedMps = (speed * metersPerPixel) / deltaTimeSec
                val smoothedSpeedKmph = ((instantSpeedMps * 3.6f) * 0.3f) + (existing.speedKmph * 0.7f)
                
                val history = (existing.velocityHistory + Pair(dx, dy)).takeLast(5)

                val updated = TrackedObject(
                    id = id,
                    label = best.label,
                    confidence = best.confidence,
                    box = best.boundingBox,
                    motionDx = dx,
                    motionDy = dy,
                    speedPxPerFrame = speed,
                    headingDeg = heading,
                    depthMeters = depth,
                    speedKmph = if (speed < 0.1f) 0f else smoothedSpeedKmph,
                    ttcSeconds = Float.MAX_VALUE,
                    riskTier = RiskTier.SAFE,
                    missedFrames = 0,
                    velocityHistory = history
                )
                preliminaryObjects.add(updated)
            } else {
                val missed = existing.copy(missedFrames = existing.missedFrames + 1)
                if (missed.missedFrames <= MAX_MISSED_FRAMES) {
                    preliminaryObjects.add(missed)
                }
            }
        }
        for (det in unmatchedDetections) {
            val depth = distanceEstimator.estimate(det.boundingBox, det.label)
            val newTrack = TrackedObject(
                id = nextId++,
                label = det.label,
                confidence = det.confidence,
                box = det.boundingBox,
                motionDx = 0f, motionDy = 0f,
                speedPxPerFrame = 0f, headingDeg = 0f,
                depthMeters = depth, 
                speedKmph = 0f,
                ttcSeconds = Float.MAX_VALUE,
                riskTier = RiskTier.SAFE,
                velocityHistory = emptyList()
            )
            preliminaryObjects.add(newTrack)
        }
        val pedestrians = preliminaryObjects.filter { it.label.lowercase() == "person" }
        val vehicles = preliminaryObjects.filter {
            it.label.lowercase() in setOf("car", "motorcycle", "bus", "truck", "bicycle", "train")
        }

        val results = preliminaryObjects.map { obj ->
            val ttc = computeTTC(obj, pedestrians, vehicles, deltaTimeSec)
            obj.copy(ttcSeconds = ttc, riskTier = classifyRisk(ttc))
        }

        tracks.clear()
        results.forEach { tracks[it.id] = it }

        prevGray?.release()
        prevGray = currentGray

        return results
    }

    private fun computeOpticalFlow(prevGray: Mat, currGray: Mat, box: android.graphics.RectF): Pair<Float, Float> {
        val x1 = (box.left   * IMAGE_WIDTH_PX).toInt().coerceIn(0, IMAGE_WIDTH_PX.toInt() - 1)
        val y1 = (box.top    * IMAGE_HEIGHT_PX).toInt().coerceIn(0, IMAGE_HEIGHT_PX.toInt() - 1)
        val x2 = (box.right  * IMAGE_WIDTH_PX).toInt().coerceIn(0, IMAGE_WIDTH_PX.toInt() - 1)
        val y2 = (box.bottom * IMAGE_HEIGHT_PX).toInt().coerceIn(0, IMAGE_HEIGHT_PX.toInt() - 1)
        if (x2 <= x1 || y2 <= y1) return Pair(0f, 0f)

        val roi = prevGray.submat(y1, y2, x1, x2)
        val prevPts = MatOfPoint2f()

        val corners = MatOfPoint()
        Imgproc.goodFeaturesToTrack(
            roi, corners, adaptiveMaxFeatures,
            0.3, 3.0, Mat(), 3, false, 0.04
        )

        if (corners.rows() < MIN_FEATURES_FOR_FLOW) {
            roi.release(); corners.release()
            return Pair(0f, 0f)
        }

        val fullPts = corners.toArray().map { pt ->
            Point(pt.x + x1, pt.y + y1)
        }
        prevPts.fromList(fullPts)

        val currPts  = MatOfPoint2f()
        val status   = MatOfByte()
        val err      = MatOfFloat()

        Video.calcOpticalFlowPyrLK(
            prevGray, currGray, prevPts, currPts, status, err,
            Size(21.0, 21.0), 3,
            TermCriteria(TermCriteria.EPS + TermCriteria.COUNT, 30, 0.01)
        )

        val prevArr   = prevPts.toArray()
        val currArr   = currPts.toArray()
        val statusArr = status.toArray()

        var sumDx = 0f; var sumDy = 0f; var count = 0
        for (i in statusArr.indices) {
            if (statusArr[i].toInt() == 1) {
                sumDx += (currArr[i].x - prevArr[i].x).toFloat()
                sumDy += (currArr[i].y - prevArr[i].y).toFloat()
                count++
            }
        }

        roi.release(); corners.release()
        prevPts.release(); currPts.release()
        status.release(); err.release()

        return if (count > 0) Pair(sumDx / count, sumDy / count) else Pair(0f, 0f)
    }

    private fun computeTTC(
        obj: TrackedObject,
        pedestrians: List<TrackedObject>,
        vehicles: List<TrackedObject>,
        deltaTimeSec: Float
    ): Float {
        val label = obj.label.lowercase()
        val isVehicle = label in setOf("car","motorcycle","bus","truck","bicycle","train")
        val isPerson = label == "person"

        var minTTC = Float.MAX_VALUE

        if (isPerson) {
            for (other in vehicles) {
                if (other.id == obj.id) continue
                val ttc = computeTTCBetween(obj, other, deltaTimeSec)
                if (ttc < minTTC) minTTC = ttc
            }
        } else if (isVehicle) {
            for (other in pedestrians) {
                if (other.id == obj.id) continue
                val ttc = computeTTCBetween(other, obj, deltaTimeSec)
                if (ttc < minTTC) minTTC = ttc
            }

            val metersPerPixel = obj.depthMeters / FOCAL_LENGTH_PX
            val vZ = (-obj.motionDy * metersPerPixel) / deltaTimeSec
            val closingSpeed = -vZ
            if (closingSpeed > 0.5f) {
                val ttcToCamera = (obj.depthMeters / closingSpeed).coerceIn(0.1f, TTC_MAX_SEC)
                if (ttcToCamera < minTTC) minTTC = ttcToCamera
            }
        }

        return minTTC
    }
    
    private fun computeTTCBetween(
        ped: TrackedObject,
        veh: TrackedObject,
        deltaTimeSec: Float
    ): Float {
  
        val pedCxNorm = (ped.box.left + ped.box.right) / 2f
        val vehCxNorm = (veh.box.left + veh.box.right) / 2f

        val metersPerPixelPed = ped.depthMeters / FOCAL_LENGTH_PX
        val metersPerPixelVeh = veh.depthMeters / FOCAL_LENGTH_PX

        val pedSpeed = ped.speedPxPerFrame
        val vehSpeed = veh.speedPxPerFrame

        if (pedSpeed < MIN_SPEED_FOR_TTC && vehSpeed < MIN_SPEED_FOR_TTC) {
            return Float.MAX_VALUE
        }

        val pedX = (pedCxNorm - 0.5f) * IMAGE_WIDTH_PX * metersPerPixelPed
        val pedZ = ped.depthMeters

        val vehX = (vehCxNorm - 0.5f) * IMAGE_WIDTH_PX * metersPerPixelVeh
        val vehZ = veh.depthMeters

        val pedVx = (ped.motionDx * metersPerPixelPed) / deltaTimeSec
        val pedVz = (-ped.motionDy * metersPerPixelPed) / deltaTimeSec

        val vehVx = (veh.motionDx * metersPerPixelVeh) / deltaTimeSec
        val vehVz = (-veh.motionDy * metersPerPixelVeh) / deltaTimeSec

        var minDistSq = Float.MAX_VALUE
        var ttcSeconds = Float.MAX_VALUE
        val collisionRadiusSq = COLLISION_RADIUS_M * COLLISION_RADIUS_M

        for (k in 1..100) {
            val t = k * 0.05f 
            val pedFx = pedX + t * pedVx
            val pedFz = pedZ + t * pedVz
            val vehFx = vehX + t * vehVx
            val vehFz = vehZ + t * vehVz

            val distSq = (pedFx - vehFx) * (pedFx - vehFx) +
                        (pedFz - vehFz) * (pedFz - vehFz)

            if (distSq < minDistSq) {
                minDistSq = distSq
            }

            if (distSq < collisionRadiusSq) {
                ttcSeconds = t
                break
            }
        }

        return if (ttcSeconds < Float.MAX_VALUE) {
            ttcSeconds.coerceIn(0.1f, TTC_MAX_SEC)
        } else {
            val closingSpeedMps = -vehVz 
            if (closingSpeedMps > 0.5f) {
                (veh.depthMeters / closingSpeedMps).coerceIn(0.1f, TTC_MAX_SEC)
            } else {
                Float.MAX_VALUE  
            }
        }
    }

    private fun classifyRisk(ttc: Float) = when {
        ttc < TTC_DANGER_SEC  -> RiskTier.DANGER
        ttc < TTC_CAUTION_SEC -> RiskTier.CAUTION
        ttc < TTC_MAX_SEC -> RiskTier.ADVISORY 
        else -> RiskTier.SAFE
    }

    private fun iou(a: android.graphics.RectF, b: android.graphics.RectF): Float {
        val rectA = RectF(a.left, a.top, a.right, a.bottom)
        val rectB = RectF(b.left, b.top, b.right, b.bottom)
        return FrameUtils.iou(rectA, rectB)
    }

    fun release() {
        prevGray?.release()
        prevGray = null
        tracks.clear()
    }
}

