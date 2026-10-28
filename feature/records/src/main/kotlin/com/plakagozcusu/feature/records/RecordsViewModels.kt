package com.plakagozcusu.feature.records

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plakagozcusu.core.common.DispatcherProvider
import com.plakagozcusu.core.common.TimeSource
import com.plakagozcusu.core.data.ObservationRepository
import com.plakagozcusu.core.data.export.EvidenceExporter
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.core.model.ObservationQuery
import com.plakagozcusu.core.model.RecognitionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** Result of an export for a one-shot snackbar. */
sealed interface ExportState {
    data object Idle : ExportState

    data object Running : ExportState

    data class Done(
        val records: Int,
        val integrityFailures: Int,
    ) : ExportState

    data object Failed : ExportState
}

/** Shared export + bulk actions used by records, incident and detail screens. */
class RecordActions
    @Inject
    constructor(
        private val repository: ObservationRepository,
        private val dispatchers: DispatcherProvider,
        private val time: TimeSource,
    ) {
        suspend fun export(
            resolver: ContentResolver,
            target: Uri,
            observations: List<Observation>,
            appVersion: String,
        ): ExportState =
            withContext(dispatchers.io) {
                runCatching {
                    resolver.openOutputStream(target)?.use { out ->
                        EvidenceExporter(
                            repository::resolveFile,
                            appVersion,
                            StampedCopy::render,
                        ).export(observations, time.now().toEpochMilli(), out)
                    } ?: error("no output stream")
                }.fold({ ExportState.Done(it.records, it.integrityFailures) }, { ExportState.Failed })
            }

        suspend fun protect(
            ids: Collection<String>,
            value: Boolean,
        ) = repository.setProtected(ids, value)

        suspend fun delete(ids: Collection<String>) = repository.delete(ids)

        /** Thumbnail source: the vehicle crop, else the scene. */
        fun thumbnail(o: Observation): java.io.File? = (o.vehicleCrop ?: o.scene)?.let(repository::resolveFile)
    }

data class RecordsFilter(
    val plate: String = "",
    val statuses: Set<RecognitionStatus> = emptySet(),
    val starredOnly: Boolean = false,
    val protectedOnly: Boolean = false,
) {
    fun toQuery() =
        ObservationQuery(
            plateFragment = plate.takeIf { it.isNotBlank() },
            statuses = statuses,
            starredOnly = starredOnly,
            protectedOnly = protectedOnly,
        )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordsViewModel
    @Inject
    constructor(
        repository: ObservationRepository,
        private val actions: RecordActions,
    ) : ViewModel() {
        private val _filter = MutableStateFlow(RecordsFilter())
        val filter: StateFlow<RecordsFilter> = _filter.asStateFlow()

        private val _selection = MutableStateFlow<Set<String>>(emptySet())
        val selection: StateFlow<Set<String>> = _selection.asStateFlow()

        private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
        val export: StateFlow<ExportState> = _export.asStateFlow()

        val records: StateFlow<List<Observation>?> =
            _filter
                .flatMapLatest { repository.observe(it.toQuery()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        fun setFilter(transform: (RecordsFilter) -> RecordsFilter) = _filter.update(transform)

        fun fileResolver(): (Observation) -> java.io.File? = actions::thumbnail

        fun toggleSelect(id: String) = _selection.update { if (id in it) it - id else it + id }

        fun clearSelection() = _selection.update { emptySet() }

        fun protectSelected() = bulk { actions.protect(it, true) }

        fun deleteSelected() = bulk { actions.delete(it) }

        fun exportSelected(
            resolver: ContentResolver,
            target: Uri,
            appVersion: String,
        ) {
            val chosen = records.value.orEmpty().filter { it.id in _selection.value }
            viewModelScope.launch {
                _export.value = ExportState.Running
                _export.value = actions.export(resolver, target, chosen, appVersion)
            }
        }

        fun consumeExport() {
            _export.value = ExportState.Idle
        }

        private fun bulk(block: suspend (Set<String>) -> Unit) {
            val ids = _selection.value
            viewModelScope.launch {
                block(ids)
                _selection.value = emptySet()
            }
        }
    }

/** Incident window in local time; presets cover the common "what happened just now/yesterday". */
data class IncidentWindow(
    val date: LocalDate,
    val from: LocalTime,
    val to: LocalTime,
) {
    fun toMillis(zone: ZoneId): Pair<Long, Long> {
        val start = ZonedDateTime.of(date, from, zone).toInstant().toEpochMilli()
        val endDate = if (to <= from) date.plusDays(1) else date
        val end = ZonedDateTime.of(endDate, to, zone).toInstant().toEpochMilli()
        return start to end
    }

    companion object {
        fun lastHours(
            hours: Long,
            now: Instant,
            zone: ZoneId,
        ): IncidentWindow {
            val end = now.atZone(zone)
            val start = end.minusHours(hours)
            return IncidentWindow(start.toLocalDate(), start.toLocalTime().withNano(0), end.toLocalTime().withNano(0))
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncidentViewModel
    @Inject
    constructor(
        repository: ObservationRepository,
        private val actions: RecordActions,
        private val time: TimeSource,
    ) : ViewModel() {
        private val _window = MutableStateFlow(IncidentWindow.lastHours(1, time.now(), time.zone()))
        val window: StateFlow<IncidentWindow> = _window.asStateFlow()

        private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
        val export: StateFlow<ExportState> = _export.asStateFlow()

        /** Oldest first: an incident is reviewed as a timeline. Includes uncertain/unreadable records. */
        val records: StateFlow<List<Observation>?> =
            _window
                .flatMapLatest { w ->
                    val (from, to) = w.toMillis(time.zone())
                    repository.observe(ObservationQuery(fromMillis = from, toMillis = to, newestFirst = false))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        fun fileResolver(): (Observation) -> java.io.File? = actions::thumbnail

        fun setWindow(w: IncidentWindow) {
            _window.value = w
        }

        fun presetLastHours(h: Long) = setWindow(IncidentWindow.lastHours(h, time.now(), time.zone()))

        fun presetDay(daysAgo: Long) {
            val d =
                time
                    .now()
                    .atZone(time.zone())
                    .toLocalDate()
                    .minusDays(daysAgo)
            setWindow(IncidentWindow(d, LocalTime.MIDNIGHT, LocalTime.MAX.withNano(0)))
        }

        fun protectAll() {
            val ids = records.value.orEmpty().map { it.id }
            viewModelScope.launch { actions.protect(ids, true) }
        }

        fun exportAll(
            resolver: ContentResolver,
            target: Uri,
            appVersion: String,
        ) {
            val all = records.value.orEmpty()
            viewModelScope.launch {
                _export.value = ExportState.Running
                _export.value = actions.export(resolver, target, all, appVersion)
            }
        }

        fun consumeExport() {
            _export.value = ExportState.Idle
        }
    }

private const val STOP_MS = 5_000L
