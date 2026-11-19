package com.familyhub.ui.screens.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarDayIndicatorMapperTest {

    @Test
    fun indicatorsForDay_emptyDay_returnsNoVisibleKinds() {
        val result = CalendarDayIndicatorMapper.indicatorsForDay(
            hasEvents = false,
            hasTasks = false,
            hasGoals = false,
            hasMenu = false,
        )

        assertTrue(result.visibleKinds.isEmpty())
        assertFalse(result.hasMore)
        assertFalse(CalendarDayIndicatorMapper.hasImportantContent(result))
    }

    @Test
    fun indicatorsForDay_multipleKinds_preservesPriorityOrder() {
        val result = CalendarDayIndicatorMapper.indicatorsForDay(
            hasEvents = true,
            hasTasks = true,
            hasGoals = true,
            hasMenu = true,
        )

        assertEquals(
            listOf(
                CalendarContentKind.EVENT,
                CalendarContentKind.TASK,
                CalendarContentKind.GOAL,
            ),
            result.visibleKinds,
        )
        assertTrue(result.hasMore)
    }

    @Test
    fun indicatorsForDay_threeKinds_showsAllWithoutMoreFlag() {
        val result = CalendarDayIndicatorMapper.indicatorsForDay(
            hasEvents = true,
            hasTasks = true,
            hasGoals = true,
            hasMenu = false,
        )

        assertEquals(3, result.visibleKinds.size)
        assertFalse(result.hasMore)
    }

    @Test
    fun todayAndSelected_areDistinctStates() {
        val today = LocalDate.of(2026, 8, 1)
        val selected = LocalDate.of(2026, 8, 8)

        assertTrue(CalendarDayIndicatorMapper.isToday(today, today))
        assertFalse(CalendarDayIndicatorMapper.isSelected(today, selected))
        assertTrue(CalendarDayIndicatorMapper.isSelected(selected, selected))
    }
}
