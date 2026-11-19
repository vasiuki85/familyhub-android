package com.familyhub.ui.screens.tasks

import kotlin.math.roundToInt

object TaskDragIndexCalculator {
    fun targetIndex(
        startIndex: Int,
        cumulativeOffsetPx: Float,
        estimatedItemExtentPx: Float,
        itemCount: Int,
    ): Int {
        if (itemCount <= 0) return 0
        val lastIndex = itemCount - 1
        val clampedStart = startIndex.coerceIn(0, lastIndex)
        if (estimatedItemExtentPx <= 0f) return clampedStart
        val deltaSteps = (cumulativeOffsetPx / estimatedItemExtentPx).roundToInt()
        return (clampedStart + deltaSteps).coerceIn(0, lastIndex)
    }
}
