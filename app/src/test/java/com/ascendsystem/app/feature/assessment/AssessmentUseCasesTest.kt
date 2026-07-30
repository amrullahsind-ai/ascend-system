package com.ascendsystem.app.feature.assessment

import com.ascendsystem.app.core.domain.StrictnessMode
import com.ascendsystem.app.core.domain.AppRestriction
import com.ascendsystem.app.feature.assessment.data.AssessmentCodec
import com.ascendsystem.app.feature.assessment.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AssessmentUseCasesTest {
    private fun validDraft() = AssessmentDraft(
        basicProfile = BasicProfileDraft(displayName = "Hunter"),
        goals = setOf(UserGoal.DISCIPLINE),
        motivationProfile = MotivationProfileDraft(motivators = setOf("XP")),
        emergencySetup = EmergencySetupDraft(applications = setOf("Phone"), overrideDurationMinutes = 15),
        updatedAtMillis = 42
    )

    @Test fun `basic step requires display name`() {
        val errors = ValidateAssessmentStepUseCase()(AssessmentDraft(), AssessmentStep.BASIC_PROFILE)
        assertTrue(errors.any { it.contains("Display name") })
    }

    @Test fun `emergency setup validates application and duration`() {
        val draft = validDraft().copy(emergencySetup = EmergencySetupDraft(applications = emptySet(), overrideDurationMinutes = 10))
        val errors = ValidateAssessmentStepUseCase()(draft, AssessmentStep.EMERGENCY)
        assertEquals(2, errors.size)
    }

    @Test fun `guardian strictness requires guardian contact`() {
        val draft = validDraft().copy(strictnessMode = StrictnessMode.GUARDIAN)
        assertFalse(ValidateAssessmentStepUseCase()(draft, AssessmentStep.STRICTNESS).isEmpty())
    }

    @Test fun `assessment payload survives persistence round trip`() {
        val original = validDraft().copy(
            goals = setOf(UserGoal.DISCIPLINE, UserGoal.SLEEP),
            screenTimeProfile = ScreenTimeProfileDraft(distractingApps = setOf("com.social.app")),
            currentStep = AssessmentStep.SCREEN_TIME
        )
        val restored = AssessmentCodec.decode(AssessmentCodec.encode(original), original.updatedAtMillis)
        assertEquals(original, restored)
    }

    @Test fun `protocol generation is deterministic and proposed`() {
        val draft = validDraft().copy(
            screenTimeProfile = ScreenTimeProfileDraft(
                distractingApps = setOf("com.social.app"),
                entertainmentLimitMinutes = 60
            )
        )
        val protocol = GenerateInitialProtocolUseCase()(draft)
        assertEquals(ProtocolStatus.PROPOSED, protocol.status)
        assertEquals(60, protocol.appRestrictions.single().dailyLimitMinutes)
        assertEquals(draft.strictnessMode, protocol.strictnessMode)
    }

    @Test fun `activation rejects short hold and records accepted contract`() = runBlocking {
        val repository = FakeAssessmentRepository()
        val proposal = GenerateInitialProtocolUseCase()(validDraft())
        val useCase = ActivateProtocolUseCase(repository)
        assertTrue(useCase(proposal, 2_999, 100).isFailure)
        val result = useCase(proposal, 3_000, 100).getOrThrow()
        assertEquals(ProtocolStatus.ACTIVE, result.status)
        assertEquals(1, result.contractVersion)
        assertTrue(repository.metadata().protocolActivated)
    }

    @Test fun `existing user metadata remains marked as migrated`() = runBlocking {
        val repository = FakeAssessmentRepository(migrated = true)
        val proposal = GenerateInitialProtocolUseCase()(validDraft())
        repository.complete(proposal)
        repository.activate(proposal, 1, 100)
        assertTrue(repository.metadata().migratedFromV1)
    }

    @Test fun `existing restriction wins and conflict is reported`() {
        val proposal = GenerateInitialProtocolUseCase()(validDraft().copy(
            screenTimeProfile = ScreenTimeProfileDraft(distractingApps = setOf("com.social"), entertainmentLimitMinutes = 60)
        ))
        val existing = AppRestriction("com.social", "Social", 30, 10, false)
        val result = MergeProtocolWithExistingSettingsUseCase()(proposal, listOf(existing))
        assertEquals(existing, result.merged.appRestrictions.single())
        assertEquals("com.social", result.conflicts.single().packageName)
    }
}

private class FakeAssessmentRepository(migrated: Boolean = false) : AssessmentDraftRepository {
    private var draft: AssessmentDraft? = null
    private var meta = AssessmentMetadata(false, 0, false, migrated)
    override suspend fun load() = draft
    override suspend fun save(draft: AssessmentDraft) { this.draft = draft }
    override suspend fun complete(protocol: PersonalProtocol) { meta = meta.copy(assessmentCompleted = true, assessmentVersion = 1) }
    override suspend fun activate(protocol: PersonalProtocol, contractVersion: Int, activatedAtMillis: Long) {
        meta = meta.copy(protocolActivated = true)
    }
    override suspend fun metadata() = meta
}
