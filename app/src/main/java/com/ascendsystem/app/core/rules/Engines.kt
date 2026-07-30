package com.ascendsystem.app.core.rules

import com.ascendsystem.app.core.domain.*
import kotlin.math.floor

class QuestStateMachine {
    private val transitions = mapOf(
        SystemState.IDLE to setOf(SystemState.SCHEDULED, SystemState.WARNING, SystemState.SLEEP_PROTOCOL, SystemState.RECOVERY_MODE),
        SystemState.SCHEDULED to setOf(SystemState.QUEST_PENDING, SystemState.IDLE),
        SystemState.WARNING to setOf(SystemState.QUEST_PENDING, SystemState.IDLE),
        SystemState.QUEST_PENDING to setOf(SystemState.QUEST_ACTIVE, SystemState.EMERGENCY_OVERRIDE),
        SystemState.QUEST_ACTIVE to setOf(SystemState.VERIFYING, SystemState.EMERGENCY_OVERRIDE),
        SystemState.VERIFYING to setOf(SystemState.QUEST_COMPLETED, SystemState.QUEST_FAILED),
        SystemState.QUEST_COMPLETED to setOf(SystemState.IDLE),
        SystemState.QUEST_FAILED to setOf(SystemState.LOCK_ACTIVE, SystemState.IDLE),
        SystemState.LOCK_ACTIVE to setOf(SystemState.QUEST_PENDING, SystemState.EMERGENCY_OVERRIDE),
        SystemState.SLEEP_PROTOCOL to setOf(SystemState.IDLE, SystemState.EMERGENCY_OVERRIDE),
        SystemState.EMERGENCY_OVERRIDE to setOf(SystemState.IDLE),
        SystemState.RECOVERY_MODE to setOf(SystemState.IDLE)
    )
    fun canTransition(from: SystemState, to: SystemState) = transitions[from]?.contains(to) == true
    fun transition(from: SystemState, to: SystemState): SystemState {
        require(canTransition(from, to)) { "Invalid state transition: $from -> $to" }
        return to
    }
}

class SafetyEngine {
    fun validate(quest: Quest, context: UserContext): RuleDecision {
        val physical = quest.verificationType == VerificationType.MANUAL_DEMO
        if (physical && (context.isDriving || context.isSickOrInjured || context.inPublicSetting)) {
            return RuleDecision(false, SystemState.RECOVERY_MODE, "Physical quest replaced for current safety context")
        }
        if (physical && context.sleepHours < 5) {
            return RuleDecision(false, SystemState.RECOVERY_MODE, "Recovery prioritized after insufficient sleep")
        }
        return RuleDecision(true, SystemState.QUEST_PENDING, "Safety checks passed")
    }
}

class RuleEngine(private val safety: SafetyEngine = SafetyEngine()) {
    fun evaluateTriggeredQuest(
        quest: Quest,
        usageMinutes: Int,
        limitMinutes: Int,
        executionsToday: Int,
        maxExecutions: Int,
        cooldownElapsed: Boolean,
        context: UserContext
    ): RuleDecision {
        if (usageMinutes < limitMinutes) return RuleDecision(false, SystemState.IDLE, "Usage is below limit")
        if (executionsToday >= maxExecutions) return RuleDecision(false, SystemState.IDLE, "Daily trigger cap reached")
        if (!cooldownElapsed) return RuleDecision(false, SystemState.IDLE, "Trigger cooldown active")
        return safety.validate(quest, context)
    }
}

class ScheduleEngine {
    fun isWithinWindow(minuteOfDay: Int, start: Int, end: Int): Boolean =
        if (start <= end) minuteOfDay in start until end
        else minuteOfDay >= start || minuteOfDay < end

    fun sleepState(minuteOfDay: Int, schedule: SleepSchedule): SystemState =
        if (schedule.enabled && isWithinWindow(minuteOfDay, schedule.lockMinuteOfDay, schedule.wakeMinuteOfDay))
            SystemState.SLEEP_PROTOCOL else SystemState.IDLE
}

object Progression {
    fun levelForXp(xp: Int): Int = floor(kotlin.math.sqrt(xp.coerceAtLeast(0) / 100.0)).toInt() + 1
    fun xpForLevel(level: Int): Int = (level.coerceAtLeast(1) - 1).let { it * it * 100 }
}
