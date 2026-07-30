package com.ascendsystem.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascendsystem.app.core.domain.*
import com.ascendsystem.app.service.scheduling.QuestScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuestListUiState(
    val loading: Boolean = true,
    val quests: List<Quest> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class QuestViewModel @Inject constructor(
    private val repository: QuestRepository,
    private val scheduler: QuestScheduler
) : ViewModel() {
    private val _state = MutableStateFlow(QuestListUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = runCatching { repository.quests() }.fold(
            { QuestListUiState(loading = false, quests = it) },
            { QuestListUiState(loading = false, error = it.message ?: "Gagal membaca quest") }
        )
    }

    fun createSquatQuest(title: String, repetitions: Int, delayMinutes: Int) = viewModelScope.launch {
        val quest = Quest(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = "${repetitions.coerceIn(1, 100)} squat valid melalui kamera",
            type = QuestType.SCHEDULED,
            verificationType = VerificationType.CAMERA_POSE,
            targetValue = repetitions.coerceIn(1, 100),
            rewardXp = repetitions.coerceIn(1, 100) * 2,
            scheduledAtMillis = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1) * 60_000L,
            status = QuestStatus.SCHEDULED
        )
        runCatching {
            repository.upsert(quest)
            scheduler.schedule(quest).getOrThrow()
        }.onSuccess { refresh() }
            .onFailure { _state.value = _state.value.copy(error = it.message ?: "Gagal menjadwalkan quest") }
    }

    fun delete(quest: Quest) = viewModelScope.launch {
        runCatching {
            scheduler.cancel(quest.id)
            repository.delete(quest.id)
        }.onSuccess { refresh() }
            .onFailure { _state.value = _state.value.copy(error = it.message ?: "Gagal menghapus quest") }
    }
}
