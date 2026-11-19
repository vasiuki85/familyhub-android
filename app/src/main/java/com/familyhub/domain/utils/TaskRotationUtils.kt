package com.familyhub.domain.utils

import com.familyhub.domain.model.TaskRotationFrequency

object TaskRotationUtils {
    private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

    fun calculateNextRotation(startDateTime: Long, frequency: TaskRotationFrequency, referenceTime: Long): Long {
        var next = startDateTime
        val interval = frequency.intervalDays * MILLIS_PER_DAY
        while (next <= referenceTime) {
            next += interval
        }
        return next
    }

    fun advanceAfterTrigger(currentNext: Long, frequency: TaskRotationFrequency, referenceTime: Long): Long {
        var next = currentNext
        val interval = frequency.intervalDays * MILLIS_PER_DAY
        while (next <= referenceTime) {
            next += interval
        }
        return next
    }
}
