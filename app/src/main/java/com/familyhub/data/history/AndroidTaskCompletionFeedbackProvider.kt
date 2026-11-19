package com.familyhub.data.history

import android.content.Context
import com.familyhub.R
import com.familyhub.domain.model.ScoreImpact
import com.familyhub.domain.usecase.TaskCompletionAchievement
import com.familyhub.domain.usecase.TaskCompletionFeedback
import com.familyhub.domain.usecase.TaskCompletionFeedbackProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

class AndroidTaskCompletionFeedbackProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : TaskCompletionFeedbackProvider {
    override fun buildFeedback(impact: ScoreImpact): TaskCompletionFeedback {
        val summary = context.getString(
            R.string.task_completion_feedback_summary,
            formatSigned(impact.totalDelta),
            formatSigned(impact.basePoints),
            formatSigned(impact.bonusPoints),
            formatSigned(-impact.penaltyPoints)
        )

        val achievements = impact.achievementsUnlocked.map { type ->
            TaskCompletionAchievement(
                title = context.getString(type.titleRes),
                description = context.getString(type.descriptionRes)
            )
        }

        return TaskCompletionFeedback(
            summary = summary,
            achievements = achievements
        )
    }

    private fun formatSigned(value: Int): String {
        if (value == 0) {
            return context.getString(R.string.task_completion_feedback_zero_value)
        }
        val pattern = context.getString(R.string.task_completion_feedback_signed_value)
        return String.format(Locale.getDefault(), pattern, value)
    }
}
