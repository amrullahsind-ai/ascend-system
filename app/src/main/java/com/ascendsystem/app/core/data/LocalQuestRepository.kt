package com.ascendsystem.app.core.data

import com.ascendsystem.app.core.database.QuestDao
import com.ascendsystem.app.core.database.QuestEntity
import com.ascendsystem.app.core.domain.*
import javax.inject.Inject

class LocalQuestRepository @Inject constructor(
    private val dao: QuestDao
) : QuestRepository {
    override suspend fun quests(): List<Quest> = dao.all().map { it.toDomain() }
    override suspend fun upsert(quest: Quest) = dao.upsert(quest.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}

private fun QuestEntity.toDomain() = Quest(
    id, title, description, QuestType.valueOf(type), VerificationType.valueOf(verificationType),
    targetValue, rewardXp, scheduledAtEpochMs, deadlineAtEpochMs, QuestStatus.valueOf(status),
    SafetyLevel.valueOf(safetyLevel), createdBy
)

private fun Quest.toEntity() = QuestEntity(
    id, title, description, type.name, verificationType.name, targetValue, rewardXp,
    scheduledAtMillis, deadlineAtMillis, status.name, safetyLevel.name, createdBy,
    System.currentTimeMillis()
)
