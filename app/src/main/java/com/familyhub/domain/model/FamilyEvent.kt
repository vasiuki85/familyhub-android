package com.familyhub.domain.model

data class FamilyEvent(
    val id: String,               // UUID
    val title: String,            // "Соревнования по бегу у Лёши"
    val description: String? = null,
    val startDateTime: Long,      // millis UTC (дата и время начала)
    val createdByUserId: String,  // кто добавил
    val durationMinutes: Int,     // длительность события в минутах
    val recurrence: EventRecurrence,
    val reminderMinutesBefore: Int?,
    val googleCalendarEventId: String? = null
)

