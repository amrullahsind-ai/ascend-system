package com.ascendsystem.app.feature.verification.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascendsystem.app.core.domain.SafetyLevel
import com.ascendsystem.app.core.domain.VerificationType
import com.ascendsystem.app.feature.verification.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CameraPermissionState { UNKNOWN, REQUIRED, GRANTED, DENIED, PERMANENTLY_DENIED }
enum class CameraSessionStage { PERMISSION_REQUIRED, PREPARATION, BODY_PLACEMENT, CALIBRATION, POSE_ACTIVE, READY, ERROR }
enum class LensDirection { FRONT, BACK }

data class CameraVerificationUiState(
    val permission: CameraPermissionState = CameraPermissionState.UNKNOWN,
    val stage: CameraSessionStage = CameraSessionStage.PERMISSION_REQUIRED,
    val lens: LensDirection = LensDirection.FRONT,
    val canSwitchCamera: Boolean = false,
    val cameraAvailable: Boolean = true,
    val cameraError: String? = null,
    val poseFrame: PoseFrame? = null,
    val posePresent: Boolean = false,
    val fullBodyVisible: Boolean = false,
    val lowLight: Boolean = false,
    val calibration: CalibrationState = CalibrationState(),
    val verificationSession: VerificationSession? = null,
    val processingMillis: Long = 0,
    val reducedMotion: Boolean = false
)

@HiltViewModel
class CameraVerificationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: VerificationRepository,
    private val startVerification: StartVerificationUseCase,
    private val failVerification: FailVerificationUseCase,
    private val cancelVerification: CancelVerificationUseCase
) : ViewModel() {
    private val calibrationEngine = CalibrationEngine()
    private val _state = MutableStateFlow(CameraVerificationUiState())
    val state = _state.asStateFlow()
    private var lastPersistedAt = 0L
    private var statusBeforePause: VerificationSessionStatus? = null

    init {
        savedStateHandle.get<String>("verificationSessionId")?.let { id ->
            viewModelScope.launch {
                repository.get(id)?.let { recovered ->
                    _state.update { it.copy(verificationSession = recovered, stage = recovered.toCameraStage()) }
                }
            }
        }
    }

    fun permissionRequired() = _state.update { it.copy(permission = CameraPermissionState.REQUIRED, stage = CameraSessionStage.PERMISSION_REQUIRED) }
    fun permissionDenied(permanent: Boolean) = _state.update {
        it.copy(
            permission = if (permanent) CameraPermissionState.PERMANENTLY_DENIED else CameraPermissionState.DENIED,
            stage = CameraSessionStage.PERMISSION_REQUIRED
        )
    }
    fun permissionGranted() {
        _state.update { it.copy(permission = CameraPermissionState.GRANTED, stage = CameraSessionStage.PREPARATION) }
        ensureSession()
    }
    fun cameraReady() {
        _state.update { it.copy(cameraAvailable = true, cameraError = null, stage = CameraSessionStage.BODY_PLACEMENT) }
        transitionSession(VerificationSessionStatus.PREPARING)
    }
    fun cameraFailure(message: String) {
        _state.update { it.copy(cameraAvailable = false, cameraError = message, stage = CameraSessionStage.ERROR) }
        _state.value.verificationSession?.let { session ->
            viewModelScope.launch {
                runCatching {
                    failVerification(session.id, VerificationResult.Failure(listOf(message), retryAllowed = true), System.currentTimeMillis())
                }.onSuccess { failed -> _state.update { it.copy(verificationSession = failed) } }
            }
        }
    }
    fun switchCamera() = _state.update { it.copy(lens = if (it.lens == LensDirection.FRONT) LensDirection.BACK else LensDirection.FRONT) }
    fun setSwitchCameraAvailable(available: Boolean) = _state.update { it.copy(canSwitchCamera = available) }
    fun setReducedMotion(enabled: Boolean) = _state.update { it.copy(reducedMotion = enabled) }

    fun onAnalysis(analysis: PoseAnalysis) {
        analysis.error?.let { message -> _state.update { it.copy(cameraError = message) } }
        val previous = _state.value.calibration
        val calibration = calibrationEngine.reduce(
            previous,
            CalibrationInput(
                pose = analysis.frame,
                cameraStable = true,
                bodyInsideGuide = analysis.frame?.fullBodyVisible == true,
                nowMillis = System.currentTimeMillis()
            )
        )
        val stage = when (calibration.status) {
            CalibrationStatus.READY -> CameraSessionStage.READY
            CalibrationStatus.CALIBRATING, CalibrationStatus.HOLD_STILL -> CameraSessionStage.CALIBRATION
            else -> CameraSessionStage.POSE_ACTIVE
        }
        _state.update {
            it.copy(
                poseFrame = analysis.frame, posePresent = analysis.frame != null,
                fullBodyVisible = analysis.frame?.fullBodyVisible == true,
                lowLight = analysis.luminance < 45.0, processingMillis = analysis.processingMillis,
                calibration = calibration, stage = stage
            )
        }
        val sessionStatus = if (calibration.status == CalibrationStatus.READY) VerificationSessionStatus.ACTIVE else VerificationSessionStatus.CALIBRATING
        transitionSession(sessionStatus, calibration.progress, analysis.frame?.overallConfidence)
    }

    fun onBackgrounded() {
        val status = _state.value.verificationSession?.status
        if (status in setOf(VerificationSessionStatus.ACTIVE, VerificationSessionStatus.CALIBRATING)) {
            statusBeforePause = status
            transitionSession(VerificationSessionStatus.PAUSED)
        }
    }
    fun onForegrounded() {
        if (_state.value.verificationSession?.status == VerificationSessionStatus.PAUSED) {
            transitionSession(statusBeforePause ?: VerificationSessionStatus.CALIBRATING)
            statusBeforePause = null
        }
    }
    fun cancelAndClose(onClosed: () -> Unit) {
        val session = _state.value.verificationSession
        if (session == null || session.status in setOf(VerificationSessionStatus.COMPLETED, VerificationSessionStatus.FAILED, VerificationSessionStatus.CANCELLED)) {
            onClosed()
            return
        }
        viewModelScope.launch {
            runCatching {
                cancelVerification(session.id, VerificationResult.Cancelled("camera screen closed"), System.currentTimeMillis())
            }
            onClosed()
        }
    }
    fun simulatePose(present: Boolean) {
        if (!present) onAnalysis(PoseAnalysis(null, 90.0, 4))
        else {
            val points = PoseLandmarkType.entries.mapIndexed { index, type ->
                PosePoint(type, 200f + (index % 2) * 200f, 80f + index * 20f, 0f, .92f)
            }
            onAnalysis(PoseAnalysis(PoseFrame(System.currentTimeMillis(), points, .92f, true, BodyOrientation.FRONT, 640, 960, 0), 90.0, 8))
        }
    }
    fun simulateTerminal(status: VerificationSessionStatus) {
        if (status in setOf(VerificationSessionStatus.COMPLETED, VerificationSessionStatus.FAILED, VerificationSessionStatus.CANCELLED)) {
            forceSessionStatus(status)
        }
    }

    private fun ensureSession() {
        if (_state.value.verificationSession != null) return
        viewModelScope.launch {
            val session = startVerification(
                VerificationRequest(
                    questId = savedStateHandle["questId"] ?: "debug-camera-preview",
                    type = VerificationType.CAMERA_POSE,
                    target = VerificationTarget.BooleanTarget(),
                    safetyLevel = SafetyLevel.LOW,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
            savedStateHandle["verificationSessionId"] = session.id
            _state.update { it.copy(verificationSession = session) }
        }
    }

    private fun transitionSession(
        status: VerificationSessionStatus,
        progress: Float = _state.value.verificationSession?.progress ?: 0f,
        confidence: Float? = _state.value.verificationSession?.confidence
    ) {
        val current = _state.value.verificationSession ?: return
        if (current.status == status || current.status in setOf(VerificationSessionStatus.COMPLETED, VerificationSessionStatus.CANCELLED)) return
        val machine = VerificationSessionStateMachine()
        if (!machine.canTransition(current.status, status)) return
        val now = System.currentTimeMillis()
        val updated = machine.transition(current, status).copy(
            startedAtMillis = current.startedAtMillis ?: if (status == VerificationSessionStatus.ACTIVE) now else null,
            progress = progress, confidence = confidence,
            metrics = current.metrics + mapOf("cameraStage" to _state.value.stage.name, "processingMillis" to _state.value.processingMillis.toString())
        )
        _state.update { it.copy(verificationSession = updated) }
        if (now - lastPersistedAt >= 500 || status != VerificationSessionStatus.CALIBRATING) {
            lastPersistedAt = now
            viewModelScope.launch { repository.update(updated) }
        }
    }

    private fun forceSessionStatus(status: VerificationSessionStatus) {
        val current = _state.value.verificationSession ?: return
        val updated = current.copy(status = status, completedAtMillis = System.currentTimeMillis())
        _state.update { it.copy(verificationSession = updated) }
        viewModelScope.launch { repository.update(updated) }
    }

    private fun VerificationSession.toCameraStage() = when (status) {
        VerificationSessionStatus.CREATED -> CameraSessionStage.PREPARATION
        VerificationSessionStatus.PREPARING -> CameraSessionStage.BODY_PLACEMENT
        VerificationSessionStatus.CALIBRATING -> CameraSessionStage.CALIBRATION
        VerificationSessionStatus.ACTIVE, VerificationSessionStatus.PAUSED, VerificationSessionStatus.VERIFYING -> CameraSessionStage.POSE_ACTIVE
        VerificationSessionStatus.COMPLETED -> CameraSessionStage.READY
        VerificationSessionStatus.FAILED, VerificationSessionStatus.CANCELLED -> CameraSessionStage.ERROR
    }
}
