package com.plakagozcusu.app.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import com.plakagozcusu.feature.monitor.MonitorController
import com.plakagozcusu.feature.monitor.session.MonitoringState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Cross-feature navigation rules that need monitoring state. */
@HiltViewModel
class AppNavViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val monitor: MonitorController,
    ) : ViewModel() {
        /** Calibration needs the camera with a preview; the monitoring service must release it first. */
        fun releaseCameraForCalibration() {
            if (monitor.state.value !is MonitoringState.Idle) monitor.stop(context)
        }
    }
