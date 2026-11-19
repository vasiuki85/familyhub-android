package com.familyhub.ui.screens.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDayCellVisualsTest {

    @Test
    fun highlight_selectedDay_usesFillState() {
        assertEquals(
            CalendarDayCellHighlight.SELECTED_FILL,
            CalendarDayCellVisuals.highlight(isSelected = true, isToday = false),
        )
    }

    @Test
    fun highlight_todayNotSelected_usesOutlineState() {
        assertEquals(
            CalendarDayCellHighlight.TODAY_OUTLINE,
            CalendarDayCellVisuals.highlight(isSelected = false, isToday = true),
        )
    }

    @Test
    fun highlight_selectedToday_prefersFillState() {
        assertEquals(
            CalendarDayCellHighlight.SELECTED_FILL,
            CalendarDayCellVisuals.highlight(isSelected = true, isToday = true),
        )
    }

    @Test
    fun highlight_regularDay_hasNoHighlight() {
        assertEquals(
            CalendarDayCellHighlight.NONE,
            CalendarDayCellVisuals.highlight(isSelected = false, isToday = false),
        )
    }

    @Test
    fun hasEventIndicator_visibleKindsOrMore_returnsTrue() {
        val withEvents = CalendarDayIndicatorMapper.indicatorsForDay(
            hasEvents = true,
            hasTasks = false,
            hasGoals = false,
            hasMenu = false,
        )
        val withMore = CalendarDayIndicatorMapper.indicatorsForDay(
            hasEvents = true,
            hasTasks = true,
            hasGoals = true,
            hasMenu = true,
        )
        val empty = CalendarDayIndicatorMapper.indicatorsForDay(
            hasEvents = false,
            hasTasks = false,
            hasGoals = false,
            hasMenu = false,
        )

        assertTrue(CalendarDayCellVisuals.hasEventIndicator(withEvents))
        assertTrue(CalendarDayCellVisuals.hasEventIndicator(withMore))
        assertFalse(CalendarDayCellVisuals.hasEventIndicator(empty))
    }
}
