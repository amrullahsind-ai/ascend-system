package com.ascendsystem.app.service.scheduling

import com.ascendsystem.app.core.domain.Quest

/** Device-facing seams keep domain decisions separate from Android side effects. */
interface QuestScheduler {
    suspend fun schedule(quest: Quest): Result<Unit>
    suspend fun cancel(questId: String): Result<Unit>
}

interface NotificationGateway {
    suspend fun showQuestReminder(quest: Quest): Result<Unit>
    suspend fun showSleepWarning(message: String): Result<Unit>
}

class FakeQuestScheduler : QuestScheduler {
    override suspend fun schedule(quest: Quest) = Result.success(Unit)
    override suspend fun cancel(questId: String) = Result.success(Unit)
}

class FakeNotificationGateway : NotificationGateway {
    override suspend fun showQuestReminder(quest: Quest) = Result.success(Unit)
    override suspend fun showSleepWarning(message: String) = Result.success(Unit)
}
