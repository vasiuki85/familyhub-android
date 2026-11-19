package com.familyhub.ui.common

import androidx.annotation.StringRes
import com.familyhub.R
import com.familyhub.domain.model.EventRecurrence

@StringRes
fun EventRecurrence.toTitleRes(): Int = when (this) {
    EventRecurrence.NONE -> R.string.event_dialog_recurrence_none
    EventRecurrence.DAILY -> R.string.event_dialog_recurrence_daily
    EventRecurrence.WEEKLY -> R.string.event_dialog_recurrence_weekly
    EventRecurrence.MONTHLY -> R.string.event_dialog_recurrence_monthly
    EventRecurrence.YEARLY -> R.string.event_dialog_recurrence_yearly
}
