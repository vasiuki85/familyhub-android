package com.familyhub.ui.screens.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EventDateTimePolicyTest {
    private val moscow = ZoneId.of("Europe/Moscow")

    @Test
    fun requestedCalendarDate_initializesNewEventDate() {
        val selectedDate = LocalDate.of(2026, 8, 12)
        val now = Instant.parse("2026-07-29T10:15:00Z").toEpochMilli()

        val pickerMillis = EventDateTimePolicy.initialPickerDateMillis(
            eventStartDateTime = null,
            requestedDate = selectedDate,
            nowMillis = now,
            zoneId = moscow,
        )

        assertEquals(selectedDate, EventDateTimePolicy.pickerDate(pickerMillis))
    }

    @Test
    fun existingEventDate_hasPriorityWhenEditing() {
        val eventStart = LocalDate.of(2026, 9, 3)
            .atTime(18, 40)
            .atZone(moscow)
            .toInstant()
            .toEpochMilli()

        val pickerMillis = EventDateTimePolicy.initialPickerDateMillis(
            eventStartDateTime = eventStart,
            requestedDate = LocalDate.of(2027, 1, 1),
            nowMillis = 0L,
            zoneId = moscow,
        )

        assertEquals(LocalDate.of(2026, 9, 3), EventDateTimePolicy.pickerDate(pickerMillis))
    }

    @Test
    fun combiningPickerDateAndTime_preservesDateAcrossTimeZones() {
        val selectedDate = LocalDate.of(2026, 8, 12)
        val pickerMillis = selectedDate
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        listOf("Europe/Moscow", "America/Los_Angeles").forEach { zoneName ->
            val zone = ZoneId.of(zoneName)
            val result = EventDateTimePolicy.combinePickerDateAndTime(
                selectedDateMillis = pickerMillis,
                hour = 7,
                minute = 25,
                zoneId = zone,
            )
            val resultDateTime = Instant.ofEpochMilli(result).atZone(zone)

            assertEquals(selectedDate, resultDateTime.toLocalDate())
            assertEquals(7, resultDateTime.hour)
            assertEquals(25, resultDateTime.minute)
        }
    }
}
