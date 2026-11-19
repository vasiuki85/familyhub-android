package com.familyhub.data.local.entity

import com.familyhub.domain.model.FamilyGoalStage
import com.familyhub.domain.model.FamilyGoalStep

fun FamilyGoalStageEntity.toDomain(steps: List<FamilyGoalStepEntity>): FamilyGoalStage =
    FamilyGoalStage(
        id = id,
        goalId = goalId,
        title = title,
        description = description,
        position = position,
        steps = steps.sortedBy(FamilyGoalStepEntity::position).map(FamilyGoalStepEntity::toDomain)
    )

fun FamilyGoalStepEntity.toDomain(): FamilyGoalStep =
    FamilyGoalStep(
        id = id,
        goalId = goalId,
        stageId = stageId,
        title = title,
        isCompleted = isCompleted,
        position = position
    )

fun FamilyGoalStage.toEntity(): FamilyGoalStageEntity =
    FamilyGoalStageEntity(
        id = id,
        goalId = goalId,
        title = title,
        description = description,
        position = position
    )

fun FamilyGoalStep.toEntity(): FamilyGoalStepEntity =
    FamilyGoalStepEntity(
        id = id,
        goalId = goalId,
        stageId = stageId,
        title = title,
        isCompleted = isCompleted,
        position = position
    )
