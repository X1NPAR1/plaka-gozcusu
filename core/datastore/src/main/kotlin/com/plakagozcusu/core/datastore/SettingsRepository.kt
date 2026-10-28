package com.plakagozcusu.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.plakagozcusu.core.model.AnalysisLoad
import com.plakagozcusu.core.model.AppSettings
import com.plakagozcusu.core.model.Calibration
import com.plakagozcusu.core.model.ThemeMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Typed settings over Preferences DataStore. Unknown/corrupt values fall back to defaults. */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) {
        val settings: Flow<AppSettings> = store.data.map(::toSettings)

        suspend fun update(transform: (AppSettings) -> AppSettings) {
            store.edit { prefs ->
                val next = transform(toSettings(prefs)).validated()
                write(prefs, next)
            }
        }

        private fun toSettings(p: Preferences): AppSettings {
            val d = AppSettings()
            return AppSettings(
                onboardingDone = p[K.ONBOARDING] ?: d.onboardingDone,
                analysisLoad = enumOr(p[K.LOAD], d.analysisLoad),
                minDetectionConfidence = p[K.DET_CONF] ?: d.minDetectionConfidence,
                minQualityForHighConfidence = p[K.HIGH_Q] ?: d.minQualityForHighConfidence,
                motionThresholdPxPerSec = p[K.MOTION] ?: d.motionThresholdPxPerSec,
                duplicateWindowSec = p[K.DUP] ?: d.duplicateWindowSec,
                maxStillsPerTrack = p[K.STILLS] ?: d.maxStillsPerTrack,
                saveContextFrames = p[K.CONTEXT] ?: d.saveContextFrames,
                retentionDays = p[K.RETENTION] ?: d.retentionDays,
                storageCapMb = p[K.CAP] ?: d.storageCapMb,
                showOverlays = p[K.OVERLAYS] ?: d.showOverlays,
                themeMode = enumOr(p[K.THEME], d.themeMode),
                calibration = toCalibration(p, d.calibration),
            ).validated()
        }

        private fun toCalibration(
            p: Preferences,
            d: Calibration,
        ) = Calibration(
            zoomRatio = p[K.ZOOM] ?: d.zoomRatio,
            roiPoints = Calibration.decodeRoi(p[K.ROI]),
            focusLocked = p[K.FOCUS_LOCK] ?: d.focusLocked,
            focusX = p[K.FOCUS_X] ?: d.focusX,
            focusY = p[K.FOCUS_Y] ?: d.focusY,
            exposureIndex = p[K.EXPOSURE] ?: d.exposureIndex,
        )

        private fun write(
            p: androidx.datastore.preferences.core.MutablePreferences,
            s: AppSettings,
        ) {
            p[K.ONBOARDING] = s.onboardingDone
            p[K.LOAD] = s.analysisLoad.name
            p[K.DET_CONF] = s.minDetectionConfidence
            p[K.HIGH_Q] = s.minQualityForHighConfidence
            p[K.MOTION] = s.motionThresholdPxPerSec
            p[K.DUP] = s.duplicateWindowSec
            p[K.STILLS] = s.maxStillsPerTrack
            p[K.CONTEXT] = s.saveContextFrames
            p[K.RETENTION] = s.retentionDays
            p[K.CAP] = s.storageCapMb
            p[K.OVERLAYS] = s.showOverlays
            p[K.THEME] = s.themeMode.name
            p[K.ZOOM] = s.calibration.zoomRatio
            p[K.ROI] = Calibration.encodeRoi(s.calibration.roiPoints)
            p[K.FOCUS_LOCK] = s.calibration.focusLocked
            p[K.FOCUS_X] = s.calibration.focusX
            p[K.FOCUS_Y] = s.calibration.focusY
            p[K.EXPOSURE] = s.calibration.exposureIndex
        }

        private inline fun <reified E : Enum<E>> enumOr(
            name: String?,
            default: E,
        ): E = enumValues<E>().firstOrNull { it.name == name } ?: default

        private object K {
            val ONBOARDING = booleanPreferencesKey("onboarding_done")
            val LOAD = stringPreferencesKey("analysis_load")
            val DET_CONF = floatPreferencesKey("min_detection_confidence")
            val HIGH_Q = floatPreferencesKey("min_quality_high_confidence")
            val MOTION = floatPreferencesKey("motion_threshold")
            val DUP = intPreferencesKey("duplicate_window_sec")
            val STILLS = intPreferencesKey("max_stills_per_track")
            val CONTEXT = booleanPreferencesKey("save_context_frames")
            val RETENTION = intPreferencesKey("retention_days")
            val CAP = intPreferencesKey("storage_cap_mb")
            val OVERLAYS = booleanPreferencesKey("show_overlays")
            val THEME = stringPreferencesKey("theme_mode")
            val ZOOM = floatPreferencesKey("cal_zoom")
            val ROI = stringPreferencesKey("cal_roi")
            val FOCUS_LOCK = booleanPreferencesKey("cal_focus_locked")
            val FOCUS_X = floatPreferencesKey("cal_focus_x")
            val FOCUS_Y = floatPreferencesKey("cal_focus_y")
            val EXPOSURE = intPreferencesKey("cal_exposure")
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile("settings") },
        )
}
