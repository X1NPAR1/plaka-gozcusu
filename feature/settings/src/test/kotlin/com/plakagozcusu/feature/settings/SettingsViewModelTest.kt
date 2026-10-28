package com.plakagozcusu.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.datastore.SecretStore
import com.plakagozcusu.core.datastore.SettingsRepository
import com.plakagozcusu.core.model.AnalysisLoad
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: SettingsRepository
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val store = PreferenceDataStoreFactory.create { File(tmp.root, "s.preferences_pb") }
        repo = SettingsRepository(store)
        vm = SettingsViewModel(repo, SecretStore(store), mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `updates are persisted and validated`() =
        runTest(dispatcher) {
            backgroundScope.launch { vm.settings.collect { } }

            vm.update { it.copy(analysisLoad = AnalysisLoad.HIGH, retentionDays = 0) }

            val s = repo.settings.first { it.analysisLoad == AnalysisLoad.HIGH }
            assertThat(s.retentionDays).isEqualTo(1)
        }
}
