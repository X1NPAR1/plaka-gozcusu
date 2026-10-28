package com.plakagozcusu.feature.records.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.plakagozcusu.core.designsystem.component.PgTextButton
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.feature.records.ExportState
import com.plakagozcusu.feature.records.R
import com.plakagozcusu.feature.records.RecordsViewModel
import java.io.File

@Composable
fun RecordsScreen(
    onOpen: (String) -> Unit,
    appVersion: String,
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME)) { uri ->
            if (uri != null) viewModel.exportSelected(resolver, uri, appVersion)
        }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(horizontal = PgTheme.spacing.lg)) {
        OutlinedTextField(
            value = filter.plate,
            onValueChange = { v -> viewModel.setFilter { it.copy(plate = v) } },
            label = { Text(stringResource(R.string.records_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterRow(filter.statuses, filter.starredOnly, filter.protectedOnly, viewModel)
        if (selection.isNotEmpty()) {
            SelectionBar(
                count = selection.size,
                onProtect = viewModel::protectSelected,
                onExport = { exportLauncher.launch(exportFileName()) },
                onDelete = { confirmDelete = true },
                onCancel = viewModel::clearSelection,
            )
        }
        ExportStatus(export, viewModel::consumeExport)
        RecordList(records, selection, viewModel.fileResolver(), onOpen, viewModel::toggleSelect, Modifier.weight(1f))
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            onConfirm = {
                confirmDelete = false
                viewModel.deleteSelected()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun FilterRow(
    statuses: Set<RecognitionStatus>,
    starred: Boolean,
    protectedOnly: Boolean,
    viewModel: RecordsViewModel,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = PgTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
    ) {
        StatusChip(R.string.filter_review, RecognitionStatus.NEEDS_REVIEW in statuses) {
            viewModel.setFilter { it.copy(statuses = it.statuses.toggle(RecognitionStatus.NEEDS_REVIEW)) }
        }
        StatusChip(R.string.filter_unreadable, RecognitionStatus.UNREADABLE in statuses) {
            viewModel.setFilter { it.copy(statuses = it.statuses.toggle(RecognitionStatus.UNREADABLE)) }
        }
        StatusChip(R.string.filter_starred, starred) { viewModel.setFilter { it.copy(starredOnly = !it.starredOnly) } }
        StatusChip(R.string.filter_protected, protectedOnly) { viewModel.setFilter { it.copy(protectedOnly = !it.protectedOnly) } }
    }
}

private fun <T> Set<T>.toggle(v: T) = if (v in this) this - v else this + v

@Composable
private fun StatusChip(
    label: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

@Composable
private fun SelectionBar(
    count: Int,
    onProtect: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.selected_count, count),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(PgTheme.spacing.md),
        )
        PgTextButton(stringResource(R.string.action_protect), onProtect)
        PgTextButton(stringResource(R.string.action_export), onExport)
        PgTextButton(stringResource(R.string.action_delete), onDelete)
        PgTextButton(stringResource(R.string.action_cancel), onCancel)
    }
}

@Composable
private fun RecordList(
    records: List<Observation>?,
    selection: Set<String>,
    fileOf: (Observation) -> File?,
    onOpen: (String) -> Unit,
    onToggle: (String) -> Unit,
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
                records.groupBy { formatDay(it) }.forEach { (day, dayRecords) ->
                    item(key = "day-$day") { DayHeader(day) }
                    items(dayRecords, key = { it.id }) { o ->
                        RecordRow(
                            observation = o,
                            thumbnail = fileOf(o),
                            selected = o.id in selection,
                            onClick = { if (selection.isEmpty()) onOpen(o.id) else onToggle(o.id) },
                            onLongClick = { onToggle(o.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExportStatus(
    state: ExportState,
    onConsumed: () -> Unit,
) {
    val text =
        when (state) {
            ExportState.Idle -> {
                return
            }

            ExportState.Running -> {
                stringResource(R.string.export_running)
            }

            is ExportState.Done -> {
                if (state.integrityFailures == 0) {
                    stringResource(R.string.export_done, state.records)
                } else {
                    stringResource(R.string.export_done_with_failures, state.records, state.integrityFailures)
                }
            }

            ExportState.Failed -> {
                stringResource(R.string.export_failed)
            }
        }
    Row(Modifier.fillMaxWidth().padding(vertical = PgTheme.spacing.xs)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (state !is ExportState.Running) PgTextButton(stringResource(android.R.string.ok), onConsumed)
    }
}

@Composable
fun ConfirmDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete_title)) },
        text = { Text(stringResource(R.string.confirm_delete_body)) },
        confirmButton = { PgTextButton(stringResource(R.string.action_delete), onConfirm) },
        dismissButton = { PgTextButton(stringResource(R.string.action_cancel), onDismiss) },
    )
}

const val ZIP_MIME = "application/zip"

fun exportFileName(): String = "plaka-gozcusu-kanit-${System.currentTimeMillis()}.zip"
