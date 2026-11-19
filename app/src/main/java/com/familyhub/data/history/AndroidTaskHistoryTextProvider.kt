package com.familyhub.data.history

import android.content.Context
import com.familyhub.R
import com.familyhub.domain.usecase.TaskHistoryTextProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidTaskHistoryTextProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : TaskHistoryTextProvider {
    override fun taskCreated(title: String): String {
        return context.getString(R.string.task_history_created, title)
    }

    override fun taskUpdated(changes: List<String>): String {
        val joined = changes.joinToString(", ")
        return context.getString(R.string.task_history_updated, joined)
    }

    override fun statusChanged(oldStatus: String, newStatus: String): String {
        return context.getString(R.string.task_history_status_changed, oldStatus, newStatus)
    }

    override fun taskDeleted(title: String): String {
        return context.getString(R.string.task_history_deleted, title)
    }

    override fun taskReassigned(): String {
        return context.getString(R.string.task_history_reassigned)
    }

    override fun privacyChanged(isPrivate: Boolean): String {
        return if (isPrivate) {
            context.getString(R.string.task_history_privacy_private)
        } else {
            context.getString(R.string.task_history_privacy_public)
        }
    }

    override fun rotationChanged(previousName: String?, newName: String): String {
        return context.getString(
            R.string.task_history_rotation,
            previousName ?: context.getString(R.string.task_history_rotation_unknown),
            newName
        )
    }
}
