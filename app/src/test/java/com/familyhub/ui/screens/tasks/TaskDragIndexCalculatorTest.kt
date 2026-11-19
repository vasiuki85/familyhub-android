package com.familyhub.ui.screens.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDragIndexCalculatorTest {

    @Test
    fun cumulativeOffset_reachesNextAndPreviousIndex() {
        assertEquals(
            2,
            TaskDragIndexCalculator.targetIndex(
                startIndex = 1,
                cumulativeOffsetPx = 90f,
                estimatedItemExtentPx = 80f,
                itemCount = 4,
            ),
        )
        assertEquals(
            0,
            TaskDragIndexCalculator.targetIndex(
                startIndex = 1,
                cumulativeOffsetPx = -90f,
                estimatedItemExtentPx = 80f,
                itemCount = 4,
            ),
        )
    }

    @Test
    fun targetIndex_isClamped() {
        assertEquals(
            3,
            TaskDragIndexCalculator.targetIndex(
                startIndex = 0,
                cumulativeOffsetPx = 10_000f,
                estimatedItemExtentPx = 80f,
                itemCount = 4,
            ),
        )
        assertEquals(
            0,
            TaskDragIndexCalculator.targetIndex(
                startIndex = 3,
                cumulativeOffsetPx = -10_000f,
                estimatedItemExtentPx = 80f,
                itemCount = 4,
            ),
        )
    }

    @Test
    fun smallOffset_staysOnStartIndex() {
        assertEquals(
            1,
            TaskDragIndexCalculator.targetIndex(
                startIndex = 1,
                cumulativeOffsetPx = 20f,
                estimatedItemExtentPx = 80f,
                itemCount = 4,
            ),
        )
    }
}
