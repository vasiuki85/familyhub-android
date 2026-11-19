package com.familyhub.domain.model

/**
 * Отдельный шаг внутри этапа семейной цели.
 */
data class FamilyGoalStep(
    val id: String,
    val goalId: String,
    val stageId: String,
    val title: String,
    val isCompleted: Boolean,
    val position: Int
)
