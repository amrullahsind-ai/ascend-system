package com.ascendsystem.app.feature.verification.domain

enum class CalibrationStatus {
    NOT_STARTED, SEARCHING_FOR_BODY, BODY_INCOMPLETE, HOLD_STILL, CALIBRATING, READY, FAILED
}
data class CalibrationInput(
    val pose: PoseFrame?,
    val cameraStable: Boolean,
    val bodyInsideGuide: Boolean,
    val nowMillis: Long
)
data class CalibrationState(
    val status: CalibrationStatus = CalibrationStatus.NOT_STARTED,
    val validSinceMillis: Long? = null,
    val progress: Float = 0f,
    val message: String = "CALIBRATION REQUIRED"
)

class BodyVisibilityEvaluator(
    private val minimumConfidence: Float = .55f,
    private val edgeMarginRatio: Float = .03f
) {
    private val required = setOf(
        PoseLandmarkType.NOSE, PoseLandmarkType.LEFT_SHOULDER, PoseLandmarkType.RIGHT_SHOULDER,
        PoseLandmarkType.LEFT_HIP, PoseLandmarkType.RIGHT_HIP, PoseLandmarkType.LEFT_KNEE,
        PoseLandmarkType.RIGHT_KNEE, PoseLandmarkType.LEFT_ANKLE, PoseLandmarkType.RIGHT_ANKLE
    )
    fun isFullBodyVisible(points: List<PosePoint>, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val byType = points.associateBy { it.type }
        return required.all { type ->
            val point = byType[type] ?: return@all false
            point.confidence >= minimumConfidence &&
                point.x in width * edgeMarginRatio..width * (1f - edgeMarginRatio) &&
                point.y in height * edgeMarginRatio..height * (1f - edgeMarginRatio)
        }
    }
}

class PoseConfidenceEvaluator(private val acceptable: Float = .6f) {
    fun acceptable(frame: PoseFrame?) = frame != null && frame.landmarks.isNotEmpty() && frame.overallConfidence >= acceptable
}

class CalibrationEngine(
    private val requiredHoldMillis: Long = 2_000L,
    private val confidenceEvaluator: PoseConfidenceEvaluator = PoseConfidenceEvaluator()
) {
    fun reduce(previous: CalibrationState, input: CalibrationInput): CalibrationState {
        val pose = input.pose ?: return CalibrationState(CalibrationStatus.SEARCHING_FOR_BODY, message = "FULL BODY NOT DETECTED")
        if (!confidenceEvaluator.acceptable(pose)) return CalibrationState(CalibrationStatus.SEARCHING_FOR_BODY, message = "POSE CONFIDENCE LOW")
        if (!pose.fullBodyVisible || !input.bodyInsideGuide) return CalibrationState(CalibrationStatus.BODY_INCOMPLETE, message = "FULL BODY NOT DETECTED")
        if (!input.cameraStable) return CalibrationState(CalibrationStatus.HOLD_STILL, message = "HOLD POSITION")
        val since = previous.validSinceMillis ?: input.nowMillis
        val elapsed = (input.nowMillis - since).coerceAtLeast(0)
        val progress = (elapsed.toFloat() / requiredHoldMillis).coerceIn(0f, 1f)
        return if (progress >= 1f) {
            CalibrationState(CalibrationStatus.READY, since, 1f, "VERIFICATION ENGINE READY")
        } else {
            CalibrationState(CalibrationStatus.CALIBRATING, since, progress, "HOLD POSITION")
        }
    }
}
