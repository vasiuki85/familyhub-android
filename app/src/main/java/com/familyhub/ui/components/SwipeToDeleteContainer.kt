package com.familyhub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.familyhub.R

@Composable
fun SwipeToDeleteContainer(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableStateOf(0f) }
    val offsetXAnimated by animateFloatAsState(
        targetValue = offsetX,
        label = "swipe_offset"
    )
    
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 200.dp.toPx() }

    Box(modifier = modifier) {
        // Delete background
        if (offsetXAnimated < 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = FhIcons.Delete,
                    contentDescription = stringResource(R.string.swipe_delete_cd),
                    tint = Color.White,
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
        }

        // Content
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetXAnimated.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        val newOffset = offsetX + dragAmount
                        offsetX = newOffset.coerceAtMost(0f).coerceAtLeast(-swipeThreshold * 1.5f)
                        
                        if (!change.pressed && offsetX <= -swipeThreshold) {
                            onDelete()
                            offsetX = 0f
                        } else if (!change.pressed) {
                            offsetX = 0f
                        }
                    }
                }
                .fillMaxSize()
        ) {
            content()
        }
    }
}

