package com.familyhub.ui.screens.goals

data class GoalStageInput(
    val id: String?,
    val title: String,
    val description: String?,
    val steps: List<GoalStepInput>
)

data class GoalStepInput(
    val id: String?,
    val title: String,
    val isCompleted: Boolean
)
