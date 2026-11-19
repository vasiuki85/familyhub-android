package com.familyhub.data.local.entity

import com.familyhub.domain.model.EventRecurrence
import com.familyhub.domain.model.FamilyEvent

fun FamilyEventEntity.toDomain(): FamilyEvent = FamilyEvent(
    id = id,
    title = title,
    description = description,
    startDateTime = startDateTime,
    createdByUserId = createdByUserId,
    durationMinutes = durationMinutes,
    recurrence = EventRecurrence.valueOf(recurrence),
    reminderMinutesBefore = reminderMinutesBefore,
    googleCalendarEventId = googleCalendarEventId
)

fun FamilyEvent.toEntity(): FamilyEventEntity = FamilyEventEntity(
    id = id,
    title = title,
    description = description,
    startDateTime = startDateTime,
    createdByUserId = createdByUserId,
    durationMinutes = durationMinutes,
    recurrence = recurrence.name,
    reminderMinutesBefore = reminderMinutesBefore,
    googleCalendarEventId = googleCalendarEventId
)
