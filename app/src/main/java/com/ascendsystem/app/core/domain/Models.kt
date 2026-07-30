package com.ascendsystem.app.core.domain

enum class SystemState {
    IDLE, SCHEDULED, WARNING, QUEST_PENDING, QUEST_ACTIVE, VERIFYING,
    QUEST_COMPLETED, QUEST_FAILED, LOCK_ACTIVE, SLEEP_PROTOCOL,
    EMERGENCY_OVERRIDE, RECOVERY_MODE
}
enum class QuestType { DAILY, SCHEDULED, TRIGGERED, EMERGENCY, RECOVERY, BOSS }
enum class QuestStatus { DRAFT, SCHEDULED, ACTIVE, VERIFYING, COMPLETED, FAILED, CANCELLED }
enum class VerificationType {
    NONE, CAMERA_POSE, GPS, TIMER, FOCUS, APP_USAGE, PHOTO, QUIZ, GUARDIAN, MANUAL_PROOF,
    @Deprecated("Use GUARDIAN") PARTNER,
    @Deprecated("Use MANUAL_PROOF") MANUAL_DEMO
}
enum class SafetyLevel { LOW, MODERATE, HIGH }
enum class StrictnessMode { GUIDED, STRICT, GUARDIAN, DEDICATED }

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val type: QuestType,
    val verificationType: VerificationType,
    val targetValue: Int,
    val rewardXp: Int,
    val scheduledAtMillis: Long? = null,
    val deadlineAtMillis: Long? = null,
    val status: QuestStatus = QuestStatus.DRAFT,
    val safetyLevel: SafetyLevel = SafetyLevel.LOW,
    val createdBy: String = "USER"
)

data class AppRestriction(
    val packageName: String,
    val displayName: String,
    val dailyLimitMinutes: Int?,
    val sessionLimitMinutes: Int?,
    val isEssential: Boolean,
    val enabled: Boolean = true
)

data class SleepSchedule(
    val preparationMinuteOfDay: Int,
    val lockMinuteOfDay: Int,
    val wakeMinuteOfDay: Int,
    val enabled: Boolean
)

data class OverrideRequest(val reason: String, val note: String, val durationMinutes: Int)
data class UserContext(
    val isDriving: Boolean = false,
    val isSickOrInjured: Boolean = false,
    val inPublicSetting: Boolean = false,
    val sleepHours: Double = 8.0
)
data class RuleDecision(val allowed: Boolean, val nextState: SystemState, val reason: String)

interface QuestRepository {
    suspend fun quests(): List<Quest>
    suspend fun upsert(quest: Quest)
    suspend fun delete(id: String)
}

interface RestrictionRepository {
    suspend fun restrictions(): List<AppRestriction>
    suspend fun upsert(restriction: AppRestriction)
    suspend fun delete(packageName: String)
}

interface OverrideRepository {
    suspend fun activate(request: OverrideRequest, activeQuestId: String?, nowMillis: Long)
    suspend fun activeUntilMillis(): Long
}

interface AiProvider {
    suspend fun generateDailyPlan(context: UserContext): List<Quest>
    suspend fun respond(message: String, context: UserContext): String
}
