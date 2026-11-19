package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportTask

internal object TaskStatusMerge {
    fun statusRank(status: String): Int = when (status) {
        "DONE" -> 2
        "WAITING_FOR_APPROVAL" -> 1
        else -> 0
    }

    fun hasMergedTaskStatusAheadOfRemote(merged: ExportData, remote: ExportData): Boolean {
        val remoteById = remote.tasks.associateBy(ExportTask::id)
        return merged.tasks.any { mergedTask ->
            val remoteTask = remoteById[mergedTask.id] ?: return@any false
            statusRank(mergedTask.status) > statusRank(remoteTask.status)
        }
    }
}
