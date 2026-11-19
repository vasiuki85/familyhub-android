package com.familyhub.domain.model

/**
 * Полное описание семейной цели с этапами и шагами.
 */
data class FamilyGoalDetail(
    val goal: FamilyGoal,
    val stages: List<FamilyGoalStage>
) {
    val totalSteps: Int
        get() = stages.sumOf(FamilyGoalStage::totalSteps)

    val completedSteps: Int
        get() = stages.sumOf(FamilyGoalStage::completedSteps)
}
