package com.ascendsystem.app.feature.assessment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascendsystem.app.feature.assessment.domain.AssessmentDraftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartFlow { INITIALIZATION, CALIBRATION, ASSESSMENT, PROTOCOL_REVIEW, DASHBOARD }

@HiltViewModel
class StartupViewModel @Inject constructor(repository: AssessmentDraftRepository) : ViewModel() {
    private val _flow = MutableStateFlow<StartFlow?>(null)
    val flow = _flow.asStateFlow()

    init {
        viewModelScope.launch {
            val metadata = repository.metadata()
            _flow.value = when {
                metadata.protocolActivated -> StartFlow.DASHBOARD
                metadata.assessmentCompleted -> StartFlow.PROTOCOL_REVIEW
                metadata.migratedFromV1 -> StartFlow.CALIBRATION
                repository.load() != null -> StartFlow.ASSESSMENT
                else -> StartFlow.INITIALIZATION
            }
        }
    }
}
