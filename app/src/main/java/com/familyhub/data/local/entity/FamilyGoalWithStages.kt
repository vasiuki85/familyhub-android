package com.familyhub.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class FamilyGoalWithStages(
    @Embedded val goal: FamilyGoalEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "goalId",
        entity = FamilyGoalStageEntity::class
    )
    val stages: List<StageWithSteps>
)

data class StageWithSteps(
    @Embedded val stage: FamilyGoalStageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "stageId"
    )
    val steps: List<FamilyGoalStepEntity>
)
