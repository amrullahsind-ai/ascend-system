package com.ascendsystem.app.core.data

import com.ascendsystem.app.core.ai.FakeAiProvider
import com.ascendsystem.app.core.domain.AiProvider
import com.ascendsystem.app.core.domain.QuestRepository
import com.ascendsystem.app.feature.assessment.data.RoomAssessmentDraftRepository
import com.ascendsystem.app.feature.assessment.domain.AssessmentDraftRepository
import com.ascendsystem.app.feature.verification.data.RoomVerificationRepository
import com.ascendsystem.app.feature.verification.domain.VerificationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun questRepository(impl: LocalQuestRepository): QuestRepository
    @Binds @Singleton abstract fun aiProvider(impl: FakeAiProvider): AiProvider
    @Binds @Singleton abstract fun assessmentRepository(impl: RoomAssessmentDraftRepository): AssessmentDraftRepository
    @Binds @Singleton abstract fun verificationRepository(impl: RoomVerificationRepository): VerificationRepository
}
