package com.plakagozcusu.feature.monitor.ui

import android.Manifest
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.designsystem.component.PgCard
import com.plakagozcusu.core.designsystem.component.PgPrimaryButton
import com.plakagozcusu.core.designsystem.component.PgSecondaryButton
import com.plakagozcusu.core.designsystem.component.PgStatusPill
import com.plakagozcusu.core.designsystem.component.PillTone
import com.plakagozcusu.core.designsystem.component.StatusBadge
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.core.designsystem.theme.PlateTextStyle
import com.plakagozcusu.feature.monitor.R
import com.plakagozcusu.feature.monitor.service.MonitoringNotifications
import com.plakagozcusu.feature.monitor.session.MonitoringState

private const val PREVIEW_ASPECT = 3f / 4f

@Composable
fun DashboardScreen(
    onOpenRecord: (String) -> Unit,
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val used by viewModel.usedBytes.collectAsStateWithLifecycle()
    var dimmed by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.start() }
    val active = state !is MonitoringState.Idle && state !is MonitoringState.Failed

    if (dimmed && active) {
        DimScreen(state, onWake = { dimmed = false })
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PgTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.md),
    ) {
        Text(
            stringResource(R.string.dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        StatusLine(state)
        if (active) LiveView(viewModel)
        Row(horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
            if (active) {
                PgPrimaryButton(stringResource(R.string.monitor_stop), viewModel::stop)
                PgSecondaryButton(stringResource(R.string.monitor_dim), { dimmed = true })
            } else {
                PgPrimaryButton(stringResource(R.string.monitor_start), {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.start()
                    }
                })
                PgSecondaryButton(stringResource(R.string.monitor_calibrate), onCalibrate)
            }
        }
        TodayCard(today, used, viewModel.freeBytes)
        LatestCard(latest, viewModel, onOpenRecord)
    }
}

@Composable
private fun StatusLine(state: MonitoringState) {
    when (state) {
        MonitoringState.Idle -> {
            PgStatusPill(stringResource(R.string.status_idle), PillTone.NEUTRAL)
        }

        MonitoringState.InitializingCamera, MonitoringState.LoadingModels -> {
            PgStatusPill(stringResource(R.string.notif_starting), PillTone.NEUTRAL)
        }

        is MonitoringState.Running -> {
            PgStatusPill(
                stringResource(if (state.degraded != null) R.string.status_running_degraded else R.string.status_running),
                if (state.degraded != null) PillTone.WARNING else PillTone.LIVE,
                pulse = true,
            )
        }

        is MonitoringState.Paused -> {
            PgStatusPill(stringResource(MonitoringNotifications.pauseText(state.reason)), PillTone.WARNING)
        }

        MonitoringState.Stopping -> {
            PgStatusPill(stringResource(R.string.status_stopping), PillTone.NEUTRAL)
        }

        is MonitoringState.Failed -> {
            PgStatusPill(
                stringResource(if (state.error is AppError.CameraInUseByAnotherApp) R.string.pause_camera else R.string.notif_failed),
                PillTone.DANGER,
            )
        }
    }
}

@Composable
private fun LiveView(viewModel: DashboardViewModel) {
    val frame by viewModel.preview.collectAsStateWithLifecycle()
    val tracks by viewModel.overlay.collectAsStateWithLifecycle()
    val show by viewModel.showOverlays.collectAsStateWithLifecycle()
    val live = PgTheme.status.live
    val muted = PgTheme.status.neutral
    val description = stringResource(R.string.live_description)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(frame?.let { it.width.toFloat() / it.height } ?: PREVIEW_ASPECT)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black)
            .semantics { contentDescription = description },
    ) {
        frame?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (show) {
            Canvas(Modifier.fillMaxSize()) {
                tracks.forEach { t ->
                    val c = if (t.moving && t.inRoi) live else muted
                    drawRect(
                        c,
                        topLeft = Offset(t.box.left * size.width, t.box.top * size.height),
                        size = Size(t.box.width * size.width, t.box.height * size.height),
                        style = Stroke(width = if (t.confirmed) 3.dp.toPx() else 1.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayCard(
    today: TodayCounts?,
    used: Long?,
    free: Long,
) {
    val context = LocalContext.current
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleMedium)
        if (today == null) {
            Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.today_counts, today.vehicles, today.read, today.uncertain, today.unreadable))
        }
        used?.let {
            Text(
                stringResource(
                    R.string.storage_line,
                    Formatter.formatShortFileSize(context, it),
                    Formatter.formatShortFileSize(context, free),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LatestCard(
    latest: List<com.plakagozcusu.core.model.Observation>?,
    viewModel: DashboardViewModel,
    onOpen: (String) -> Unit,
) {
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.latest_title), style = MaterialTheme.typography.titleMedium)
        when {
            latest == null -> {
                Text(stringResource(R.string.loading))
            }

            latest.isEmpty() -> {
                Text(stringResource(R.string.latest_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> {
                latest.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(o.id) }.padding(vertical = PgTheme.spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = viewModel.thumbnail(o),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp, 44.dp).clip(MaterialTheme.shapes.small),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                o.plateText ?: stringResource(R.string.plate_unknown),
                                style =
                                    if (o.plateText !=
                                        null
                                    ) {
                                        PlateTextStyle
                                    } else {
                                        MaterialTheme.typography.bodyLarge
                                    },
                            )
                            StatusBadge(o.recognitionStatus)
                        }
                    }
                }
            }
        }
    }
}

/** Black screen with a minimal status line; processing continues, burn-in and power are reduced. */
@Composable
private fun DimScreen(
    state: MonitoringState,
    onWake: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onWake), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.padding(PgTheme.spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
            StatusLine(state)
            Text(stringResource(R.string.dim_hint), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}
