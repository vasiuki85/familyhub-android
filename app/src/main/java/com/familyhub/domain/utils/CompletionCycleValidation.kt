package com.familyhub.domain.utils

import com.familyhub.data.export.ExportData
import com.familyhub.domain.model.TaskTransitionInvalidException

object CompletionCycleValidation {
    fun requireNonNegativeTaskCycle(cycle: Long) {
        if (cycle < 0L) {
            throw TaskTransitionInvalidException("Task completionCycle must be >= 0")
        }
    }

    fun requireNonNegativeCompletionCycle(cycle: Long) {
        if (cycle < 0L) {
            throw TaskTransitionInvalidException("Task completion completionCycle must be >= 0")
        }
    }

    fun validateExportSnapshotCycles(data: ExportData) {
        data.tasks.forEach { task ->
            requireNonNegativeTaskCycle(task.completionCycle)
        }
        data.taskCompletions.forEach { completion ->
            completion.completionCycle?.let { requireNonNegativeCompletionCycle(it) }
        }
    }
}
