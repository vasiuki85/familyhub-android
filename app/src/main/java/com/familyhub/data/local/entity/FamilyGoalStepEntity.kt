package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_steps",
    indices = [Index(value = ["goalId"]), Index(value = ["stageId"])],
    foreignKeys = [
        ForeignKey(
            entity = FamilyGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FamilyGoalStageEntity::class,
            parentColumns = ["id"],
            childColumns = ["stageId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class FamilyGoalStepEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val stageId: String,
    val title: String,
    val isCompleted: Boolean,
    val position: Int
)
