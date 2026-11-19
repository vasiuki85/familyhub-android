package com.familyhub.domain.model

import androidx.annotation.StringRes
import com.familyhub.R

/**
 * Тип задачи влияет на базовое количество баллов за её выполнение.
 */
enum class TaskType(
    val weight: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
) {
    ROUTINE(
        weight = 1,
        titleRes = R.string.task_type_routine,
        descriptionRes = R.string.task_type_routine_desc
    ),
    IMPORTANT(
        weight = 2,
        titleRes = R.string.task_type_important,
        descriptionRes = R.string.task_type_important_desc
    ),
    CARE(
        weight = 2,
        titleRes = R.string.task_type_care,
        descriptionRes = R.string.task_type_care_desc
    ),
    CHALLENGE(
        weight = 3,
        titleRes = R.string.task_type_challenge,
        descriptionRes = R.string.task_type_challenge_desc
    );

    companion object {
        fun fromName(value: String?): TaskType = values().firstOrNull { it.name == value }
            ?: ROUTINE
    }
}
