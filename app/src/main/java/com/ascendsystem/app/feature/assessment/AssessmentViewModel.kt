package com.ascendsystem.app.feature.assessment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascendsystem.app.feature.assessment.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssessmentViewModel @Inject constructor(
    private val repository: AssessmentDraftRepository,
    private val saveDraft: SaveAssessmentDraftUseCase,
    private val validateStep: ValidateAssessmentStepUseCase,
    private val generateProtocol: GenerateInitialProtocolUseCase,
    private val completeAssessment: CompleteAssessmentUseCase,
    private val activateProtocol: ActivateProtocolUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(AssessmentUiState())
    val state: StateFlow<AssessmentUiState> = _state.asStateFlow()
    private val _protocol = MutableStateFlow<PersonalProtocol?>(null)
    val protocol: StateFlow<PersonalProtocol?> = _protocol.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repository.load() ?: AssessmentDraft() }
            .onSuccess { draft ->
                _state.value = AssessmentUiState(draft = draft, loading = false)
                _protocol.value = generateProtocol(draft)
            }.onFailure { error -> _state.update { it.copy(loading = false, error = error.message) } }
    }

    fun dispatch(action: AssessmentAction) {
        val current = _state.value.draft
        val next = when (action) {
            is AssessmentAction.SetDisplayName -> current.copy(basicProfile = current.basicProfile.copy(displayName = action.value))
            is AssessmentAction.ToggleGoal -> current.copy(goals = current.goals.toggle(action.goal))
            is AssessmentAction.SelectStrictness -> current.copy(strictnessMode = action.mode)
            is AssessmentAction.SetEmergencyDuration -> current.copy(emergencySetup = current.emergencySetup.copy(overrideDurationMinutes = action.minutes))
            is AssessmentAction.SetGuardianContact -> current.copy(emergencySetup = current.emergencySetup.copy(guardianContact = action.value))
            AssessmentAction.Back -> current.copy(currentStep = AssessmentStep.entries[(current.currentStep.ordinal - 1).coerceAtLeast(0)])
            AssessmentAction.Next -> {
                val errors = validateStep(current)
                if (errors.isNotEmpty()) {
                    _state.update { it.copy(stepErrors = errors) }
                    return
                }
                current.copy(currentStep = AssessmentStep.entries[(current.currentStep.ordinal + 1).coerceAtMost(AssessmentStep.REVIEW.ordinal)])
            }
            AssessmentAction.Retry -> { load(); return }
        }
        _state.update { it.copy(draft = next, stepErrors = emptyList()) }
        persist(next)
    }

    fun refreshProtocol() { _protocol.value = generateProtocol(_state.value.draft) }

    fun approveProposal(onComplete: (Result<PersonalProtocol>) -> Unit) = viewModelScope.launch {
        val completed = completeAssessment(_state.value.draft)
        if (completed.isFailure) {
            onComplete(completed)
            return@launch
        }
        val proposal = completed.getOrThrow()
        onComplete(activateProtocol(proposal, 3_000L, System.currentTimeMillis()))
    }

    private fun persist(draft: AssessmentDraft) = viewModelScope.launch {
        _state.update { it.copy(saving = true) }
        runCatching { saveDraft(draft) }
            .onFailure { error -> _state.update { it.copy(error = error.message) } }
        _state.update { it.copy(saving = false) }
    }

    private fun <T> Set<T>.toggle(value: T) = if (value in this) this - value else this + value
}
