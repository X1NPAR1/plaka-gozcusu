package com.plakagozcusu.feature.monitor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import com.plakagozcusu.feature.monitor.service.MonitoringService
import com.plakagozcusu.feature.monitor.session.MonitoringEvent
import com.plakagozcusu.feature.monitor.session.MonitoringState
import com.plakagozcusu.feature.monitor.session.MonitoringStateMachine
import com.plakagozcusu.vision.pipeline.TrackView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide monitoring state shared by the service (writer) and the UI (reader).
 * Starting must happen from a visible UI: a camera foreground service cannot be started from the
 * background or at boot (while-in-use restriction, ADR-006).
 */
@Singleton
class MonitorController
    @Inject
    constructor() {
        private val _state = MutableStateFlow<MonitoringState>(MonitoringState.Idle)
        val state: StateFlow<MonitoringState> = _state.asStateFlow()

        private val _overlay = MutableStateFlow<List<TrackView>>(emptyList())
        val overlay: StateFlow<List<TrackView>> = _overlay.asStateFlow()

        internal fun dispatch(event: MonitoringEvent) = _state.update { MonitoringStateMachine.reduce(it, event) }

        internal fun publishOverlay(tracks: List<TrackView>) {
            _overlay.value = tracks
        }

        /**
         * Small preview made from analysis frames. The service binds no CameraX Preview, so
         * monitoring never depends on a visible surface (screen off / app in background).
         */
        private val _preview = MutableStateFlow<Bitmap?>(null)
        val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

        /** True while the dashboard live view is on screen: the service then binds a real Preview. */
        private val _previewWanted = MutableStateFlow(false)
        val previewWanted: StateFlow<Boolean> = _previewWanted.asStateFlow()

        fun setPreviewWanted(wanted: Boolean) {
            _previewWanted.value = wanted
        }

        internal fun publishPreview(bitmap: Bitmap?) {
            _preview.value = bitmap
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP),
            )
        }
    }
