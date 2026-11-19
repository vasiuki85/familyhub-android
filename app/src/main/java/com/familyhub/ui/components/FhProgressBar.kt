package com.familyhub.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun clampDeterminateProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

/**
 * Determinate progress bar without Material 3's default end-of-track stop indicator dot.
 * At [progress] == 0f the track reads fully empty.
 */
@Composable
fun FhProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentDescription: String? = null,
    height: Dp = 8.dp,
) {
    val clampedProgress = clampDeterminateProgress(progress)

    LinearProgressIndicator(
        progress = { clampedProgress },
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = clampedProgress,
                    range = 0f..1f,
                )
                contentDescription?.let { this.contentDescription = it }
            },
        color = color,
        trackColor = trackColor,
        drawStopIndicator = {},
    )
}
