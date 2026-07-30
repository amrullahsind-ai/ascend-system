package com.ascendsystem.app.feature.verification.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.ExperimentalGetImage
import com.ascendsystem.app.feature.verification.domain.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean

data class PoseAnalysis(
    val frame: PoseFrame?,
    val luminance: Double,
    val processingMillis: Long,
    val error: String? = null
)

class MlKitPoseAnalyzer(
    private val smoother: LandmarkSmoother = ExponentialLandmarkSmoother(),
    private val visibility: BodyVisibilityEvaluator = BodyVisibilityEvaluator(),
    private val onResult: (PoseAnalysis) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val processing = AtomicBoolean(false)
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false); imageProxy.close(); return
        }
        val started = System.currentTimeMillis()
        val luminance = imageProxy.averageLuminance()
        val rotation = imageProxy.imageInfo.rotationDegrees
        detector.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { pose ->
                val points = mapping.mapNotNull { (mlType, domainType) ->
                    pose.getPoseLandmark(mlType)?.let {
                        PosePoint(domainType, it.position.x, it.position.y, it.position3D.z, it.inFrameLikelihood)
                    }
                }
                if (points.isEmpty()) {
                    smoother.reset()
                    onResult(PoseAnalysis(null, luminance, System.currentTimeMillis() - started))
                } else {
                    val confidence = points.map { it.confidence }.average().toFloat()
                    val raw = PoseFrame(
                        System.currentTimeMillis(), points, confidence,
                        visibility.isFullBodyVisible(points, imageProxy.width, imageProxy.height),
                        orientation(points), imageProxy.width, imageProxy.height, rotation
                    )
                    onResult(PoseAnalysis(smoother.smooth(raw), luminance, System.currentTimeMillis() - started))
                }
            }
            .addOnFailureListener { onResult(PoseAnalysis(null, luminance, System.currentTimeMillis() - started, it.message ?: "Pose analysis failed")) }
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    override fun close() { detector.close(); smoother.reset() }

    private fun ImageProxy.averageLuminance(): Double {
        val buffer = planes.firstOrNull()?.buffer ?: return 0.0
        val step = (buffer.remaining() / 256).coerceAtLeast(1)
        var sum = 0L; var count = 0; var index = buffer.position()
        while (index < buffer.limit()) { sum += buffer.get(index).toInt() and 0xFF; count++; index += step }
        return if (count == 0) 0.0 else sum.toDouble() / count
    }

    companion object {
        private val mapping = mapOf(
            PoseLandmark.NOSE to PoseLandmarkType.NOSE,
            PoseLandmark.LEFT_EYE_INNER to PoseLandmarkType.LEFT_EYE_INNER,
            PoseLandmark.LEFT_EYE to PoseLandmarkType.LEFT_EYE, PoseLandmark.RIGHT_EYE to PoseLandmarkType.RIGHT_EYE,
            PoseLandmark.LEFT_EYE_OUTER to PoseLandmarkType.LEFT_EYE_OUTER,
            PoseLandmark.RIGHT_EYE_INNER to PoseLandmarkType.RIGHT_EYE_INNER,
            PoseLandmark.RIGHT_EYE_OUTER to PoseLandmarkType.RIGHT_EYE_OUTER,
            PoseLandmark.LEFT_EAR to PoseLandmarkType.LEFT_EAR, PoseLandmark.RIGHT_EAR to PoseLandmarkType.RIGHT_EAR,
            PoseLandmark.LEFT_MOUTH to PoseLandmarkType.LEFT_MOUTH, PoseLandmark.RIGHT_MOUTH to PoseLandmarkType.RIGHT_MOUTH,
            PoseLandmark.LEFT_SHOULDER to PoseLandmarkType.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER to PoseLandmarkType.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW to PoseLandmarkType.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW to PoseLandmarkType.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST to PoseLandmarkType.LEFT_WRIST, PoseLandmark.RIGHT_WRIST to PoseLandmarkType.RIGHT_WRIST,
            PoseLandmark.LEFT_PINKY to PoseLandmarkType.LEFT_PINKY, PoseLandmark.RIGHT_PINKY to PoseLandmarkType.RIGHT_PINKY,
            PoseLandmark.LEFT_INDEX to PoseLandmarkType.LEFT_INDEX, PoseLandmark.RIGHT_INDEX to PoseLandmarkType.RIGHT_INDEX,
            PoseLandmark.LEFT_THUMB to PoseLandmarkType.LEFT_THUMB, PoseLandmark.RIGHT_THUMB to PoseLandmarkType.RIGHT_THUMB,
            PoseLandmark.LEFT_HIP to PoseLandmarkType.LEFT_HIP, PoseLandmark.RIGHT_HIP to PoseLandmarkType.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE to PoseLandmarkType.LEFT_KNEE, PoseLandmark.RIGHT_KNEE to PoseLandmarkType.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE to PoseLandmarkType.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE to PoseLandmarkType.RIGHT_ANKLE,
            PoseLandmark.LEFT_HEEL to PoseLandmarkType.LEFT_HEEL, PoseLandmark.RIGHT_HEEL to PoseLandmarkType.RIGHT_HEEL,
            PoseLandmark.LEFT_FOOT_INDEX to PoseLandmarkType.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX to PoseLandmarkType.RIGHT_FOOT_INDEX
        )
        private fun orientation(points: List<PosePoint>): BodyOrientation {
            val map = points.associateBy { it.type }
            val left = map[PoseLandmarkType.LEFT_SHOULDER] ?: return BodyOrientation.UNKNOWN
            val right = map[PoseLandmarkType.RIGHT_SHOULDER] ?: return BodyOrientation.UNKNOWN
            val shoulderWidth = kotlin.math.abs(left.x - right.x)
            val torsoHeight = map[PoseLandmarkType.LEFT_HIP]?.let { kotlin.math.abs(left.y - it.y) } ?: return BodyOrientation.UNKNOWN
            if (torsoHeight == 0f || shoulderWidth / torsoHeight > .45f) return BodyOrientation.FRONT
            return if (left.confidence >= right.confidence) BodyOrientation.LEFT_SIDE else BodyOrientation.RIGHT_SIDE
        }
    }
}
