package com.plakagozcusu.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plakagozcusu.core.designsystem.component.PgCard
import com.plakagozcusu.core.designsystem.component.PgSecondaryButton
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.core.model.AnalysisLoad
import com.plakagozcusu.core.model.AppSettings
import com.plakagozcusu.core.model.ThemeMode
import kotlin.math.roundToInt

private const val RETENTION_MAX_DAYS = 180f
private const val CAP_MAX_GB = 32f
private const val MB_PER_GB = 1024
private const val DUP_MAX_SEC = 600f

@Composable
fun SettingsScreen(
    onRecalibrate: () -> Unit,
    appVersion: String,
    modelVersions: String,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PgTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.md),
    ) {
        LoadCard(settings, viewModel)
        StorageCard(settings, viewModel)
        AiCard(settings, viewModel)
        TrainingCard(viewModel)
        RecognitionCard(settings, viewModel, onRecalibrate)
        PrivacyCard()
        AboutCard(appVersion, modelVersions)
    }
}

private fun AnalysisLoad.label(): Int =
    when (this) {
        AnalysisLoad.LOW -> R.string.load_low
        AnalysisLoad.BALANCED -> R.string.load_balanced
        AnalysisLoad.HIGH -> R.string.load_high
    }

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onDone: (Float) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Slider(value = value.coerceIn(range), onValueChange = onDone, valueRange = range)
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LoadCard(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_load), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
            AnalysisLoad.entries.forEach { load ->
                FilterChip(
                    selected = settings.analysisLoad == load,
                    onClick = { viewModel.update { it.copy(analysisLoad = load) } },
                    label = { Text(stringResource(load.label())) },
                )
            }
        }
        Text(
            stringResource(R.string.settings_load_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageCard(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleMedium)
        LabeledSlider(
            stringResource(R.string.settings_retention, settings.retentionDays),
            settings.retentionDays.toFloat(),
            1f..RETENTION_MAX_DAYS,
        ) { v ->
            viewModel.update { it.copy(retentionDays = v.roundToInt()) }
        }
        LabeledSlider(
            stringResource(R.string.settings_cap, settings.storageCapMb / MB_PER_GB.toFloat()),
            settings.storageCapMb / MB_PER_GB.toFloat(),
            1f..CAP_MAX_GB,
        ) { v -> viewModel.update { it.copy(storageCapMb = (v * MB_PER_GB).roundToInt()) } }
        Text(
            stringResource(R.string.settings_protect_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecognitionCard(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onRecalibrate: () -> Unit,
) {
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_recognition), style = MaterialTheme.typography.titleMedium)
        LabeledSlider(
            stringResource(R.string.settings_duplicate, settings.duplicateWindowSec),
            settings.duplicateWindowSec.toFloat(),
            0f..DUP_MAX_SEC,
        ) { v ->
            viewModel.update { it.copy(duplicateWindowSec = v.roundToInt()) }
        }
        ToggleRow(
            stringResource(R.string.settings_overlays),
            settings.showOverlays,
        ) { v -> viewModel.update { it.copy(showOverlays = v) } }
        ToggleRow(stringResource(R.string.settings_dark), settings.themeMode != ThemeMode.LIGHT) { v ->
            viewModel.update { it.copy(themeMode = if (v) ThemeMode.DARK else ThemeMode.LIGHT) }
        }
        PgSecondaryButton(stringResource(R.string.settings_recalibrate), onRecalibrate)
    }
}

@Composable
private fun PrivacyCard() {
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AboutCard(
    appVersion: String,
    modelVersions: String,
) {
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_versions, appVersion, modelVersions), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TrainingCard(viewModel: SettingsViewModel) {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val done by viewModel.trainingExport.collectAsStateWithLifecycle()
    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .CreateDocument("application/zip"),
        ) { uri -> if (uri != null) viewModel.exportTrainingData(resolver, uri) }
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.training_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.training_body), style = MaterialTheme.typography.bodyMedium)
        PgSecondaryButton(stringResource(R.string.training_export), { launcher.launch("plaka-egitim-verisi.zip") })
        done?.let { Text(stringResource(R.string.training_done, it), style = MaterialTheme.typography.bodySmall) }
    }
}
