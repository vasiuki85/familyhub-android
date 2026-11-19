package com.familyhub.data.importer

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.domain.model.TaskTransitionInvalidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImportCycleValidationTest {

    private val taskId = "task-1"

    @Test
    fun rejectsNegativeTaskCycle() {
        val data = ExportData(
            tasks = listOf(exportTask(completionCycle = -1L))
        )
        assertThrows(TaskTransitionInvalidException::class.java) {
            ImportCycleValidation.validateAndNormalize(data)
        }
    }

    @Test
    fun rejectsNegativeCompletionCycle() {
        val data = ExportData(
            taskCompletions = listOf(completion(cycle = -1L))
        )
        assertThrows(TaskTransitionInvalidException::class.java) {
            ImportCycleValidation.validateAndNormalize(data)
        }
    }

    @Test
    fun rejectsConflictingDuplicateLogicalCycle() {
        val data = ExportData(
            taskCompletions = listOf(
                completion(id = "a", cycle = 0L, updatedAt = 100L),
                completion(id = "b", cycle = 0L, updatedAt = 200L)
            )
        )
        assertThrows(ImportValidationException::class.java) {
            ImportCycleValidation.validateAndNormalize(data)
        }
    }

    @Test
    fun collapsesIdenticalDuplicateLogicalCycle() {
        val duplicate = completion(id = "a", cycle = 0L, updatedAt = 100L)
        val data = ExportData(taskCompletions = listOf(duplicate, duplicate))
        val normalized = ImportCycleValidation.validateAndNormalize(data)
        assertEquals(1, normalized.taskCompletions.size)
        assertEquals(duplicate, normalized.taskCompletions.single())
    }

    @Test
    fun allowsLegacyNullCompletions() {
        val legacyA = completion(id = "legacy-a", cycle = null, updatedAt = 100L)
        val legacyB = completion(id = "legacy-b", cycle = null, updatedAt = 200L)
        val data = ExportData(taskCompletions = listOf(legacyB, legacyA))
        val normalized = ImportCycleValidation.validateAndNormalize(data)
        assertEquals(listOf(legacyA, legacyB), normalized.taskCompletions)
    }

    private fun exportTask(completionCycle: Long) = ExportTask(
        id = taskId,
        title = "Task",
        createdByUserId = "parent",
        assignedToUserId = "child",
        status = "TODO",
        completionCycle = completionCycle
    )

    private fun completion(
        id: String = "completion-1",
        cycle: Long?,
        updatedAt: Long = 1L
    ) = ExportTaskCompletion(
        id = id,
        taskId = taskId,
        userId = "child",
        taskType = "ROUTINE",
        completionTime = updatedAt,
        weekStartDate = 1L,
        basePoints = 10,
        totalPoints = 10,
        updatedAt = updatedAt,
        completionCycle = cycle
    )
}
