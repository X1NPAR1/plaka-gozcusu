package com.plakagozcusu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plakagozcusu.core.data.ai.AiService
import com.plakagozcusu.core.datastore.SecretStore
import com.plakagozcusu.core.datastore.SettingsRepository
import com.plakagozcusu.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val secrets: SecretStore,
        private val ai: AiService,
    ) : ViewModel() {
        /** Models offered by the provider for the saved key (null = not fetched yet). */
        private val _models = MutableStateFlow<List<String>?>(null)
        val models: StateFlow<List<String>?> = _models.asStateFlow()

        private val _modelsError = MutableStateFlow(false)
        val modelsError: StateFlow<Boolean> = _modelsError.asStateFlow()

        private val _loadingModels = MutableStateFlow(false)
        val loadingModels: StateFlow<Boolean> = _loadingModels.asStateFlow()

        /** Saves the key (if given) and loads the model list with the stored key. */
        fun saveKeyAndLoadModels(newKey: String) {
            viewModelScope.launch {
                if (newKey.isNotBlank()) secrets.setAiKey(newKey)
                val key = secrets.aiKey() ?: return@launch
                _loadingModels.value = true
                _modelsError.value = false
                ai.listModels(key).fold({ list ->
                    _models.value = list
                    // Keep the chosen model if still offered, otherwise pick the first (flash first).
                    val current = repository.settings.first().aiModel
                    if (list.isNotEmpty() && current !in list) repository.update { it.copy(aiModel = list.first()) }
                }, { _modelsError.value = true })
                _loadingModels.value = false
            }
        }

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
