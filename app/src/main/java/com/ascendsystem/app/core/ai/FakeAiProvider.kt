package com.ascendsystem.app.core.ai

import com.ascendsystem.app.core.domain.*
import javax.inject.Inject

class FakeAiProvider @Inject constructor() : AiProvider {
    override suspend fun generateDailyPlan(context: UserContext): List<Quest> {
        val recovery = context.sleepHours < 5 || context.isSickOrInjured
        return listOf(
            Quest(
                id = "fake-daily-focus",
                title = if (recovery) "Recovery protocol" else "Deep focus",
                description = if (recovery) "Breathe slowly for 2 minutes." else "Focus without social apps for 25 minutes.",
                type = if (recovery) QuestType.RECOVERY else QuestType.DAILY,
                verificationType = VerificationType.TIMER,
                targetValue = if (recovery) 2 else 25,
                rewardXp = 30
            )
        )
    }
    override suspend fun respond(message: String, context: UserContext): String =
        if (context.sleepHours < 5) "[RECOVERY] Heavy quests reduced. Prioritize rest."
        else "[SYSTEM] Local coach ready. Cloud AI is not required."
}
