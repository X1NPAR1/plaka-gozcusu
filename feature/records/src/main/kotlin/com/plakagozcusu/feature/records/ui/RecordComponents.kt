package com.plakagozcusu.feature.records.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plakagozcusu.core.designsystem.component.PgStatusPill
import com.plakagozcusu.core.designsystem.component.PillTone
import com.plakagozcusu.core.designsystem.component.StatusBadge
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.core.designsystem.theme.PlateTextStyle
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.feature.records.R
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.forLanguageTag("tr"))
private val DAY = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr"))
private val FULL = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss", Locale.forLanguageTag("tr"))

private fun zoned(
    millis: Long,
    zone: String,
) = Instant.ofEpochMilli(millis).atZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()))

fun formatTime(o: Observation): String = TIME.format(zoned(o.bestFrameAt, o.zoneId))

fun formatDay(o: Observation): String = DAY.format(zoned(o.bestFrameAt, o.zoneId))

fun formatFull(
    millis: Long,
    zone: String,
): String = FULL.format(zoned(millis, zone))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordRow(
    observation: Observation,
    thumbnail: File?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(bg)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .semantics { this.selected = selected }
                .padding(PgTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(
                    width = 96.dp,
                    height = 64.dp,
                ).clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = stringResource(R.string.thumbnail_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.xs)) {
            Text(
                observation.plateText ?: stringResource(R.string.plate_unknown),
                style = if (observation.plateText != null) PlateTextStyle else MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(observation.recognitionStatus)
                if (observation.isProtected) PgStatusPill(stringResource(R.string.protected_label), PillTone.LIVE)
            }
            Text(formatTime(observation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DayHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = PgTheme.spacing.md, bottom = PgTheme.spacing.xs),
    )
}
