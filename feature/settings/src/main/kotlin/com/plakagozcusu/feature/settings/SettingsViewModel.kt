package com.plakagozcusu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plakagozcusu.core.datastore.SecretStore
import com.plakagozcusu.core.datastore.SettingsRepository
import com.plakagozcusu.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val secrets: SecretStore,
    ) : ViewModel() {
        val hasAiKey: StateFlow<Boolean> = secrets.hasAiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), false)

        /** Saving an empty key removes it and switches the AI check off. */
        fun saveAiKey(key: String) {
            viewModelScope.launch {
                secrets.setAiKey(key)
                if (key.isBlank()) repository.update { it.copy(aiEnabled = false) }
            }
        }

        val settings: StateFlow<AppSettings?> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        fun update(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch { repository.update(transform) }
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }
