package com.familyhub.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.familyhub.data.local.db.converters.TaskAttachmentListConverter
import com.familyhub.data.local.db.converters.TaskCommentListConverter
import com.familyhub.domain.model.TaskReminderMode

@Entity(
    tableName = "tasks",
    indices = [
        androidx.room.Index("status"),
        androidx.room.Index("assignedToUserId"),
        androidx.room.Index("dueDate")
    ]
)
@TypeConverters(TaskAttachmentListConverter::class, TaskCommentListConverter::class)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val createdByUserId: String,
    val assignedToUserId: String,
    val dueDate: Long?,
    @ColumnInfo(defaultValue = "0")
    val dueDateHasTime: Boolean = false,
    @ColumnInfo(defaultValue = "NONE")
    val reminderMode: String = TaskReminderMode.NONE.name,
    @ColumnInfo(defaultValue = "NULL")
    val reminderMinutesOfDay: Int? = null,
    val repeatRule: String,       // "NONE" / "DAILY" / "WEEKLY"
    val status: String,           // "TODO" / "DONE"
    val createdAt: Long,
    val orderIndex: Int = 0,      // порядок сортировки (для drag & drop)
    val isPrivate: Boolean = false, // приватная задача (видна только создателю и исполнителю)
    val taskType: String = "ROUTINE",
    @ColumnInfo(defaultValue = "MEDIUM")
    val priority: String = "MEDIUM",
    @ColumnInfo(defaultValue = "NULL")
    val rewardPoints: Int? = null,
    val categoryId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val requiresApproval: Boolean = false,
    @ColumnInfo(defaultValue = "[]")
    val attachments: List<TaskAttachmentPayload> = emptyList(),
    @ColumnInfo(defaultValue = "[]")
    val comments: List<TaskCommentPayload> = emptyList(),
    @ColumnInfo(defaultValue = "0")
    val completionCycle: Long = 0L
)

