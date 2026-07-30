package com.ascendsystem.app.feature.assessment.domain

import com.ascendsystem.app.core.domain.AppRestriction
import com.ascendsystem.app.core.domain.StrictnessMode

enum class AssessmentStep {
    BASIC_PROFILE, GOALS, DAILY_SCHEDULE, SCREEN_TIME, PRODUCTIVITY,
    PHYSICAL, SLEEP_RECOVERY, MOTIVATION, STRICTNESS, EMERGENCY, REVIEW
}
enum class UserGoal { DISCIPLINE, REDUCE_SCREEN_TIME, FITNESS, STUDY, SLEEP, PROJECTS, ROUTINES, PRODUCTIVITY }
enum class DailyRole { STUDENT, EMPLOYEE, CAREGIVER, SELF_EMPLOYED, OTHER }
enum class MotivationStyle { STRICT_DIRECT, CALM_ANALYTICAL, SUPPORTIVE, COMPETITIVE, MINIMAL }
enum class ReminderIntensity { LIGHT, STANDARD, STRONG }

data class BasicProfileDraft(
    val displayName: String = "",
    val age: Int? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val dailyRole: DailyRole? = null,
    val gender: String? = null,
    val physicalLimitations: String = ""
)
data class DailyScheduleDraft(
    val wakeMinute: Int = 420,
    val sleepMinute: Int = 1350,
    val commitments: String = "",
    val focusPeriods: String = "",
    val exercisePeriods: String = ""
)
data class ScreenTimeProfileDraft(
    val distractingApps: Set<String> = emptySet(),
    val essentialApps: Set<String> = emptySet(),
    val sleepAllowlist: Set<String> = emptySet(),
    val focusAllowlist: Set<String> = emptySet(),
    val approximateDailyMinutes: Int = 180,
    val lateNightScrolling: Boolean = false,
    val shortVideoMinutes: Int = 30,
    val entertainmentLimitMinutes: Int = 90
)
data class ProductivityProfileDraft(
    val typicalFocusMinutes: Int = 25,
    val difficultyStarting: Int = 3,
    val difficultyFinishing: Int = 3,
    val procrastinationFrequency: Int = 3,
    val distractionCauses: String = "",
    val preferredFocusMinutes: Int = 25,
    val reminderIntensity: ReminderIntensity = ReminderIntensity.STANDARD
)
data class PhysicalProfileDraft(
    val activityLevel: Int = 1,
    val exerciseDaysPerWeek: Int = 0,
    val comfortableSquats: Int = 0,
    val comfortablePushUps: Int = 0,
    val plankSeconds: Int = 0,
    val canWalkOrRun: Boolean = true,
    val injuriesOrLimitations: String = "",
    val physicalCorrectiveQuestsAllowed: Boolean = false
)
data class SleepProfileDraft(
    val averageSleepHours: Double = 7.0,
    val consistency: Int = 3,
    val difficultySleeping: Int = 3,
    val difficultyWaking: Int = 3,
    val desiredLockMinute: Int = 1350,
    val desiredWakeMinute: Int = 420,
    val nightEmergencyApps: Set<String> = emptySet()
)
data class MotivationProfileDraft(
    val style: MotivationStyle = MotivationStyle.CALM_ANALYTICAL,
    val motivators: Set<String> = setOf("XP", "VISIBLE_PROGRESS")
)
data class EmergencySetupDraft(
    val contacts: Set<String> = emptySet(),
    val applications: Set<String> = setOf("Phone"),
    val overrideDurationMinutes: Int = 15,
    val guardianContact: String? = null
)
data class AssessmentDraft(
    val basicProfile: BasicProfileDraft = BasicProfileDraft(),
    val goals: Set<UserGoal> = emptySet(),
    val dailySchedule: DailyScheduleDraft = DailyScheduleDraft(),
    val screenTimeProfile: ScreenTimeProfileDraft = ScreenTimeProfileDraft(),
    val productivityProfile: ProductivityProfileDraft = ProductivityProfileDraft(),
    val physicalProfile: PhysicalProfileDraft = PhysicalProfileDraft(),
    val sleepProfile: SleepProfileDraft = SleepProfileDraft(),
    val motivationProfile: MotivationProfileDraft = MotivationProfileDraft(),
    val strictnessMode: StrictnessMode = StrictnessMode.GUIDED,
    val emergencySetup: EmergencySetupDraft = EmergencySetupDraft(),
    val currentStep: AssessmentStep = AssessmentStep.BASIC_PROFILE,
    val updatedAtMillis: Long = 0L
)

data class AssessmentUiState(
    val draft: AssessmentDraft = AssessmentDraft(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val stepErrors: List<String> = emptyList()
)
sealed interface AssessmentAction {
    data class SetDisplayName(val value: String) : AssessmentAction
    data class ToggleGoal(val goal: UserGoal) : AssessmentAction
    data class SelectStrictness(val mode: StrictnessMode) : AssessmentAction
    data class SetEmergencyDuration(val minutes: Int) : AssessmentAction
    data class SetGuardianContact(val value: String) : AssessmentAction
    data object Next : AssessmentAction
    data object Back : AssessmentAction
    data object Retry : AssessmentAction
}

enum class ProtocolStatus { PROPOSED, APPROVED, ACTIVE, REJECTED }
data class WakeSchedule(val wakeMinute: Int)
data class SleepProtocolConfig(val lockMinute: Int, val wakeMinute: Int, val emergencyApps: Set<String>)
data class FocusBlock(val label: String, val startMinute: Int, val durationMinutes: Int)
data class QuestTemplate(val title: String, val targetMinutes: Int, val rewardXp: Int)
data class CorrectiveQuestPolicy(val physicalAllowed: Boolean, val maxPerDay: Int)
data class EmergencyPolicy(val applications: Set<String>, val overrideMinutes: Int)
data class PersonalProtocol(
    val id: String,
    val wakeSchedule: WakeSchedule,
    val sleepProtocol: SleepProtocolConfig,
    val focusBlocks: List<FocusBlock>,
    val appRestrictions: List<AppRestriction>,
    val dailyQuestTemplates: List<QuestTemplate>,
    val correctiveQuestPolicy: CorrectiveQuestPolicy,
    val emergencyPolicy: EmergencyPolicy,
    val strictnessMode: StrictnessMode,
    val status: ProtocolStatus = ProtocolStatus.PROPOSED,
    val protocolVersion: Int = 1,
    val contractVersion: Int? = null,
    val activatedAtMillis: Long? = null
)

interface AssessmentDraftRepository {
    suspend fun load(): AssessmentDraft?
    suspend fun save(draft: AssessmentDraft)
    suspend fun complete(protocol: PersonalProtocol)
    suspend fun activate(protocol: PersonalProtocol, contractVersion: Int, activatedAtMillis: Long)
    suspend fun metadata(): AssessmentMetadata
}
data class AssessmentMetadata(
    val assessmentCompleted: Boolean,
    val assessmentVersion: Int,
    val protocolActivated: Boolean,
    val migratedFromV1: Boolean
)
