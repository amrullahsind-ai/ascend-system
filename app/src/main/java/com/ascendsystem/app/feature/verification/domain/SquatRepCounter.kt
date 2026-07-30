package com.ascendsystem.app.feature.verification.domain

import kotlin.math.acos
import kotlin.math.sqrt

enum class SquatPhase { NOT_READY, STANDING, DESCENDING, BOTTOM, ASCENDING, COMPLETE }

data class SquatResult(
    val phase: SquatPhase = SquatPhase.NOT_READY,
    val repetitions: Int = 0,
    val target: Int,
    val kneeAngle: Float? = null,
    val confidence: Float = 0f,
    val feedback: String = "Posisikan seluruh tubuh di dalam panduan.",
    val completed: Boolean = false
)

class SquatRepCounter(
    private val target: Int,
    private val standingAngle: Float = 160f,
    private val bottomAngle: Float = 100f,
    private val minimumRepMillis: Long = 700L,
    private val maximumPoseGapMillis: Long = 700L
) {
    private var result = SquatResult(target = target.coerceAtLeast(1))
    private var repStartedAt: Long? = null
    private var lastValidPoseAt: Long? = null

    fun update(frame: PoseFrame): SquatResult {
        val measurement = frame.measureKnees()
        if (!frame.fullBodyVisible || measurement == null || measurement.second < .55f) {
            val lostTooLong = lastValidPoseAt?.let { frame.timestampMillis - it > maximumPoseGapMillis } ?: true
            if (lostTooLong) {
                repStartedAt = null
                result = result.copy(
                    phase = SquatPhase.NOT_READY,
                    kneeAngle = null,
                    confidence = measurement?.second ?: 0f,
                    feedback = "Seluruh tubuh dan kedua kaki harus terlihat."
                )
            }
            return result
        }

        lastValidPoseAt = frame.timestampMillis
        val angle = measurement.first
        val confidence = measurement.second
        val next = when (result.phase) {
            SquatPhase.NOT_READY -> if (angle >= standingAngle) {
                result.copy(phase = SquatPhase.STANDING, feedback = "Siap. Turunkan pinggul dengan terkontrol.")
            } else result.copy(feedback = "Mulai dari posisi berdiri tegak.")
            SquatPhase.STANDING -> if (angle < standingAngle - 12f) {
                repStartedAt = frame.timestampMillis
                result.copy(phase = SquatPhase.DESCENDING, feedback = "Turun sampai paha cukup rendah.")
            } else result
            SquatPhase.DESCENDING -> when {
                angle <= bottomAngle -> result.copy(phase = SquatPhase.BOTTOM, feedback = "Kedalaman valid. Berdiri kembali.")
                angle >= standingAngle -> {
                    repStartedAt = null
                    result.copy(phase = SquatPhase.STANDING, feedback = "Belum cukup rendah. Ulangi dengan terkontrol.")
                }
                else -> result
            }
            SquatPhase.BOTTOM -> if (angle > bottomAngle + 12f) {
                result.copy(phase = SquatPhase.ASCENDING, feedback = "Dorong tubuh kembali berdiri.")
            } else result
            SquatPhase.ASCENDING -> if (angle >= standingAngle) {
                val duration = frame.timestampMillis - (repStartedAt ?: frame.timestampMillis)
                repStartedAt = null
                if (duration < minimumRepMillis) {
                    result.copy(phase = SquatPhase.STANDING, feedback = "Gerakan terlalu cepat dan tidak dihitung.")
                } else {
                    val reps = result.repetitions + 1
                    result.copy(
                        phase = if (reps >= result.target) SquatPhase.COMPLETE else SquatPhase.STANDING,
                        repetitions = reps,
                        feedback = if (reps >= result.target) "Target squat selesai." else "Repetisi valid. Lanjutkan.",
                        completed = reps >= result.target
                    )
                }
            } else result
            SquatPhase.COMPLETE -> result
        }
        result = next.copy(kneeAngle = angle, confidence = confidence)
        return result
    }

    fun reset() {
        result = SquatResult(target = target.coerceAtLeast(1))
        repStartedAt = null
        lastValidPoseAt = null
    }
}

private fun PoseFrame.measureKnees(): Pair<Float, Float>? {
    val points = landmarks.associateBy { it.type }
    fun side(hip: PoseLandmarkType, knee: PoseLandmarkType, ankle: PoseLandmarkType): Pair<Float, Float>? {
        val h = points[hip] ?: return null
        val k = points[knee] ?: return null
        val a = points[ankle] ?: return null
        return angle(h, k, a) to minOf(h.confidence, k.confidence, a.confidence)
    }
    val measurements = listOfNotNull(
        side(PoseLandmarkType.LEFT_HIP, PoseLandmarkType.LEFT_KNEE, PoseLandmarkType.LEFT_ANKLE),
        side(PoseLandmarkType.RIGHT_HIP, PoseLandmarkType.RIGHT_KNEE, PoseLandmarkType.RIGHT_ANKLE)
    )
    if (measurements.size < 2) return null
    return measurements.map { it.first }.average().toFloat() to measurements.minOf { it.second }
}

private fun angle(first: PosePoint, vertex: PosePoint, third: PosePoint): Float {
    val ax = first.x - vertex.x
    val ay = first.y - vertex.y
    val bx = third.x - vertex.x
    val by = third.y - vertex.y
    val denominator = sqrt(ax * ax + ay * ay) * sqrt(bx * bx + by * by)
    if (denominator <= 0f) return 0f
    val cosine = ((ax * bx + ay * by) / denominator).coerceIn(-1f, 1f)
    return Math.toDegrees(acos(cosine).toDouble()).toFloat()
}
