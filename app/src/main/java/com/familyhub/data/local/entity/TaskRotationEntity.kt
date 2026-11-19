package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_rotations",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TaskRotationEntity(
    @PrimaryKey val taskId: String,
    val memberIds: List<String>,
    val currentIndex: Int,
    val frequency: String,
    val startDateTime: Long,
    val nextRotationAt: Long,
    val lastRotationAt: Long?
)
