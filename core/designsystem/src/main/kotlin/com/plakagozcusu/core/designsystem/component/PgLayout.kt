package com.plakagozcusu.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.plakagozcusu.core.designsystem.theme.MinTouchTarget
import com.plakagozcusu.core.designsystem.theme.PgTheme

/**
 * Standard screen frame: top app bar (title, optional back, actions) and a padded content column.
 * Every screen uses it so spacing, titles and navigation look the same everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PgScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backDescription: String = "",
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backDescription) }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}

@Composable
fun PgSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = PgTheme.spacing.md).semantics { heading() },
    )
}

/** A single headline number with label; [value] null shows "—" (never a made-up zero). */
@Composable
fun PgStatTile(
    label: String,
    value: Int?,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    PgCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium)
    }
}

/** Primary action with a leading icon; full width by default for the main call to action. */
@Composable
fun PgIconButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, modifier = Modifier.padding(start = PgTheme.spacing.sm))
    }
    if (primary) {
        Button(onClick = onClick, modifier = modifier.heightIn(min = MinTouchTarget), enabled = enabled, content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = MinTouchTarget), enabled = enabled, content = content)
    }
}

/** Vertical list of content with the standard gutter and spacing. */
@Composable
fun PgContentColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.padding(horizontal = PgTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.md),
        content = content,
    )
}
