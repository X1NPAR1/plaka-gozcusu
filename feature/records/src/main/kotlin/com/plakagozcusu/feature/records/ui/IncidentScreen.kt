package com.plakagozcusu.feature.records.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plakagozcusu.core.designsystem.component.PgEmptyState
import com.plakagozcusu.core.designsystem.component.PgPrimaryButton
import com.plakagozcusu.core.designsystem.component.PgSecondaryButton
import com.plakagozcusu.core.designsystem.component.PgTextButton
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.feature.records.IncidentViewModel
import com.plakagozcusu.feature.records.IncidentWindow
import com.plakagozcusu.feature.records.R
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("tr"))
private val HM = DateTimeFormatter.ofPattern("HH:mm")

private enum class Picker { NONE, DATE, FROM, TO }

@Composable
fun IncidentScreen(
    onOpen: (String) -> Unit,
    appVersion: String,
    modifier: Modifier = Modifier,
    viewModel: IncidentViewModel = hiltViewModel(),
) {
    val window by viewModel.window.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME)) { uri ->
            if (uri != null) viewModel.exportAll(resolver, uri, appVersion)
        }
    var picker by remember { mutableStateOf(Picker.NONE) }

    Column(
        modifier.fillMaxSize().padding(horizontal = PgTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
    ) {
        Text(
            stringResource(R.string.incident_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
        ) {
            AssistChip(onClick = { viewModel.presetLastHours(1) }, label = { Text(stringResource(R.string.preset_last_hour)) })
            AssistChip(onClick = { viewModel.presetLastHours(HOURS_PER_DAY) }, label = { Text(stringResource(R.string.preset_last_day)) })
            AssistChip(onClick = { viewModel.presetDay(0) }, label = { Text(stringResource(R.string.preset_today)) })
            AssistChip(onClick = { viewModel.presetDay(1) }, label = { Text(stringResource(R.string.preset_yesterday)) })
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
        ) {
            PgSecondaryButton(DATE.format(window.date), onClick = { picker = Picker.DATE })
            PgSecondaryButton(HM.format(window.from), onClick = { picker = Picker.FROM })
            PgSecondaryButton(HM.format(window.to), onClick = { picker = Picker.TO })
        }
        records?.let { list ->
            Text(stringResource(R.string.incident_count, list.size), style = MaterialTheme.typography.titleMedium)
            if (list.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
                ) {
                    PgSecondaryButton(stringResource(R.string.incident_protect_all), onClick = viewModel::protectAll)
                    PgPrimaryButton(stringResource(R.string.incident_export_all), onClick = { exportLauncher.launch(exportFileName()) })
                }
            }
        }
        ExportStatus(export, viewModel::consumeExport)
        IncidentList(records, viewModel.fileResolver(), onOpen, Modifier.weight(1f))
    }
    WindowPickers(picker, window, onDone = { w ->
        w?.let(viewModel::setWindow)
        picker = Picker.NONE
    })
}

@Composable
private fun IncidentList(
    records: List<com.plakagozcusu.core.model.Observation>?,
    fileOf: (com.plakagozcusu.core.model.Observation) -> java.io.File?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        records == null -> {
            LinearProgressIndicator(modifier.fillMaxWidth())
        }

        records.isEmpty() -> {
            PgEmptyState(stringResource(R.string.records_empty_title), stringResource(R.string.records_empty_body), modifier)
        }

        else -> {
            LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
                items(records, key = { it.id }) { o ->
                    RecordRow(o, fileOf(o), selected = false, onClick = { onOpen(o.id) }, onLongClick = { onOpen(o.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowPickers(
    picker: Picker,
    window: IncidentWindow,
    onDone: (IncidentWindow?) -> Unit,
) {
    when (picker) {
        // Nothing to show.
        Picker.NONE -> {}

        Picker.DATE -> {
            val state =
                rememberDatePickerState(
                    initialSelectedDateMillis =
                        window.date
                            .atStartOfDay()
                            .toInstant(ZoneOffset.UTC)
                            .toEpochMilli(),
                )
            DatePickerDialog(
                onDismissRequest = { onDone(null) },
                confirmButton = {
                    PgTextButton(stringResource(android.R.string.ok), onClick = {
                        val d = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        onDone(d?.let { window.copy(date = it) })
                    })
                },
            ) { DatePicker(state) }
        }

        Picker.FROM, Picker.TO -> {
            val initial = if (picker == Picker.FROM) window.from else window.to
            val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
            DatePickerDialog(
                onDismissRequest = { onDone(null) },
                confirmButton = {
                    PgTextButton(stringResource(android.R.string.ok), onClick = {
                        val t = LocalTime.of(state.hour, state.minute)
                        onDone(if (picker == Picker.FROM) window.copy(from = t) else window.copy(to = t))
                    })
                },
            ) {
                Text(
                    stringResource(if (picker == Picker.FROM) R.string.incident_pick_from else R.string.incident_pick_to),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(PgTheme.spacing.lg),
                )
                TimeInput(state, Modifier.padding(PgTheme.spacing.lg))
            }
        }
    }
}

private const val HOURS_PER_DAY = 24L
