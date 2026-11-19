package com.familyhub.domain.model

import androidx.annotation.StringRes
import com.familyhub.R

enum class AchievementType(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
) {
    FIRST_TASK(
        titleRes = R.string.achievement_first_task_title,
        descriptionRes = R.string.achievement_first_task_desc
    ),
    TEN_TASKS(
        titleRes = R.string.achievement_ten_tasks_title,
        descriptionRes = R.string.achievement_ten_tasks_desc
    ),
    PERFECT_WEEK(
        titleRes = R.string.achievement_perfect_week_title,
        descriptionRes = R.string.achievement_perfect_week_desc
    )
}
