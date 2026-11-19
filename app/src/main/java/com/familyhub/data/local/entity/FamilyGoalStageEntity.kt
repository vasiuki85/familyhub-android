package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_stages",
    indices = [Index(value = ["goalId"])],
    foreignKeys = [
        ForeignKey(
            entity = FamilyGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class FamilyGoalStageEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val title: String,
    val description: String?,
    val position: Int
)
