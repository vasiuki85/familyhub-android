package com.familyhub.domain.utils

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.domain.model.TaskTransitionInvalidException
import org.junit.Assert.assertThrows
import org.junit.Test

class CompletionCycleValidationTest {

    private val taskId = "task-1"

    @Test
    fun validateExportSnapshotCycles_rejectsNegativeTaskCycle() {
        val data = ExportData(
            tasks = listOf(exportTask(completionCycle = -1L))
        )

        assertThrows(TaskTransitionInvalidException::class.java) {
            CompletionCycleValidation.validateExportSnapshotCycles(data)
        }
    }

    @Test
    fun validateExportSnapshotCycles_rejectsNegativeCompletionCycle() {
        val data = ExportData(
            taskCompletions = listOf(completion(cycle = -1L))
        )

        assertThrows(TaskTransitionInvalidException::class.java) {
            CompletionCycleValidation.validateExportSnapshotCycles(data)
        }
    }

    @Test
    fun validateExportSnapshotCycles_allowsLegacyNullCompletionCycle() {
        CompletionCycleValidation.validateExportSnapshotCycles(
            ExportData(taskCompletions = listOf(completion(cycle = null)))
        )
    }

    private fun exportTask(completionCycle: Long) = ExportTask(
        id = taskId,
        title = "Task",
        createdByUserId = "parent",
        assignedToUserId = "child",
        status = "TODO",
        completionCycle = completionCycle
    )

    private fun completion(cycle: Long?) = ExportTaskCompletion(
        id = "completion-1",
        taskId = taskId,
        userId = "child",
        taskType = "ROUTINE",
        completionTime = 1L,
        weekStartDate = 1L,
        basePoints = 10,
        totalPoints = 10,
        updatedAt = 1L,
        completionCycle = cycle
    )
}
