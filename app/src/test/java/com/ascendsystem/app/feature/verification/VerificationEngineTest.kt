package com.ascendsystem.app.feature.verification

import com.ascendsystem.app.core.domain.*
import com.ascendsystem.app.feature.verification.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VerificationEngineTest {
    private fun request() = VerificationRequest(
        "quest-1", VerificationType.CAMERA_POSE, VerificationTarget.BooleanTarget(),
        SafetyLevel.LOW, 100L
    )

    @Test fun `verification state machine accepts camera lifecycle`() {
        val machine = VerificationSessionStateMachine()
        var session = session()
        session = machine.transition(session, VerificationSessionStatus.PREPARING)
        session = machine.transition(session, VerificationSessionStatus.CALIBRATING)
        session = machine.transition(session, VerificationSessionStatus.ACTIVE)
        session = machine.transition(session, VerificationSessionStatus.VERIFYING)
        session = machine.transition(session, VerificationSessionStatus.COMPLETED)
        assertEquals(VerificationSessionStatus.COMPLETED, session.status)
        assertFalse(machine.canTransition(session.status, VerificationSessionStatus.ACTIVE))
    }

    @Test fun `start verification persists created session`() = runBlocking {
        val repository = FakeVerificationRepository()
        val result = StartVerificationUseCase(repository)(request())
        assertEquals(result, repository.get(result.id))
        assertEquals(VerificationSessionStatus.CREATED, result.status)
    }

    @Test fun `complete verification completes linked quest`() = runBlocking {
        val repository = FakeVerificationRepository()
        val questRepository = FakeQuestRepository()
        val access = RecordingAccessController()
        val active = session(status = VerificationSessionStatus.ACTIVE)
        repository.create(active)
        val result = CompleteVerificationUseCase(repository, questRepository, access)(
            active.id, VerificationResult.Success(.91f, mapOf("frames" to "42")), 500L
        )
        assertEquals(VerificationSessionStatus.COMPLETED, result.status)
        assertEquals(QuestStatus.COMPLETED, questRepository.quests().single().status)
        assertTrue(access.unlocked)
    }

    @Test fun `failure stores reasons and retry policy`() = runBlocking {
        val repository = FakeVerificationRepository()
        repository.create(session(status = VerificationSessionStatus.ACTIVE))
        val result = FailVerificationUseCase(repository, FakeQuestRepository())(
            "session-1", VerificationResult.Failure(listOf("pose lost"), true), 500L
        )
        assertEquals(listOf("pose lost"), result.failureReasons)
        assertEquals("true", result.metrics["retryAllowed"])
    }

    @Test fun `final verification failure fails linked quest`() = runBlocking {
        val repository = FakeVerificationRepository()
        val quests = FakeQuestRepository()
        repository.create(session(status = VerificationSessionStatus.ACTIVE))
        FailVerificationUseCase(repository, quests)(
            "session-1", VerificationResult.Failure(listOf("body not visible"), false), 500L
        )
        assertEquals(QuestStatus.FAILED, quests.quests().single().status)
    }

    @Test fun `cancel stores optional reason`() = runBlocking {
        val repository = FakeVerificationRepository()
        repository.create(session(status = VerificationSessionStatus.PREPARING))
        val result = CancelVerificationUseCase(repository, FakeQuestRepository())(
            "session-1", VerificationResult.Cancelled("user closed camera"), 500L
        )
        assertEquals(VerificationSessionStatus.CANCELLED, result.status)
        assertEquals("user closed camera", result.failureReasons.single())
    }

    @Test fun `persisted active session can be recovered`() = runBlocking {
        val repository = FakeVerificationRepository()
        val active = session(status = VerificationSessionStatus.ACTIVE).copy(progress = .4f)
        repository.create(active)
        assertEquals(active, repository.get(active.id))
    }

    private fun session(status: VerificationSessionStatus = VerificationSessionStatus.CREATED) = VerificationSession(
        "session-1", "quest-1", VerificationType.CAMERA_POSE, VerificationTarget.BooleanTarget(),
        SafetyLevel.LOW, status, 100L
    )
}

private class RecordingAccessController : AccessController {
    var unlocked = false
    override fun unlockAfterVerifiedQuest() { unlocked = true }
}

class CalibrationEngineTest {
    private fun fullFrame(confidence: Float = .9f): PoseFrame {
        val required = listOf(
            PoseLandmarkType.NOSE to (500f to 100f),
            PoseLandmarkType.LEFT_SHOULDER to (400f to 300f), PoseLandmarkType.RIGHT_SHOULDER to (600f to 300f),
            PoseLandmarkType.LEFT_HIP to (430f to 600f), PoseLandmarkType.RIGHT_HIP to (570f to 600f),
            PoseLandmarkType.LEFT_KNEE to (440f to 800f), PoseLandmarkType.RIGHT_KNEE to (560f to 800f),
            PoseLandmarkType.LEFT_ANKLE to (450f to 950f), PoseLandmarkType.RIGHT_ANKLE to (550f to 950f)
        )
        val points = required.map { PosePoint(it.first, it.second.first, it.second.second, 0f, confidence) }
        return PoseFrame(0, points, confidence, true, BodyOrientation.FRONT, 1000, 1100, 0)
    }

    @Test fun `body visibility requires all confident landmarks inside frame`() {
        val evaluator = BodyVisibilityEvaluator()
        assertTrue(evaluator.isFullBodyVisible(fullFrame().landmarks, 1000, 1100))
        assertFalse(evaluator.isFullBodyVisible(fullFrame(.2f).landmarks, 1000, 1100))
    }

    @Test fun `pose confidence evaluator rejects weak pose`() {
        assertFalse(PoseConfidenceEvaluator().acceptable(fullFrame(.4f)))
        assertTrue(PoseConfidenceEvaluator().acceptable(fullFrame(.9f)))
    }

    @Test fun `calibration reaches ready after stable hold`() {
        val engine = CalibrationEngine(requiredHoldMillis = 2_000)
        val frame = fullFrame()
        val first = engine.reduce(CalibrationState(), CalibrationInput(frame, true, true, 1_000))
        assertEquals(CalibrationStatus.CALIBRATING, first.status)
        val ready = engine.reduce(first, CalibrationInput(frame, true, true, 3_000))
        assertEquals(CalibrationStatus.READY, ready.status)
    }

    @Test fun `calibration resets when pose is lost`() {
        val engine = CalibrationEngine()
        val result = engine.reduce(CalibrationState(CalibrationStatus.CALIBRATING, 1_000, .5f), CalibrationInput(null, true, true, 2_000))
        assertEquals(CalibrationStatus.SEARCHING_FOR_BODY, result.status)
        assertNull(result.validSinceMillis)
    }
}

class SquatRepCounterTest {
    @Test fun `counts complete controlled squat`() {
        val counter = SquatRepCounter(target = 1)
        counter.update(frame(0, 170f))
        counter.update(frame(300, 135f))
        counter.update(frame(700, 90f))
        counter.update(frame(1_000, 125f))
        val result = counter.update(frame(1_300, 170f))
        assertEquals(1, result.repetitions)
        assertTrue(result.completed)
    }

    @Test fun `rejects shallow squat`() {
        val counter = SquatRepCounter(target = 1)
        counter.update(frame(0, 170f))
        counter.update(frame(300, 135f))
        val result = counter.update(frame(1_000, 170f))
        assertEquals(0, result.repetitions)
        assertFalse(result.completed)
    }

    @Test fun `rejects implausibly fast squat`() {
        val counter = SquatRepCounter(target = 1)
        counter.update(frame(0, 170f))
        counter.update(frame(100, 130f))
        counter.update(frame(200, 90f))
        counter.update(frame(300, 125f))
        val result = counter.update(frame(400, 170f))
        assertEquals(0, result.repetitions)
        assertEquals(SquatPhase.STANDING, result.phase)
    }

    private fun frame(time: Long, kneeAngle: Float): PoseFrame {
        val radians = Math.toRadians(kneeAngle.toDouble())
        fun points(prefix: String, x: Float): List<PosePoint> {
            val side = if (prefix == "LEFT") listOf(
                PoseLandmarkType.LEFT_HIP, PoseLandmarkType.LEFT_KNEE, PoseLandmarkType.LEFT_ANKLE
            ) else listOf(
                PoseLandmarkType.RIGHT_HIP, PoseLandmarkType.RIGHT_KNEE, PoseLandmarkType.RIGHT_ANKLE
            )
            return listOf(
                PosePoint(side[0], x + kotlin.math.sin(radians).toFloat() * 100f, 400f + kotlin.math.cos(radians).toFloat() * 100f, 0f, .95f),
                PosePoint(side[1], x, 400f, 0f, .95f),
                PosePoint(side[2], x, 500f, 0f, .95f)
            )
        }
        return PoseFrame(
            time, points("LEFT", 400f) + points("RIGHT", 600f), .95f, true,
            BodyOrientation.FRONT, 1000, 1100, 0
        )
    }
}

private class FakeVerificationRepository : VerificationRepository {
    private val values = MutableStateFlow<Map<String, VerificationSession>>(emptyMap())
    override suspend fun create(session: VerificationSession) { values.value = values.value + (session.id to session) }
    override suspend fun get(id: String) = values.value[id]
    override fun observe(id: String): Flow<VerificationSession?> = values.map { it[id] }
    override suspend fun update(session: VerificationSession) { values.value = values.value + (session.id to session) }
}

private class FakeQuestRepository : QuestRepository {
    private var quest = Quest(
        "quest-1", "Camera preview", "Verify pose", QuestType.DAILY,
        VerificationType.CAMERA_POSE, 1, 10, status = QuestStatus.ACTIVE
    )
    override suspend fun quests() = listOf(quest)
    override suspend fun upsert(quest: Quest) { this.quest = quest }
    override suspend fun delete(id: String) = Unit
}
