package com.ascendsystem.app.feature.verification.domain

import com.ascendsystem.app.core.domain.SafetyLevel
import com.ascendsystem.app.core.domain.VerificationType
import kotlinx.coroutines.flow.Flow

sealed interface VerificationTarget {
    data class Count(val value: Int) : VerificationTarget
    data class Duration(val seconds: Int) : VerificationTarget
    data class Distance(val meters: Int) : VerificationTarget
    data class BooleanTarget(val expected: Boolean = true) : VerificationTarget
}

enum class VerificationSessionStatus {
    CREATED, PREPARING, CALIBRATING, ACTIVE, PAUSED, VERIFYING,
    COMPLETED, FAILED, CANCELLED
}

data class VerificationRequest(
    val questId: String,
    val type: VerificationType,
    val target: VerificationTarget,
    val safetyLevel: SafetyLevel,
    val createdAtMillis: Long
)

data class VerificationSession(
    val id: String,
    val questId: String,
    val type: VerificationType,
    val target: VerificationTarget,
    val safetyLevel: SafetyLevel,
    val status: VerificationSessionStatus = VerificationSessionStatus.CREATED,
    val createdAtMillis: Long,
    val startedAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val progress: Float = 0f,
    val confidence: Float? = null,
    val failureReasons: List<String> = emptyList(),
    val metrics: Map<String, String> = emptyMap()
)

sealed interface VerificationResult {
    data class Success(val confidence: Float, val metrics: Map<String, String>) : VerificationResult
    data class Failure(val reasons: List<String>, val retryAllowed: Boolean) : VerificationResult
    data class Cancelled(val reason: String?) : VerificationResult
}

interface VerificationRepository {
    suspend fun create(session: VerificationSession)
    suspend fun get(id: String): VerificationSession?
    fun observe(id: String): Flow<VerificationSession?>
    suspend fun update(session: VerificationSession)
}

class VerificationSessionStateMachine {
    private val transitions = mapOf(
        VerificationSessionStatus.CREATED to setOf(VerificationSessionStatus.PREPARING, VerificationSessionStatus.CANCELLED),
        VerificationSessionStatus.PREPARING to setOf(VerificationSessionStatus.CALIBRATING, VerificationSessionStatus.FAILED, VerificationSessionStatus.CANCELLED),
        VerificationSessionStatus.CALIBRATING to setOf(VerificationSessionStatus.ACTIVE, VerificationSessionStatus.PAUSED, VerificationSessionStatus.FAILED, VerificationSessionStatus.CANCELLED),
        VerificationSessionStatus.ACTIVE to setOf(VerificationSessionStatus.CALIBRATING, VerificationSessionStatus.PAUSED, VerificationSessionStatus.VERIFYING, VerificationSessionStatus.FAILED, VerificationSessionStatus.CANCELLED),
        VerificationSessionStatus.PAUSED to setOf(VerificationSessionStatus.CALIBRATING, VerificationSessionStatus.ACTIVE, VerificationSessionStatus.CANCELLED),
        VerificationSessionStatus.VERIFYING to setOf(VerificationSessionStatus.COMPLETED, VerificationSessionStatus.FAILED, VerificationSessionStatus.CANCELLED),
        VerificationSessionStatus.FAILED to setOf(VerificationSessionStatus.PREPARING),
        VerificationSessionStatus.COMPLETED to emptySet(),
        VerificationSessionStatus.CANCELLED to emptySet()
    )
    fun canTransition(from: VerificationSessionStatus, to: VerificationSessionStatus) =
        transitions[from]?.contains(to) == true
    fun transition(session: VerificationSession, to: VerificationSessionStatus): VerificationSession {
        require(canTransition(session.status, to)) { "Invalid verification transition: ${session.status} -> $to" }
        return session.copy(status = to)
    }
}

enum class PoseLandmarkType {
    NOSE,
    LEFT_EYE_INNER, LEFT_EYE, LEFT_EYE_OUTER,
    RIGHT_EYE_INNER, RIGHT_EYE, RIGHT_EYE_OUTER,
    LEFT_EAR, RIGHT_EAR, LEFT_MOUTH, RIGHT_MOUTH,
    LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST, LEFT_PINKY, RIGHT_PINKY,
    LEFT_INDEX, RIGHT_INDEX, LEFT_THUMB, RIGHT_THUMB,
    LEFT_HIP, RIGHT_HIP, LEFT_KNEE,
    RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE, LEFT_HEEL, RIGHT_HEEL,
    LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX
}
enum class BodyOrientation { FRONT, LEFT_SIDE, RIGHT_SIDE, UNKNOWN }
data class PosePoint(
    val type: PoseLandmarkType,
    val x: Float,
    val y: Float,
    val z: Float?,
    val confidence: Float
)
data class PoseFrame(
    val timestampMillis: Long,
    val landmarks: List<PosePoint>,
    val overallConfidence: Float,
    val fullBodyVisible: Boolean,
    val orientation: BodyOrientation,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int
)

interface LandmarkSmoother {
    fun smooth(frame: PoseFrame): PoseFrame
    fun reset()
}

class ExponentialLandmarkSmoother(private val alpha: Float = .55f) : LandmarkSmoother {
    private var previous: Map<PoseLandmarkType, PosePoint> = emptyMap()
    override fun smooth(frame: PoseFrame): PoseFrame {
        val smoothed = frame.landmarks.map { point ->
            previous[point.type]?.let { old ->
                point.copy(
                    x = old.x + alpha * (point.x - old.x),
                    y = old.y + alpha * (point.y - old.y),
                    z = point.z?.let { z -> (old.z ?: z) + alpha * (z - (old.z ?: z)) }
                )
            } ?: point
        }
        previous = smoothed.associateBy { it.type }
        return frame.copy(landmarks = smoothed)
    }
    override fun reset() { previous = emptyMap() }
}
