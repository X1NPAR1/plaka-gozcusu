package com.plakagozcusu.core.designsystem.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.plakagozcusu.core.designsystem.theme.MinTouchTarget

@Composable
fun PgPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = MinTouchTarget), enabled = enabled) {
        Text(text)
    }
}

@Composable
fun PgSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = MinTouchTarget), enabled = enabled) {
        Text(text)
    }
}

@Composable
fun PgTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = MinTouchTarget)) {
        Text(text)
    }
}
