package com.plakagozcusu.feature.records.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.feature.records.R
import java.io.File

private const val MAX_ZOOM = 8f
private const val DOUBLE_TAP_ZOOM = 3f

/** Stretches mid-tones and lifts contrast; helps reading dark or hazy plates. Display only — the file is untouched. */
private val ENHANCE =
    ColorMatrix(
        floatArrayOf(
            1.6f,
            0f,
            0f,
            0f,
            -45f,
            0f,
            1.6f,
            0f,
            0f,
            -45f,
            0f,
            0f,
            1.6f,
            0f,
            -45f,
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    )

/**
 * In-app full-screen viewer for an evidence image: pinch-zoom, pan, double-tap zoom, optional
 * contrast enhancement, closed with the button or Back. Never opens an external app.
 */
@Composable
fun ImageViewerDialog(
    file: File,
    title: String,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var enhance by remember { mutableStateOf(false) }
    val state =
        rememberTransformableState { _, zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = file,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                colorFilter = if (enhance) ColorFilter.colorMatrix(ENHANCE) else null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = DOUBLE_TAP_ZOOM
                                }
                            })
                        }.transformable(state)
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(PgTheme.spacing.sm)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilterChip(selected = enhance, onClick = { enhance = !enhance }, label = { Text(stringResource(R.string.viewer_enhance)) })
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.viewer_close), color = Color.White) }
            }
            Text(
                stringResource(R.string.viewer_hint),
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(PgTheme.spacing.md),
            )
        }
    }
}
