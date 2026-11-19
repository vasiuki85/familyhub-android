package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * История изменений задач
 */
@Entity(
    tableName = "task_history",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskId"])]
)
data class TaskHistoryEntity(
    @PrimaryKey val id: String,
    val taskId: String, // ID задачи
    val changedByUserId: String, // Кто внес изменение
    val changeType: String, // "CREATED", "UPDATED", "STATUS_CHANGED", "DELETED"
    val changeDescription: String, // Описание изменения
    val oldValue: String?, // Старое значение (JSON для сложных изменений)
    val newValue: String?, // Новое значение (JSON для сложных изменений)
    val timestamp: Long // Время изменения
)

