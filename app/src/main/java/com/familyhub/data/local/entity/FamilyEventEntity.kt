package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_events")
data class FamilyEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String? = null,
    val startDateTime: Long,
    val createdByUserId: String,
    val durationMinutes: Int,
    val recurrence: String,
    val reminderMinutesBefore: Int?,
    val googleCalendarEventId: String? = null
)

