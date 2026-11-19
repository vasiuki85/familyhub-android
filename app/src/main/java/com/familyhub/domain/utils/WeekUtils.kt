package com.familyhub.domain.utils

import java.util.Calendar

object WeekUtils {
    /**
     * Возвращает миллисекунды начала текущей недели (понедельник 00:00)
     */
    fun getCurrentWeekStartMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Установить на понедельник
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = when (dayOfWeek) {
            Calendar.SUNDAY -> 6
            else -> dayOfWeek - Calendar.MONDAY
        }
        calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        
        return calendar.timeInMillis
    }
    
    /**
     * Возвращает начало недели для указанной даты
     */
    fun getWeekStartMillis(dateMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dateMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = when (dayOfWeek) {
            Calendar.SUNDAY -> 6
            else -> dayOfWeek - Calendar.MONDAY
        }
        calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday)

        return calendar.timeInMillis
    }

    fun shiftWeek(startOfWeekMillis: Long, deltaWeeks: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startOfWeekMillis
        calendar.add(Calendar.WEEK_OF_YEAR, deltaWeeks)
        return calendar.timeInMillis
    }

    fun dayStart(startOfWeekMillis: Long, dayOffset: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startOfWeekMillis
        calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
        return calendar.timeInMillis
    }
}

