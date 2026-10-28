package com.plakagozcusu.feature.onboarding.calibration

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plakagozcusu.core.camera.CameraState
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.designsystem.component.PgCard
import com.plakagozcusu.core.designsystem.component.PgPrimaryButton
import com.plakagozcusu.core.designsystem.component.PgSecondaryButton
import com.plakagozcusu.core.designsystem.component.PgStatusPill
import com.plakagozcusu.core.designsystem.component.PgTextButton
import com.plakagozcusu.core.designsystem.component.PillTone
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.feature.onboarding.R
import com.plakagozcusu.feature.onboarding.permission.CameraPermissionGate
import com.plakagozcusu.vision.api.geometry.PointF

// Preview and analysis are bound with a 4:3 sensor aspect; upright it is 3:4 in portrait.
private const val SENSOR_ASPECT = 4f / 3f
private const val ROI_FILL_ALPHA = 0.18f

@Composable
fun CalibrationScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CameraPermissionGate(Modifier.safeDrawingPadding()) {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                viewModel.bind(lifecycleOwner)
                onDispose { viewModel.unbind() }
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
            Column(
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(PgTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.md),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.calibration_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    PgTextButton(stringResource(R.string.calibration_done), onClick = { viewModel.save(onDone) })
                }
                CameraStatusLine(state.camera)
                Box(Modifier.fillMaxWidth().aspectRatio(uprightAspect())) {
                    surfaceRequest?.let { request ->
                        // Fit (not the default crop): the Box has the stream aspect, so taps map 1:1 to the scene.
                        CameraXViewfinder(
                            surfaceRequest = request,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    PreviewOverlay(state, onTap = viewModel::onPreviewTap)
                }
                TapModeSelector(state.tapMode, viewModel::setTapMode)
                Text(
                    stringResource(
                        if (state.tapMode ==
                            TapMode.ROI
                        ) {
                            R.string.calibration_hint_roi
                        } else {
                            R.string.calibration_hint_focus
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RoiControls(state.roi, onUndo = viewModel::undoRoi, onClear = viewModel::clearRoi)
                CameraControls(state, viewModel)
                CaptureCard(state, onCapture = viewModel::testCapture)
                EstimateCard(state)
            }
        }
    }
}

@Composable
private fun uprightAspect(): Float =
    if (LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_PORTRAIT
    ) {
        1f / SENSOR_ASPECT
    } else {
        SENSOR_ASPECT
    }

@Composable
private fun CameraStatusLine(camera: CameraState) {
    when (camera) {
        // Ready: the live preview itself is the status.
        is CameraState.Ready -> {}

        CameraState.Idle, CameraState.Initializing -> {
            PgStatusPill(stringResource(R.string.calibration_camera_starting), PillTone.NEUTRAL)
        }

        is CameraState.Interrupted -> {
            PgStatusPill(
                stringResource(
                    if (camera.error is AppError.CameraInUseByAnotherApp) {
                        R.string.calibration_camera_in_use
                    } else {
                        R.string.calibration_camera_starting
                    },
                ),
                PillTone.WARNING,
            )
        }

        is CameraState.Failed -> {
            PgStatusPill(stringResource(R.string.calibration_camera_failed), PillTone.DANGER)
        }
    }
}

@Composable
private fun PreviewOverlay(
    state: CalibrationUiState,
    onTap: (PointF) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val focusColor = PgTheme.status.warning
    val description = stringResource(R.string.calibration_preview_description)
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onTap(PointF(offset.x / size.width, offset.y / size.height))
                }
            },
    ) {
        val points = state.roi.points.map { Offset(it.x * size.width, it.y * size.height) }
        if (points.size >= 2) {
            val path =
                Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    if (state.roi.isComplete) close()
                }
            if (state.roi.isComplete) drawPath(path, accent.copy(alpha = ROI_FILL_ALPHA))
            drawPath(path, accent, style = Stroke(width = 3.dp.toPx()))
        }
        points.forEach { drawCircle(accent, radius = 6.dp.toPx(), center = it) }
        state.focusPoint?.let { p ->
            drawCircle(
                focusColor,
                radius = 22.dp.toPx(),
                center = Offset(p.x * size.width, p.y * size.height),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        if (state.camera !is CameraState.Ready) drawRect(Color.Black.copy(alpha = 0.4f))
    }
}

@Composable
private fun TapModeSelector(
    mode: TapMode,
    onSelect: (TapMode) -> Unit,
) {
    val labels = mapOf(TapMode.ROI to R.string.calibration_mode_roi, TapMode.FOCUS to R.string.calibration_mode_focus)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        TapMode.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = mode == entry,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, TapMode.entries.size),
            ) { Text(stringResource(labels.getValue(entry))) }
        }
    }
}

@Composable
private fun RoiControls(
    roi: RoiEditor,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
    ) {
        PgStatusPill(
            stringResource(R.string.calibration_roi_points, roi.points.size),
            if (roi.isComplete) PillTone.SUCCESS else PillTone.NEUTRAL,
        )
        if (roi.canUndo) {
            PgTextButton(stringResource(R.string.calibration_roi_undo), onClick = onUndo)
            PgTextButton(stringResource(R.string.calibration_roi_clear), onClick = onClear)
        }
    }
}

@Composable
private fun CameraControls(
    state: CalibrationUiState,
    viewModel: CalibrationViewModel,
) {
    val caps = state.capabilities ?: return
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.calibration_zoom, state.zoomRatio), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.zoomRatio,
            onValueChange = viewModel::onZoom,
            valueRange = caps.minZoomRatio..caps.maxZoomRatio.coerceAtLeast(caps.minZoomRatio + MIN_SLIDER_SPAN),
        )
        if (caps.exposureIndexMax > caps.exposureIndexMin) {
            val index = (state.camera as? CameraState.Ready)?.exposureIndex ?: 0
            Text(stringResource(R.string.calibration_exposure, index), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = index.toFloat(),
                onValueChange = { viewModel.onExposure(it.toInt()) },
                valueRange = caps.exposureIndexMin.toFloat()..caps.exposureIndexMax.toFloat(),
                steps = (caps.exposureIndexMax - caps.exposureIndexMin - 1).coerceAtLeast(0),
            )
        }
        PgSecondaryButton(
            stringResource(
                if (state.focusLocked) R.string.calibration_focus_unlock else R.string.calibration_focus_lock,
            ),
            onClick = viewModel::toggleFocusLock,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(
                R.string.calibration_camera_info,
                caps.focalLengthsMm
                    .joinToString {
                        "%.2f".format(
                            it,
                        )
                    }.ifEmpty { stringResource(R.string.unknown_value) },
                caps.maxStillWidth?.let { "$it×${caps.maxStillHeight}" } ?: stringResource(R.string.unknown_value),
                caps.minZoomRatio,
                caps.maxZoomRatio,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MIN_SLIDER_SPAN = 0.01f

@Composable
private fun CaptureCard(
    state: CalibrationUiState,
    onCapture: () -> Unit,
) {
    val context = LocalContext.current
    PgCard(Modifier.fillMaxWidth()) {
        PgPrimaryButton(
            stringResource(if (state.capturing) R.string.calibration_capturing else R.string.calibration_test_capture),
            onClick = onCapture,
            enabled = !state.capturing && state.camera is CameraState.Ready,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.reading) Text(stringResource(R.string.calibration_reading), style = MaterialTheme.typography.bodyMedium)
        state.testRead?.let { r ->
            Text(
                r.plate?.let { stringResource(R.string.calibration_read_ok, it) } ?: stringResource(R.string.calibration_read_none),
                style = MaterialTheme.typography.titleMedium,
                color = if (r.plate != null) PgTheme.status.success else PgTheme.status.warning,
            )
            if (r.rawTexts.isNotEmpty()) {
                Text(
                    stringResource(R.string.calibration_read_raw, r.rawTexts.joinToString(" · ")),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            r.plateWidthPx?.let { Text(stringResource(R.string.calibration_read_width, it), style = MaterialTheme.typography.bodyMedium) }
        }
        state.lastCapture?.let {
            Text(
                stringResource(
                    R.string.calibration_capture_result,
                    it.width,
                    it.height,
                    Formatter.formatShortFileSize(context, it.sizeBytes.toLong()),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.captureError != null) {
            Text(
                stringResource(R.string.calibration_capture_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EstimateCard(state: CalibrationUiState) {
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.calibration_estimate_title), style = MaterialTheme.typography.titleMedium)
        val estimates = state.estimates
        if (estimates.isEmpty()) {
            Text(stringResource(R.string.calibration_estimate_unavailable), style = MaterialTheme.typography.bodyMedium)
        } else {
            estimates.forEach {
                Text(
                    stringResource(R.string.calibration_estimate_row, it.distanceM, it.pixels),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            stringResource(R.string.calibration_estimate_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
