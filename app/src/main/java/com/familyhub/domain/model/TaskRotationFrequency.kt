package com.familyhub.domain.model

import androidx.annotation.StringRes
import com.familyhub.R

enum class TaskRotationFrequency(
    val intervalDays: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
) {
    DAILY(
        intervalDays = 1,
        titleRes = R.string.task_rotation_frequency_daily,
        descriptionRes = R.string.task_rotation_frequency_daily_desc
    ),
    WEEKLY(
        intervalDays = 7,
        titleRes = R.string.task_rotation_frequency_weekly,
        descriptionRes = R.string.task_rotation_frequency_weekly_desc
    )
}
