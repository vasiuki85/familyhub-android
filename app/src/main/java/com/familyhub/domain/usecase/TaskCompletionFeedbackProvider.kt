package com.familyhub.domain.usecase

import com.familyhub.domain.model.ScoreImpact

data class TaskCompletionAchievement(
    val title: String,
    val description: String
)

data class TaskCompletionFeedback(
    val summary: String,
    val achievements: List<TaskCompletionAchievement>
)

interface TaskCompletionFeedbackProvider {
    fun buildFeedback(impact: ScoreImpact): TaskCompletionFeedback
}
