package com.plakagozcusu.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private const val STAMP_BG_ALPHA = 0.55f

/** Date/time drawn over an evidence image (bottom-right). Display layer only: the file is untouched. */
@Composable
fun BoxScope.TimeStampOverlay(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.labelMedium,
        modifier =
            modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = STAMP_BG_ALPHA), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
