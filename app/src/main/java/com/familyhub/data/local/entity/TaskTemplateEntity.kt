package com.familyhub.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_templates")
data class TaskTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val repeatRule: String,
    val defaultAssigneeId: String?,
    val isPrivate: Boolean,
    val taskType: String,
    val createdByUserId: String,
    val createdAt: Long,
    val categoryId: String?,
    @ColumnInfo(defaultValue = "MEDIUM")
    val priority: String = "MEDIUM"
)
