package com.ascendsystem.app.feature.assessment.domain

import com.ascendsystem.app.core.domain.AppRestriction
import com.ascendsystem.app.core.domain.StrictnessMode
import javax.inject.Inject

class ValidateAssessmentStepUseCase @Inject constructor() {
    operator fun invoke(draft: AssessmentDraft, step: AssessmentStep = draft.currentStep): List<String> = buildList {
        when (step) {
            AssessmentStep.BASIC_PROFILE -> {
                if (draft.basicProfile.displayName.isBlank()) add("Display name is required")
                draft.basicProfile.age?.let { if (it !in 13..120) add("Age must be between 13 and 120") }
            }
            AssessmentStep.GOALS -> if (draft.goals.isEmpty()) add("Select at least one goal")
            AssessmentStep.DAILY_SCHEDULE ->
                if (draft.dailySchedule.wakeMinute !in 0..1439 || draft.dailySchedule.sleepMinute !in 0..1439) add("Select valid wake and sleep times")
            AssessmentStep.SCREEN_TIME ->
                if (draft.screenTimeProfile.entertainmentLimitMinutes !in 0..1440) add("Entertainment limit is invalid")
            AssessmentStep.PRODUCTIVITY ->
                if (draft.productivityProfile.preferredFocusMinutes !in 5..180) add("Focus duration must be 5–180 minutes")
            AssessmentStep.PHYSICAL ->
                if (draft.physicalProfile.activityLevel !in 0..5) add("Activity level is invalid")
            AssessmentStep.SLEEP_RECOVERY ->
                if (draft.sleepProfile.averageSleepHours !in 0.0..16.0) add("Sleep duration is invalid")
            AssessmentStep.MOTIVATION ->
                if (draft.motivationProfile.motivators.isEmpty()) add("Select at least one motivator")
            AssessmentStep.STRICTNESS ->
                if (draft.strictnessMode == StrictnessMode.GUARDIAN && draft.emergencySetup.guardianContact.isNullOrBlank()) add("Guardian contact is required")
            AssessmentStep.EMERGENCY -> {
                if (draft.emergencySetup.applications.isEmpty()) add("At least one emergency application is required")
                if (draft.emergencySetup.overrideDurationMinutes !in setOf(5, 15, 30)) add("Override duration must be 5, 15, or 30 minutes")
            }
            AssessmentStep.REVIEW -> Unit
        }
    }
}

class SaveAssessmentDraftUseCase @Inject constructor(private val repository: AssessmentDraftRepository) {
    suspend operator fun invoke(draft: AssessmentDraft) = repository.save(draft.copy(updatedAtMillis = System.currentTimeMillis()))
}

class GenerateInitialProtocolUseCase @Inject constructor() {
    operator fun invoke(draft: AssessmentDraft): PersonalProtocol {
        val focusMinutes = draft.productivityProfile.preferredFocusMinutes.coerceIn(5, 90)
        val restrictions = draft.screenTimeProfile.distractingApps.map {
            AppRestriction(it, it.substringAfterLast('.'), draft.screenTimeProfile.entertainmentLimitMinutes, focusMinutes, false)
        }
        return PersonalProtocol(
            id = "protocol-${draft.updatedAtMillis}",
            wakeSchedule = WakeSchedule(draft.dailySchedule.wakeMinute),
            sleepProtocol = SleepProtocolConfig(
                draft.sleepProfile.desiredLockMinute,
                draft.sleepProfile.desiredWakeMinute,
                draft.sleepProfile.nightEmergencyApps + draft.emergencySetup.applications
            ),
            focusBlocks = listOf(FocusBlock("Primary focus", draft.dailySchedule.wakeMinute + 60, focusMinutes)),
            appRestrictions = restrictions,
            dailyQuestTemplates = listOf(QuestTemplate("Focused work", focusMinutes, 30)),
            correctiveQuestPolicy = CorrectiveQuestPolicy(draft.physicalProfile.physicalCorrectiveQuestsAllowed, 3),
            emergencyPolicy = EmergencyPolicy(draft.emergencySetup.applications, draft.emergencySetup.overrideDurationMinutes),
            strictnessMode = draft.strictnessMode
        )
    }
}

class CompleteAssessmentUseCase @Inject constructor(
    private val validator: ValidateAssessmentStepUseCase,
    private val generator: GenerateInitialProtocolUseCase,
    private val repository: AssessmentDraftRepository
) {
    suspend operator fun invoke(draft: AssessmentDraft): Result<PersonalProtocol> {
        val errors = AssessmentStep.entries.flatMap { validator(draft, it) }
        if (errors.isNotEmpty()) return Result.failure(IllegalArgumentException(errors.joinToString()))
        val proposal = generator(draft)
        repository.complete(proposal)
        return Result.success(proposal)
    }
}

class ActivateProtocolUseCase @Inject constructor(private val repository: AssessmentDraftRepository) {
    suspend operator fun invoke(protocol: PersonalProtocol, heldMillis: Long, nowMillis: Long): Result<PersonalProtocol> {
        if (heldMillis < 3_000L) return Result.failure(IllegalArgumentException("Activation hold was shorter than three seconds"))
        val active = protocol.copy(status = ProtocolStatus.ACTIVE, contractVersion = 1, activatedAtMillis = nowMillis)
        repository.activate(active, contractVersion = 1, activatedAtMillis = nowMillis)
        return Result.success(active)
    }
}

data class RestrictionConflict(
    val packageName: String,
    val existing: AppRestriction,
    val recommended: AppRestriction
)
data class ProtocolMergeResult(val merged: PersonalProtocol, val conflicts: List<RestrictionConflict>)

/** Existing user rules remain authoritative until a UI records an explicit override choice. */
class MergeProtocolWithExistingSettingsUseCase @Inject constructor() {
    operator fun invoke(proposal: PersonalProtocol, existing: List<AppRestriction>): ProtocolMergeResult {
        val existingByPackage = existing.associateBy { it.packageName }
        val conflicts = proposal.appRestrictions.mapNotNull { recommendation ->
            existingByPackage[recommendation.packageName]
                ?.takeIf { it != recommendation }
                ?.let { RestrictionConflict(recommendation.packageName, it, recommendation) }
        }
        val untouchedRecommendations = proposal.appRestrictions.filterNot { it.packageName in existingByPackage }
        return ProtocolMergeResult(proposal.copy(appRestrictions = existing + untouchedRecommendations), conflicts)
    }
}
