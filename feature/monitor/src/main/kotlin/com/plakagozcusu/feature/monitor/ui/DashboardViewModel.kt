package com.plakagozcusu.feature.monitor.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plakagozcusu.core.camera.CameraController
import com.plakagozcusu.core.common.TimeSource
import com.plakagozcusu.core.data.ObservationRepository
import com.plakagozcusu.core.datastore.SettingsRepository
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.feature.monitor.MonitorController
import com.plakagozcusu.feature.monitor.session.MonitoringState
import com.plakagozcusu.vision.pipeline.TrackView
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import javax.inject.Inject

/** Real counts only; null while loading so the UI never shows a made-up zero. */
data class TodayCounts(
    val vehicles: Int,
    val read: Int,
    val uncertain: Int,
    val unreadable: Int,
)

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val app: Context,
        private val controller: MonitorController,
        private val repository: ObservationRepository,
        settings: SettingsRepository,
        time: TimeSource,
        camera: CameraController,
    ) : ViewModel() {
        val state: StateFlow<MonitoringState> = controller.state
        val overlay: StateFlow<List<TrackView>> = controller.overlay
        val preview = controller.preview
        val surfaceRequest = camera.surfaceRequest

        fun setPreviewWanted(wanted: Boolean) = controller.setPreviewWanted(wanted)

        val showOverlays =
            settings.settings.map { it.showOverlays }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                true,
            )

        private val startOfDay =
            time
                .now()
                .atZone(
                    time.zone(),
                ).toLocalDate()
                .atTime(LocalTime.MIDNIGHT)
                .atZone(time.zone())
        val today: StateFlow<TodayCounts?> =
            repository
                .observeStatusCounts(
                    startOfDay.toInstant().toEpochMilli(),
                    startOfDay.plusDays(1).toInstant().toEpochMilli(),
                ).map { c ->
                    TodayCounts(
                        vehicles = c.values.sum(),
                        read = (c[RecognitionStatus.HIGH_CONFIDENCE] ?: 0) + (c[RecognitionStatus.USER_VERIFIED] ?: 0),
                        uncertain = c[RecognitionStatus.NEEDS_REVIEW] ?: 0,
                        unreadable =
                            (c[RecognitionStatus.UNREADABLE] ?: 0) + (c[RecognitionStatus.NO_PLATE_VISIBLE] ?: 0),
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        val latest: StateFlow<List<Observation>?> =
            repository
                .observeLatest(
                    LATEST,
                ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        private val _usedBytes = MutableStateFlow<Long?>(null)
        val usedBytes: StateFlow<Long?> = _usedBytes.asStateFlow()

        // Informational display only; nothing is allocated based on this number.
        @Suppress("UsableSpace")
        val freeBytes: Long get() = app.filesDir.usableSpace

        init {
            viewModelScope.launch { _usedBytes.value = repository.usedBytes() }
        }

        fun thumbnail(o: Observation): File? = (o.vehicleCrop ?: o.scene)?.let(repository::resolveFile)

        fun start() = controller.start(app)

        fun stop() = controller.stop(app)

        private companion object {
            const val STOP_MS = 5_000L
            const val LATEST = 5
        }
    }
