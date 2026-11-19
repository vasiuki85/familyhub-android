package com.familyhub.ui.screens.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal object EventDateTimePolicy {
    fun initialPickerDateMillis(
        eventStartDateTime: Long?,
        requestedDate: LocalDate?,
        nowMillis: Long,
        zoneId: ZoneId,
    ): Long {
        val date = when {
            eventStartDateTime != null ->
                Instant.ofEpochMilli(eventStartDateTime).atZone(zoneId).toLocalDate()
            requestedDate != null -> requestedDate
            else -> Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        }
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun pickerDate(selectedDateMillis: Long): LocalDate =
        Instant.ofEpochMilli(selectedDateMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun combinePickerDateAndTime(
        selectedDateMillis: Long,
        hour: Int,
        minute: Int,
        zoneId: ZoneId,
    ): Long = pickerDate(selectedDateMillis)
        .atTime(hour, minute)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}
