package com.plakagozcusu.feature.records

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plakagozcusu.core.data.ObservationRepository
import com.plakagozcusu.core.model.EvidenceFile
import com.plakagozcusu.core.model.Observation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DetailViewModel
    @Inject
    constructor(
        private val ai: com.plakagozcusu.core.data.ai.AiService,
        savedState: SavedStateHandle,
        private val repository: ObservationRepository,
        private val actions: RecordActions,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState[ARG_ID]) { "observation id missing" }

        val observation: StateFlow<Observation?> =
            repository.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        /** null = not checked yet; otherwise kind → hash matches. */
        private val _integrity = MutableStateFlow<Map<String, Boolean>?>(null)
        val integrity: StateFlow<Map<String, Boolean>?> = _integrity.asStateFlow()

        private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
        val export: StateFlow<ExportState> = _export.asStateFlow()

        private val _deleted = MutableStateFlow(false)
        val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

        fun fileOf(e: EvidenceFile): File = repository.resolveFile(e)

        fun verify() {
            val o = observation.value ?: return
            viewModelScope.launch { _integrity.value = repository.verifyEvidence(o) }
        }

        private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
        val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

        /** Explicit user request: runs now (does not need the background toggle), with visible feedback. */
        fun askAi() =
            mutate {
                _aiState.value = AiUiState.Running
                _aiState.value = AiUiState.Finished(ai.check(it.id, requireEnabled = false))
            }

        /** Accepting the AI suggestion is a user decision: stored as a user correction/verification. */
        fun acceptAi() = mutate { o -> o.ai?.plateText?.let { repository.correctPlate(o.id, it) } }

        fun toggleProtected() = mutate { repository.setProtected(listOf(it.id), !it.isProtected) }

        fun toggleStar() = mutate { repository.setStarred(it.id, !it.isStarred) }

        fun saveNote(note: String) = mutate { repository.setNote(it.id, note) }

        fun correctPlate(text: String) = mutate { repository.correctPlate(it.id, text) }

        fun delete() =
            mutate {
                actions.delete(listOf(it.id))
                _deleted.value = true
            }

        fun export(
            resolver: ContentResolver,
            target: Uri,
            appVersion: String,
        ) = mutate {
            _export.value = ExportState.Running
            _export.value = actions.export(resolver, target, listOf(it), appVersion)
        }

        fun consumeExport() {
            _export.value = ExportState.Idle
        }

        private fun mutate(block: suspend (Observation) -> Unit) {
            val o = observation.value ?: return
            viewModelScope.launch { block(o) }
        }

        companion object {
            const val ARG_ID = "id"
            private const val STOP_MS = 5_000L
        }
    }

sealed interface AiUiState {
    data object Idle : AiUiState

    data object Running : AiUiState

    data class Finished(
        val outcome: com.plakagozcusu.core.data.ai.AiCheckOutcome,
    ) : AiUiState
}
