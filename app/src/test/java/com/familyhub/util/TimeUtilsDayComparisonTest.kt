package com.familyhub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TimeUtilsDayComparisonTest {

    @Test
    fun daysBetweenStartOfDays_countsCalendarDays() {
        val july27 = startOfDay(2026, Calendar.JULY, 27)
        val august1 = startOfDay(2026, Calendar.AUGUST, 1)

        assertEquals(5, TimeUtils.daysBetweenStartOfDays(july27, august1))
    }

    @Test
    fun isSameDay_matchesStartOfDay() {
        val morning = startOfDay(2026, Calendar.AUGUST, 1) + (9 * 60 * 60 * 1000)
        val evening = startOfDay(2026, Calendar.AUGUST, 1) + (20 * 60 * 60 * 1000)

        assertTrue(TimeUtils.isSameDay(morning, evening))
    }

    @Test
    fun isBeforeDay_comparesStartOfDayOnly() {
        val yesterday = startOfDay(2026, Calendar.JULY, 31) + (23 * 60 * 60 * 1000)
        val today = startOfDay(2026, Calendar.AUGUST, 1) + (1 * 60 * 60 * 1000)

        assertTrue(TimeUtils.isBeforeDay(yesterday, today))
        assertFalse(TimeUtils.isBeforeDay(today, yesterday))
    }

    private fun startOfDay(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
