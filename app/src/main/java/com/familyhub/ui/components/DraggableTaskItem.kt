package com.familyhub.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun DraggableTaskItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (cumulativeOffsetY: Float) -> Unit = {},
    moveUpLabel: String? = null,
    moveDownLabel: String? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    dragHandleContentDescription: String? = null,
    content: @Composable (Modifier) -> Unit,
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val customActions = buildList {
        if (enabled && onMoveUp != null && moveUpLabel != null) {
            add(
                CustomAccessibilityAction(moveUpLabel) {
                    onMoveUp()
                    true
                },
            )
        }
        if (enabled && onMoveDown != null && moveDownLabel != null) {
            add(
                CustomAccessibilityAction(moveDownLabel) {
                    onMoveDown()
                    true
                },
            )
        }
    }

    val showDragHandle = enabled && dragHandleContentDescription != null

    val dragGestureModifier = if (enabled) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    isDragging = true
                    offsetY = 0f
                    onDragStart()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    offsetY += dragAmount.y
                    onDrag(offsetY)
                },
                onDragEnd = {
                    offsetY = 0f
                    isDragging = false
                    onDragEnd()
                },
                onDragCancel = {
                    offsetY = 0f
                    isDragging = false
                    onDragCancel()
                },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .offset { IntOffset(x = 0, y = offsetY.roundToInt()) }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isDragging = true
                                offsetY = 0f
                                onDragStart()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetY += dragAmount.y
                                onDrag(offsetY)
                            },
                            onDragEnd = {
                                offsetY = 0f
                                isDragging = false
                                onDragEnd()
                            },
                            onDragCancel = {
                                offsetY = 0f
                                isDragging = false
                                onDragCancel()
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            if (showDragHandle) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics {
                            contentDescription = dragHandleContentDescription!!
                            if (customActions.isNotEmpty()) {
                                this.customActions = customActions
                            }
                        }
                        .then(dragGestureModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = FhIcons.DragHandle,
                        contentDescription = null,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                content(Modifier)
            }
        }
    }
}
