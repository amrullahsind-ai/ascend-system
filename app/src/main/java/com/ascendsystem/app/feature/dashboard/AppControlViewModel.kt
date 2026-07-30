package com.ascendsystem.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import com.ascendsystem.app.core.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppControlUiState(
    val loading: Boolean = true,
    val restrictions: List<AppRestriction> = emptyList(),
    val overrideUntilMillis: Long = 0L,
    val installedApps: List<InstalledAppChoice> = emptyList(),
    val message: String? = null
)

data class InstalledAppChoice(val packageName: String, val displayName: String)

@HiltViewModel
class AppControlViewModel @Inject constructor(
    private val restrictions: RestrictionRepository,
    private val overrides: OverrideRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(AppControlUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching {
            restrictions.restrictions() to overrides.activeUntilMillis()
        }.onSuccess { (apps, until) ->
            val configured = apps.map { it.packageName }.toSet()
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val installed = context.packageManager.queryIntentActivities(launcherIntent, 0)
                .map {
                    InstalledAppChoice(
                        it.activityInfo.packageName,
                        it.loadLabel(context.packageManager).toString()
                    )
                }
                .distinctBy { it.packageName }
                .filter { it.packageName != context.packageName && it.packageName !in configured }
                .sortedBy { it.displayName.lowercase() }
            _state.value = AppControlUiState(false, apps, until, installed)
        }.onFailure {
            _state.value = _state.value.copy(loading = false, message = it.message)
        }
    }

    fun add(packageName: String, displayName: String, essential: Boolean) = viewModelScope.launch {
        val normalized = packageName.trim()
        if (normalized.isBlank() || !normalized.contains('.')) {
            _state.value = _state.value.copy(message = "Masukkan package Android yang valid, contoh com.instagram.android")
            return@launch
        }
        runCatching {
            restrictions.upsert(
                AppRestriction(
                    packageName = normalized,
                    displayName = displayName.trim().ifBlank { normalized.substringAfterLast('.') },
                    dailyLimitMinutes = null,
                    sessionLimitMinutes = null,
                    isEssential = essential
                )
            )
        }.onSuccess { refresh() }.onFailure { _state.value = _state.value.copy(message = it.message) }
    }

    fun delete(packageName: String) = viewModelScope.launch {
        runCatching { restrictions.delete(packageName) }
            .onSuccess { refresh() }
            .onFailure { _state.value = _state.value.copy(message = it.message) }
    }

    fun activateOverride(note: String, minutes: Int, onDone: () -> Unit) = viewModelScope.launch {
        runCatching {
            overrides.activate(
                OverrideRequest("USER_EMERGENCY", note.trim(), minutes),
                activeQuestId = null,
                nowMillis = System.currentTimeMillis()
            )
        }.onSuccess { refresh(); onDone() }
            .onFailure { _state.value = _state.value.copy(message = it.message) }
    }
}
