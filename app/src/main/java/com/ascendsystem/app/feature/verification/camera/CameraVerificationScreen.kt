package com.ascendsystem.app.feature.verification.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascendsystem.app.BuildConfig
import com.ascendsystem.app.core.designsystem.*
import com.ascendsystem.app.feature.verification.domain.*
import java.util.concurrent.Executors
import kotlin.math.max

@Composable
fun CameraVerificationScreen(
    onClose: () -> Unit,
    debugMode: Boolean = false,
    viewModel: CameraVerificationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var requestedOnce by rememberSaveable { mutableStateOf(false) }
    val closeSession = { viewModel.cancelAndClose(onClose) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.permissionGranted()
        else {
            val activity = context.findActivity()
            val permanent = requestedOnce && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            viewModel.permissionDenied(permanent)
        }
        requestedOnce = true
    }

    LaunchedEffect(Unit) {
        viewModel.setReducedMotion(
            runCatching {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
        )
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            viewModel.permissionGranted()
        } else viewModel.permissionRequired()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.onForegrounded()
                Lifecycle.Event.ON_RESUME -> {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.permissionGranted()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AscendBackground {
        Column(Modifier.fillMaxSize().padding(AscendSpacing.md), verticalArrangement = Arrangement.spacedBy(AscendSpacing.sm)) {
            SystemHeader("Verification engine", "Squat verification", stageLabel(state))
            when (state.permission) {
                CameraPermissionState.GRANTED -> CameraContent(state, viewModel, closeSession)
                CameraPermissionState.PERMANENTLY_DENIED -> PermissionPanel(
                    permanent = true,
                    request = { context.openAppSettings() },
                    close = closeSession
                )
                else -> PermissionPanel(
                    permanent = false,
                    request = { requestedOnce = true; permissionLauncher.launch(Manifest.permission.CAMERA) },
                    close = closeSession
                )
            }
            if (debugMode && BuildConfig.DEBUG) VerificationDebugPanel(state, viewModel)
        }
    }
}

@Composable
private fun ColumnScope.CameraContent(
    state: CameraVerificationUiState,
    viewModel: CameraVerificationViewModel,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
        CameraPreviewHost(
            lens = state.lens,
            analyzerFactory = { MlKitPoseAnalyzer(onResult = viewModel::onAnalysis) },
            onReady = viewModel::cameraReady,
            onError = viewModel::cameraFailure,
            onSwitchAvailability = viewModel::setSwitchCameraAvailable
        )
        BodyPlacementGuide(state.fullBodyVisible)
        state.poseFrame?.let { PoseSkeletonOverlay(it, state.lens == LensDirection.FRONT) }
        if (state.lowLight) {
            Text(
                "LOW LIGHT — INCREASE ROOM LIGHTING",
                color = AscendColors.Amber,
                modifier = Modifier.padding(AscendSpacing.sm).background(AscendColors.Background.copy(alpha = .8f)).padding(AscendSpacing.sm)
            )
        }
    }
    LinearProgressIndicator({ state.calibration.progress }, Modifier.fillMaxWidth(), color = AscendColors.Cyan)
    SystemPanel(Modifier.fillMaxWidth(), accent = if (state.squat.completed) AscendColors.Success else AscendColors.Cyan) {
        Text("SQUAT ${state.squat.repetitions} / ${state.squat.target}", color = if (state.squat.completed) AscendColors.Success else AscendColors.Cyan)
        Text(state.squat.feedback, color = AscendColors.Text)
        state.squat.kneeAngle?.let { Text("Sudut lutut ${it.toInt()}° · ${state.squat.phase.name}", color = AscendColors.Muted) }
        LinearProgressIndicator(
            { state.squat.repetitions.toFloat() / state.squat.target },
            Modifier.fillMaxWidth(),
            color = AscendColors.Success
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AscendSpacing.sm)) {
        StatusChip("POSE", state.posePresent)
        StatusChip("FULL BODY", state.fullBodyVisible)
        StatusChip("LIGHT", !state.lowLight)
    }
    state.cameraError?.let { SafetyNoticeCard("CAMERA ERROR: $it") }
    SafetyNoticeCard("On-device analysis only. No video or camera frame is saved or uploaded.")
    Row(horizontalArrangement = Arrangement.spacedBy(AscendSpacing.sm)) {
        OutlinedButton(viewModel::switchCamera, Modifier.weight(1f), enabled = state.canSwitchCamera, shape = AscendShapes.panel) { Text("SWITCH CAMERA") }
        OutlinedButton(onClose, Modifier.weight(1f), shape = AscendShapes.panel) { Text("CLOSE") }
    }
}

@Composable
private fun PermissionPanel(permanent: Boolean, request: () -> Unit, close: () -> Unit) {
    SystemPanel(Modifier.fillMaxWidth(), accent = AscendColors.Amber) {
        SystemHeader(
            "Permission required",
            if (permanent) "Camera access blocked" else "Camera access required",
            if (permanent) "Enable Camera in Android app settings." else "Camera is used only for live, on-device pose guidance."
        )
        PrimarySystemButton(if (permanent) "Open settings" else "Grant camera access", action = request)
        SecondarySystemButton("Use another verification method", action = close)
    }
}

@Composable
private fun CameraPreviewHost(
    lens: LensDirection,
    analyzerFactory: () -> MlKitPoseAnalyzer,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    onSwitchAvailability: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    DisposableEffect(lens, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = analyzerFactory()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                provider = future.get()
                onSwitchAvailability(
                    provider!!.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) &&
                        provider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                )
                val selector = if (lens == LensDirection.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if (!provider!!.hasCamera(selector)) error("${lens.name.lowercase()} camera unavailable")
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { it.setAnalyzer(executor, analyzer) }
                analysis = imageAnalysis
                provider!!.unbindAll()
                provider!!.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            }.onSuccess { onReady() }.onFailure { onError(it.message ?: "Camera initialization failed") }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            analysis?.clearAnalyzer()
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }
}

@Composable
private fun BodyPlacementGuide(valid: Boolean) = Canvas(Modifier.fillMaxSize()) {
    val color = if (valid) AscendColors.Success else AscendColors.Cyan.copy(alpha = .65f)
    drawOval(
        color = color,
        topLeft = Offset(size.width * .22f, size.height * .05f),
        size = androidx.compose.ui.geometry.Size(size.width * .56f, size.height * .9f),
        style = Stroke(width = 2.dp.toPx())
    )
}

@Composable
private fun PoseSkeletonOverlay(frame: PoseFrame, mirrored: Boolean) = Canvas(Modifier.fillMaxSize()) {
    val sourceWidth = if (frame.rotationDegrees % 180 == 0) frame.imageWidth.toFloat() else frame.imageHeight.toFloat()
    val sourceHeight = if (frame.rotationDegrees % 180 == 0) frame.imageHeight.toFloat() else frame.imageWidth.toFloat()
    val scale = max(size.width / sourceWidth, size.height / sourceHeight)
    val dx = (size.width - sourceWidth * scale) / 2f
    val dy = (size.height - sourceHeight * scale) / 2f
    fun mapped(point: PosePoint): Offset {
        var x = point.x
        var y = point.y
        when (frame.rotationDegrees) {
            90 -> { val oldX = x; x = y; y = frame.imageWidth - oldX }
            180 -> { x = frame.imageWidth - x; y = frame.imageHeight - y }
            270 -> { val oldX = x; x = frame.imageHeight - y; y = oldX }
        }
        var screenX = dx + x * scale
        if (mirrored) screenX = size.width - screenX
        return Offset(screenX, dy + y * scale)
    }
    val points = frame.landmarks.associateBy { it.type }
    skeletonConnections.forEach { (a, b) ->
        val first = points[a]; val second = points[b]
        if (first != null && second != null) {
            val confidence = minOf(first.confidence, second.confidence)
            drawLine(if (confidence >= .55f) AscendColors.Cyan else AscendColors.Amber.copy(.5f), mapped(first), mapped(second), 2.dp.toPx())
        }
    }
    frame.landmarks.forEach {
        drawCircle(if (it.confidence >= .55f) AscendColors.Success else AscendColors.Amber, 3.dp.toPx(), mapped(it))
    }
}

@Composable
private fun StatusChip(label: String, valid: Boolean) = Surface(
    color = if (valid) AscendColors.Success.copy(.12f) else AscendColors.Surface,
    shape = AscendShapes.panel
) { Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = if (valid) AscendColors.Success else AscendColors.Muted, style = MaterialTheme.typography.labelSmall) }

@Composable
private fun VerificationDebugPanel(state: CameraVerificationUiState, viewModel: CameraVerificationViewModel) = SystemPanel(Modifier.fillMaxWidth(), accent = AscendColors.Violet) {
    Text("DEBUG VERIFICATION", color = AscendColors.Violet)
    Text("confidence=${state.poseFrame?.overallConfidence ?: 0f} · calibration=${state.calibration.status} · frame=${state.processingMillis}ms")
    Text("session=${state.verificationSession?.status ?: "not started"}")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(viewModel::permissionGranted) { Text("GRANT") }
        TextButton({ viewModel.permissionDenied(false) }) { Text("DENY") }
        TextButton({ viewModel.permissionDenied(true) }) { Text("BLOCK") }
        TextButton({ viewModel.cameraFailure("simulated camera unavailable") }) { Text("NO CAM") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton({ viewModel.simulatePose(true) }) { Text("POSE") }
        TextButton({ viewModel.simulatePose(false) }) { Text("LOST") }
        TextButton({ viewModel.simulateTerminal(VerificationSessionStatus.COMPLETED) }) { Text("SUCCESS") }
        TextButton({ viewModel.simulateTerminal(VerificationSessionStatus.FAILED) }) { Text("FAIL") }
        TextButton({ viewModel.simulateTerminal(VerificationSessionStatus.CANCELLED) }) { Text("CANCEL") }
    }
}

private fun stageLabel(state: CameraVerificationUiState) = when {
    state.cameraError != null -> "CAMERA INITIALIZATION FAILED"
    state.calibration.status == CalibrationStatus.READY -> "VERIFICATION ENGINE READY"
    state.calibration.status == CalibrationStatus.BODY_INCOMPLETE -> "FULL BODY NOT DETECTED"
    state.calibration.status == CalibrationStatus.CALIBRATING -> "HOLD POSITION"
    state.posePresent -> "BODY TRACKING INITIALIZED"
    else -> "CALIBRATION REQUIRED"
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
private fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private val skeletonConnections = listOf(
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.RIGHT_SHOULDER,
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.LEFT_ELBOW,
    PoseLandmarkType.LEFT_ELBOW to PoseLandmarkType.LEFT_WRIST,
    PoseLandmarkType.RIGHT_SHOULDER to PoseLandmarkType.RIGHT_ELBOW,
    PoseLandmarkType.RIGHT_ELBOW to PoseLandmarkType.RIGHT_WRIST,
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.LEFT_HIP,
    PoseLandmarkType.RIGHT_SHOULDER to PoseLandmarkType.RIGHT_HIP,
    PoseLandmarkType.LEFT_HIP to PoseLandmarkType.RIGHT_HIP,
    PoseLandmarkType.LEFT_HIP to PoseLandmarkType.LEFT_KNEE,
    PoseLandmarkType.LEFT_KNEE to PoseLandmarkType.LEFT_ANKLE,
    PoseLandmarkType.RIGHT_HIP to PoseLandmarkType.RIGHT_KNEE,
    PoseLandmarkType.RIGHT_KNEE to PoseLandmarkType.RIGHT_ANKLE
)

@ComposePreview(showBackground = true, backgroundColor = 0xFF04070E)
@Composable
private fun CameraVerificationStatusPreview() = AscendTheme {
    AscendBackground {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SystemHeader("Verification engine", "Camera pose framework", "CALIBRATION REQUIRED")
            SafetyNoticeCard("On-device analysis only. No video or camera frame is saved.")
            LinearProgressIndicator({ .45f }, Modifier.fillMaxWidth())
        }
    }
}
