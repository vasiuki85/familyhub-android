package com.familyhub.ui.screens.tasks

import androidx.annotation.StringRes
import com.familyhub.R

enum class TaskTab(@StringRes val titleRes: Int) {
    MY(R.string.tasks_tab_my),
    ALL(R.string.tasks_tab_all),
    AWAITING(R.string.tasks_tab_awaiting)
}

