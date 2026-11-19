package com.familyhub.domain.model

import kotlin.math.roundToInt

/**
 * Детальный этап семейной цели. Содержит последовательность шагов, которые можно отмечать чекбоксами.
 */
data class FamilyGoalStage(
    val id: String,
    val goalId: String,
    val title: String,
    val description: String?,
    val position: Int,
    val steps: List<FamilyGoalStep>
) {
    val totalSteps: Int
        get() = steps.size

    val completedSteps: Int
        get() = steps.count(FamilyGoalStep::isCompleted)

    val progress: Float
        get() = if (totalSteps == 0) 0f else completedSteps.toFloat() / totalSteps.toFloat()

    val completionPercent: Int
        get() = (progress * 100f).roundToInt()
}
