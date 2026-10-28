package com.plakagozcusu.feature.records.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.plakagozcusu.core.designsystem.component.PgCard
import com.plakagozcusu.core.designsystem.component.PgSecondaryButton
import com.plakagozcusu.core.designsystem.component.PgTextButton
import com.plakagozcusu.core.designsystem.component.StatusBadge
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.core.designsystem.theme.PlateTextStyle
import com.plakagozcusu.core.model.EvidenceFile
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.feature.records.DetailViewModel
import com.plakagozcusu.feature.records.R

private const val MAX_ZOOM = 6f
private const val PERCENT = 100f
private const val SCENE_ASPECT = 4f / 3f

private enum class Dialog { NONE, CORRECT, NOTE, DELETE }

@Composable
fun DetailScreen(
    onDeleted: () -> Unit,
    appVersion: String,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val o by viewModel.observation.collectAsStateWithLifecycle()
    val integrity by viewModel.integrity.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { if (deleted) onDeleted() }
    val resolver = LocalContext.current.contentResolver
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME)) { uri ->
            if (uri != null) viewModel.export(resolver, uri, appVersion)
        }
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    val observation = o ?: return

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PgTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.md),
    ) {
        ZoomableImage(observation.scene, viewModel::fileOf, stringResource(R.string.detail_scene))
        Row(horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
            CropImage(observation.vehicleCrop, viewModel::fileOf, stringResource(R.string.detail_vehicle), Modifier.weight(1f))
            CropImage(observation.plateCrop, viewModel::fileOf, stringResource(R.string.detail_plate), Modifier.weight(1f))
        }
        InfoCard(observation, integrity)
        ExportStatus(export, viewModel::consumeExport)
        ActionRows(observation, viewModel, onDialog = { dialog = it }, onExport = { exportLauncher.launch(exportFileName()) })
    }
    when (dialog) {
        // Nothing to show.
        Dialog.NONE -> {}

        Dialog.CORRECT -> {
            TextDialog(
                R.string.detail_correct,
                observation.plateText.orEmpty(),
                R.string.detail_correct_hint,
                { viewModel.correctPlate(it) },
            ) {
                dialog = Dialog.NONE
            }
        }

        Dialog.NOTE -> {
            TextDialog(R.string.detail_note, observation.userNote.orEmpty(), R.string.detail_note, viewModel::saveNote) {
                dialog =
                    Dialog.NONE
            }
        }

        Dialog.DELETE -> {
            ConfirmDeleteDialog(onConfirm = {
                dialog = Dialog.NONE
                viewModel.delete()
            }, onDismiss = { dialog = Dialog.NONE })
        }
    }
}

@Composable
private fun InfoCard(
    o: Observation,
    integrity: Map<String, Boolean>?,
) {
    PgCard(Modifier.fillMaxWidth()) {
        Text(o.plateText ?: stringResource(R.string.plate_unknown), style = PlateTextStyle, modifier = Modifier.semantics { heading() })
        StatusBadge(o.recognitionStatus, Modifier.padding(vertical = PgTheme.spacing.xs))
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text(stringResource(R.string.detail_disclaimer), style = MaterialTheme.typography.bodySmall, color = muted)
        Text(stringResource(R.string.detail_time, formatFull(o.bestFrameAt, o.zoneId)))
        Text(stringResource(R.string.detail_seen, formatFull(o.firstSeenAt, o.zoneId), formatFull(o.lastSeenAt, o.zoneId)), color = muted)
        Text(stringResource(R.string.detail_confidence, o.plateConfidence * PERCENT, o.qualityScore * PERCENT), color = muted)
        o.rawOcrText?.let { Text(stringResource(R.string.detail_raw_ocr, it), color = muted) }
        o.originalPlateText?.let { Text(stringResource(R.string.detail_original, it), color = muted) }
        o.linkedObservationId?.let { Text(stringResource(R.string.detail_linked), color = muted) }
        o.userNote?.let { Text(it) }
        integrity?.let { map ->
            val bad = map.filterValues { !it }.keys
            Text(
                if (bad.isEmpty()) {
                    stringResource(
                        R.string.detail_verify_ok,
                    )
                } else {
                    stringResource(R.string.detail_verify_bad, bad.joinToString())
                },
                color = if (bad.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    file: EvidenceFile?,
    resolve: (EvidenceFile) -> java.io.File,
    description: String,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val state =
        rememberTransformableState { _, zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
            offsetX += pan.x
            offsetY += pan.y
        }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(SCENE_ASPECT)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .transformable(state),
    ) {
        EvidenceImage(file, resolve, description, ContentScale.Fit) {
            graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
        }
    }
}

@Composable
private fun CropImage(
    file: EvidenceFile?,
    resolve: (EvidenceFile) -> java.io.File,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(description, style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(
                    SCENE_ASPECT,
                ).clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            EvidenceImage(file, resolve, description, ContentScale.Fit) { this }
        }
    }
}

@Composable
private fun EvidenceImage(
    file: EvidenceFile?,
    resolve: (EvidenceFile) -> java.io.File,
    description: String,
    scale: ContentScale,
    transform: Modifier.() -> Modifier,
) {
    val f = file?.let(resolve)
    if (f == null || !f.isFile) {
        Text(stringResource(R.string.detail_missing_file), modifier = Modifier.padding(PgTheme.spacing.md))
        return
    }
    AsyncImage(model = f, contentDescription = description, contentScale = scale, modifier = Modifier.fillMaxSize().transform())
}

@Composable
private fun TextDialog(
    title: Int,
    initial: String,
    hint: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                text,
                { text = it },
                label = { Text(stringResource(hint)) },
                singleLine = title != R.string.detail_note,
            )
        },
        confirmButton = {
            PgTextButton(stringResource(R.string.action_save), {
                onSave(text)
                onDismiss()
            })
        },
        dismissButton = { PgTextButton(stringResource(R.string.action_cancel), onDismiss) },
    )
}

@Composable
private fun ActionRows(
    observation: Observation,
    viewModel: DetailViewModel,
    onDialog: (Dialog) -> Unit,
    onExport: () -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
        PgSecondaryButton(
            stringResource(if (observation.isProtected) R.string.action_unprotect else R.string.action_protect),
            viewModel::toggleProtected,
        )
        PgSecondaryButton(
            stringResource(if (observation.isStarred) R.string.action_unstar else R.string.action_star),
            viewModel::toggleStar,
        )
        PgSecondaryButton(stringResource(R.string.detail_correct), { onDialog(Dialog.CORRECT) })
        PgSecondaryButton(stringResource(R.string.detail_note), { onDialog(Dialog.NOTE) })
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
        PgSecondaryButton(stringResource(R.string.detail_verify), viewModel::verify)
        PgSecondaryButton(stringResource(R.string.action_export), onExport)
        PgTextButton(stringResource(R.string.action_delete), { onDialog(Dialog.DELETE) })
    }
}
