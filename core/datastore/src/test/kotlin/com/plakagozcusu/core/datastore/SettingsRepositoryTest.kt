package com.plakagozcusu.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.model.AnalysisLoad
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repository() =
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") },
        )

    @Test
    fun `defaults are returned before anything is written`() =
        runTest(UnconfinedTestDispatcher()) {
            val s = repository().settings.first()

            assertThat(s.onboardingDone).isFalse()
            assertThat(s.retentionDays).isEqualTo(30)
        }

    @Test
    fun `updates persist and are validated`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repository()

            repo.update {
                it.copy(
                    onboardingDone = true,
                    analysisLoad = AnalysisLoad.LOW,
                    retentionDays = 9_999,
                    calibration = it.calibration.copy(zoomRatio = 2.5f, roiPoints = listOf(0.1f to 0.5f)),
                )
            }

            val s = repo.settings.first()
            assertThat(s.onboardingDone).isTrue()
            assertThat(s.analysisLoad).isEqualTo(AnalysisLoad.LOW)
            assertThat(s.retentionDays).isEqualTo(365)
            assertThat(s.calibration.zoomRatio).isEqualTo(2.5f)
            assertThat(s.calibration.roiPoints).containsExactly(0.1f to 0.5f)
        }
}
