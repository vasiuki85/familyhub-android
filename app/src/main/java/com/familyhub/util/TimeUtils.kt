package com.familyhub.util

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.text.format.DateFormat as AndroidDateFormat
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun now(): Long = System.currentTimeMillis()

    fun addDays(dateMillis: Long, days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dateMillis
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return calendar.timeInMillis
    }

    fun formatDate(dateMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dateMillis))

    /**
     * Locale-aware week range without year, e.g. "Jul 27 – Aug 2" or "27 июля – 2 августа".
     */
    fun formatWeekDateRange(
        startMillis: Long,
        endMillis: Long,
        compact: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): String {
        val skeleton = if (compact) "dMMM" else "dMMMM"
        val pattern = AndroidDateFormat.getBestDateTimePattern(locale, skeleton)
        val formatter = SimpleDateFormat(pattern, ULocale.forLocale(locale))
        val startLabel = formatter.format(Date(startMillis))
        val endLabel = formatter.format(Date(endMillis))
        return "$startLabel – $endLabel"
    }

    fun formatDateTime(dateMillis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(dateMillis))

    fun formatTime(dateMillis: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(dateMillis))

    fun getStartOfDay(timeMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun isSameDay(firstMillis: Long, secondMillis: Long): Boolean =
        getStartOfDay(firstMillis) == getStartOfDay(secondMillis)

    fun isBeforeDay(dateMillis: Long, referenceMillis: Long): Boolean =
        getStartOfDay(dateMillis) < getStartOfDay(referenceMillis)

    /** Calendar-day distance from [fromMillis] to [toMillis], both normalized to start of day. */
    fun daysBetweenStartOfDays(fromMillis: Long, toMillis: Long): Int {
        val dayMillis = 24L * 60 * 60 * 1000
        return ((getStartOfDay(toMillis) - getStartOfDay(fromMillis)) / dayMillis).toInt()
    }

    fun getEndOfDay(timeMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    fun getHourOfDay(timeMillis: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        return calendar.get(Calendar.HOUR_OF_DAY)
    }
}
