package com.ascendsystem.app.feature.verification.domain

import com.ascendsystem.app.core.domain.QuestRepository
import com.ascendsystem.app.core.domain.QuestStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

interface AccessController {
    fun unlockAfterVerifiedQuest()
}

object NoOpAccessController : AccessController {
    override fun unlockAfterVerifiedQuest() = Unit
}

class StartVerificationUseCase @Inject constructor(private val repository: VerificationRepository) {
    suspend operator fun invoke(request: VerificationRequest): VerificationSession {
        val session = VerificationSession(
            id = UUID.randomUUID().toString(), questId = request.questId, type = request.type,
            target = request.target, safetyLevel = request.safetyLevel, createdAtMillis = request.createdAtMillis
        )
        repository.create(session)
        return session
    }
}
class UpdateVerificationProgressUseCase @Inject constructor(
    private val repository: VerificationRepository
) {
    suspend operator fun invoke(id: String, progress: Float, confidence: Float?, metrics: Map<String, String>): VerificationSession {
        val current = requireNotNull(repository.get(id)) { "Verification session not found" }
        require(current.status in setOf(VerificationSessionStatus.ACTIVE, VerificationSessionStatus.CALIBRATING))
        val updated = current.copy(progress = progress.coerceIn(0f, 1f), confidence = confidence?.coerceIn(0f, 1f), metrics = current.metrics + metrics)
        repository.update(updated)
        return updated
    }
}
class CompleteVerificationUseCase @Inject constructor(
    private val repository: VerificationRepository,
    private val quests: QuestRepository,
    private val accessController: AccessController = NoOpAccessController
) {
    suspend operator fun invoke(id: String, result: VerificationResult.Success, nowMillis: Long): VerificationSession {
        val current = requireNotNull(repository.get(id))
        require(current.status in setOf(VerificationSessionStatus.ACTIVE, VerificationSessionStatus.VERIFYING))
        val verifying = if (current.status == VerificationSessionStatus.ACTIVE) {
            current.copy(status = VerificationSessionStatus.VERIFYING).also { repository.update(it) }
        } else current
        val completed = verifying.copy(
            status = VerificationSessionStatus.COMPLETED, progress = 1f,
            confidence = result.confidence.coerceIn(0f, 1f), metrics = verifying.metrics + result.metrics,
            completedAtMillis = nowMillis
        )
        repository.update(completed)
        quests.quests().firstOrNull { it.id == verifying.questId }?.let {
            quests.upsert(it.copy(status = QuestStatus.COMPLETED))
        }
        accessController.unlockAfterVerifiedQuest()
        return completed
    }
}
class FailVerificationUseCase @Inject constructor(
    private val repository: VerificationRepository,
    private val quests: QuestRepository
) {
    suspend operator fun invoke(id: String, result: VerificationResult.Failure, nowMillis: Long): VerificationSession {
        val current = requireNotNull(repository.get(id))
        require(current.status !in setOf(VerificationSessionStatus.COMPLETED, VerificationSessionStatus.CANCELLED))
        val failed = current.copy(
            status = VerificationSessionStatus.FAILED, failureReasons = result.reasons,
            metrics = current.metrics + ("retryAllowed" to result.retryAllowed.toString()), completedAtMillis = nowMillis
        )
        repository.update(failed)
        if (!result.retryAllowed) {
            quests.quests().firstOrNull { it.id == current.questId }?.let {
                quests.upsert(it.copy(status = QuestStatus.FAILED))
            }
        }
        return failed
    }
}
class CancelVerificationUseCase @Inject constructor(
    private val repository: VerificationRepository,
    private val quests: QuestRepository
) {
    suspend operator fun invoke(id: String, result: VerificationResult.Cancelled, nowMillis: Long): VerificationSession {
        val current = requireNotNull(repository.get(id))
        require(current.status !in setOf(VerificationSessionStatus.COMPLETED, VerificationSessionStatus.FAILED))
        val cancelled = current.copy(
            status = VerificationSessionStatus.CANCELLED,
            failureReasons = result.reason?.let(::listOf).orEmpty(), completedAtMillis = nowMillis
        )
        repository.update(cancelled)
        quests.quests().firstOrNull { it.id == current.questId }?.let {
            quests.upsert(it.copy(status = QuestStatus.CANCELLED))
        }
        return cancelled
    }
}
class ObserveVerificationSessionUseCase @Inject constructor(private val repository: VerificationRepository) {
    operator fun invoke(id: String): Flow<VerificationSession?> = repository.observe(id)
}
