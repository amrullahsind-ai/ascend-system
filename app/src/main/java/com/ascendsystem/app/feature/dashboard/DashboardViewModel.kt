package com.ascendsystem.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascendsystem.app.core.domain.Quest
import com.ascendsystem.app.core.domain.QuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(val loading: Boolean = true, val quests: List<Quest> = emptyList(), val error: String? = null)

@HiltViewModel
class DashboardViewModel @Inject constructor(private val repository: QuestRepository) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state = _state.asStateFlow()
    init { refresh() }
    fun refresh() = viewModelScope.launch {
        _state.value = runCatching { repository.quests() }.fold(
            { DashboardUiState(loading = false, quests = it) },
            { DashboardUiState(loading = false, error = it.message ?: "Unable to load system data") }
        )
    }
}
