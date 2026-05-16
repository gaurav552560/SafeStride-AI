package com.pmgaurav.safestrideai.utils 
 
object AppConstants { 
    const val MODEL_FILE = "pedestrian_vehicle_detect.tflite" 
    const val MODEL_INPUT_WIDTH  = 300 
    const val MODEL_INPUT_HEIGHT = 300
    const val MAX_DETECTIONS = 25
    const val CONFIDENCE_THRESHOLD = 0.35f
    const val NMS_IOU_THRESHOLD = 0.40f
    const val TRACKER_IOU_THRESHOLD = 0.4f
    const val MAX_MISSING_FRAMES = 5 
    const val FOCAL_LENGTH_PX = 800f
    
    const val HEIGHT_PERSON = 1.70f
    const val HEIGHT_CAR = 1.50f
    const val HEIGHT_BUS = 3.20f
    const val HEIGHT_TRUCK = 3.50f
    const val HEIGHT_RICKSHAW = 1.70f
    const val HEIGHT_CYCLE = 1.10f
    const val HEIGHT_ANIMAL = 0.80f

    const val TTC_DANGER_THRESHOLD_S  = 2.0f
    const val TTC_WARNING_THRESHOLD_S = 4.0f 
    const val TTC_ADVISORY_THRESHOLD_S = 7.0f

    const val DISTANCE_DANGER_M = 3.0f
    const val DISTANCE_WARNING_M = 10.0f
    const val DISTANCE_ADVISORY_M = 20.0f

    const val FRAME_SKIP = 1
    const val LATENCY_BUDGET_MS = 66L
    const val VIBRATION_DURATION_MS = 400L 
}
