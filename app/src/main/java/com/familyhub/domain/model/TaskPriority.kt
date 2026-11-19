package com.familyhub.domain.model

import androidx.annotation.StringRes
import com.familyhub.R

/**
 * Приоритет задачи помогает семье понимать, какие задачи требуются в первую очередь.
 */
enum class TaskPriority(
    val order: Int,
    @StringRes val titleRes: Int,
    @StringRes val shortTitleRes: Int
) {
    LOW(
        order = 1,
        titleRes = R.string.task_priority_low,
        shortTitleRes = R.string.task_priority_low_short
    ),
    MEDIUM(
        order = 2,
        titleRes = R.string.task_priority_medium,
        shortTitleRes = R.string.task_priority_medium_short
    ),
    HIGH(
        order = 3,
        titleRes = R.string.task_priority_high,
        shortTitleRes = R.string.task_priority_high_short
    );

    companion object {
        fun fromName(value: String?): TaskPriority = values().firstOrNull { it.name == value }
            ?: MEDIUM
    }
}
