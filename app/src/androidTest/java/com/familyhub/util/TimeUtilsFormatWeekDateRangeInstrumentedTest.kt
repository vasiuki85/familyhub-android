package com.familyhub.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeUtilsFormatWeekDateRangeInstrumentedTest {

    @Test
    fun formatWeekDateRange_monthBoundary_englishLocale_omitsYear() {
        val start = calendarMillis(2026, Calendar.JULY, 27)
        val end = calendarMillis(2026, Calendar.AUGUST, 2)

        val formatted = TimeUtils.formatWeekDateRange(
            startMillis = start,
            endMillis = end,
            compact = false,
            locale = Locale.US,
        )

        assertTrue(formatted.contains("27"))
        assertTrue(formatted.contains("2"))
        assertTrue(formatted.contains("Jul") || formatted.contains("July"))
        assertTrue(formatted.contains("Aug") || formatted.contains("August"))
        assertFalse(formatted.contains("2026"))
    }

    @Test
    fun formatWeekDateRange_monthBoundary_russianLocale_usesFullMonthNames() {
        val start = calendarMillis(2026, Calendar.JULY, 27)
        val end = calendarMillis(2026, Calendar.AUGUST, 2)

        val formatted = TimeUtils.formatWeekDateRange(
            startMillis = start,
            endMillis = end,
            compact = false,
            locale = Locale.forLanguageTag("ru"),
        )

        assertTrue(formatted.contains("27"))
        assertTrue(formatted.contains("2"))
        assertTrue(formatted.contains("июл"))
        assertTrue(formatted.contains("авг"))
        assertFalse(formatted.contains("2026"))
    }

    @Test
    fun formatWeekDateRange_yearBoundary_englishLocale_staysShort() {
        val start = calendarMillis(2026, Calendar.DECEMBER, 28)
        val end = calendarMillis(2027, Calendar.JANUARY, 3)

        val formatted = TimeUtils.formatWeekDateRange(
            startMillis = start,
            endMillis = end,
            compact = false,
            locale = Locale.US,
        )

        assertTrue(formatted.contains("28"))
        assertTrue(formatted.contains("3"))
        assertFalse(formatted.contains("2026"))
        assertFalse(formatted.contains("2027"))
    }

    @Test
    fun formatWeekDateRange_yearBoundary_russianLocale_staysShort() {
        val start = calendarMillis(2026, Calendar.DECEMBER, 28)
        val end = calendarMillis(2027, Calendar.JANUARY, 3)

        val formatted = TimeUtils.formatWeekDateRange(
            startMillis = start,
            endMillis = end,
            compact = false,
            locale = Locale.forLanguageTag("ru"),
        )

        assertTrue(formatted.contains("28"))
        assertTrue(formatted.contains("3"))
        assertTrue(formatted.contains("дек") || formatted.contains("декаб"))
        assertTrue(formatted.contains("янв"))
        assertFalse(formatted.contains("2026"))
        assertFalse(formatted.contains("2027"))
    }

    @Test
    fun formatWeekDateRange_compactMode_usesAbbreviatedMonths() {
        val start = calendarMillis(2026, Calendar.JULY, 27)
        val end = calendarMillis(2026, Calendar.AUGUST, 2)

        val full = TimeUtils.formatWeekDateRange(start, end, compact = false, locale = Locale.US)
        val compact = TimeUtils.formatWeekDateRange(start, end, compact = true, locale = Locale.US)

        assertTrue(compact.length <= full.length)
        assertFalse(compact.contains("2026"))
    }

    private fun calendarMillis(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        calendar.clear()
        calendar.set(year, month, day, 12, 0, 0)
        return calendar.timeInMillis
    }
}
