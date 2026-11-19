package com.familyhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Trailing-edge fade hint for horizontally scrollable rows. Signals more content without
 * covering chips with a floating control.
 */
@Composable
fun FhHorizontalScrollFade(
    visible: Boolean,
    modifier: Modifier = Modifier,
    fadeWidth: Dp = 28.dp,
    fadeColor: Color = MaterialTheme.colorScheme.background,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(fadeWidth)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to fadeColor,
                    ),
                ),
        )
    }
}

/**
 * Draws a trailing-edge fade over scrollable content without intercepting touch events.
 */
fun Modifier.fhHorizontalScrollFadeOverlay(
    visible: Boolean,
    fadeWidth: Dp = 28.dp,
    fadeColor: Color,
): Modifier {
    if (!visible) return this
    return drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, fadeColor),
                startX = size.width - fadeWidth.toPx(),
                endX = size.width,
            ),
        )
    }
}
