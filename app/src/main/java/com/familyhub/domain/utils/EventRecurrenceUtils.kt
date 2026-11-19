package com.familyhub.domain.utils

import com.familyhub.domain.model.EventRecurrence
import com.familyhub.domain.model.FamilyEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private val zoneId: ZoneId = ZoneId.systemDefault()

fun FamilyEvent.getNextOccurrence(afterMillis: Long): Long? {
    var occurrence = startDateTime.toZonedDateTime()
    val after = Instant.ofEpochMilli(afterMillis).atZone(zoneId)

    if (occurrence.isAfter(after) || occurrence.isEqual(after)) {
        return occurrence.toEpochMillis()
    }

    occurrence = when (recurrence) {
        EventRecurrence.NONE -> return null
        EventRecurrence.DAILY -> {
            val diff = ChronoUnit.DAYS.between(occurrence, after)
            occurrence.plusDays(diff + 1)
        }
        EventRecurrence.WEEKLY -> {
            val diff = ChronoUnit.WEEKS.between(occurrence, after)
            occurrence.plusWeeks(diff + 1)
        }
        EventRecurrence.MONTHLY -> {
            val diff = ChronoUnit.MONTHS.between(occurrence, after)
            occurrence.plusMonths(diff + 1)
        }
        EventRecurrence.YEARLY -> {
            val diff = ChronoUnit.YEARS.between(occurrence, after)
            occurrence.plusYears(diff + 1)
        }
    }

    return occurrence.toEpochMillis()
}

fun FamilyEvent.getOccurrencesBetween(startMillis: Long, endMillis: Long): List<Long> {
    if (endMillis < startDateTime) {
        return emptyList()
    }

    val occurrences = mutableListOf<Long>()
    var next = getNextOccurrence(startMillis - 1)

    while (next != null && next <= endMillis) {
        occurrences += next
        if (recurrence == EventRecurrence.NONE) {
            break
        }
        next = getNextOccurrence(next + 1)
    }

    return occurrences
}

private fun Long.toZonedDateTime(): ZonedDateTime =
    Instant.ofEpochMilli(this).atZone(zoneId)

private fun ZonedDateTime.toEpochMillis(): Long =
    toInstant().toEpochMilli()
