package com.familyhub.data.importer

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.sync.logic.CompletionCycleMerge
import com.familyhub.domain.utils.CompletionCycleValidation

class ImportValidationException(message: String) : IllegalArgumentException(message)

object ImportCycleValidation {

    fun validateAndNormalize(data: ExportData): ExportData {
        data.tasks.forEach { task ->
            CompletionCycleValidation.requireNonNegativeTaskCycle(task.completionCycle)
        }

        val normalizedCompletions = normalizeTaskCompletions(data.taskCompletions)
        return data.copy(taskCompletions = normalizedCompletions)
    }

    private fun normalizeTaskCompletions(
        completions: List<ExportTaskCompletion>
    ): List<ExportTaskCompletion> {
        completions.forEach { completion ->
            val cycle = completion.completionCycle
            if (cycle != null) {
                CompletionCycleValidation.requireNonNegativeCompletionCycle(cycle)
            }
        }

        val grouped = completions.groupBy { completion ->
            val cycle = completion.completionCycle
            if (cycle != null) {
                completion.taskId to cycle
            } else {
                null
            }
        }

        val legacy = grouped[null].orEmpty()
        val cycleRows = grouped.filterKeys { it != null }

        val dedupedCycleRows = cycleRows.map { (key, group) ->
            val distinct = group.distinct()
            if (distinct.size > 1) {
                val taskId = key!!.first
                val cycle = key.second
                throw ImportValidationException(
                    "Conflicting task completions for taskId=$taskId completionCycle=$cycle"
                )
            }
            distinct.single()
        }

        return (legacy + dedupedCycleRows)
            .sortedWith(completionStableOrder())
    }

    private fun completionStableOrder(): Comparator<ExportTaskCompletion> =
        compareBy(
            { completion ->
                when (val key = CompletionCycleMerge.logicalKey(completion)) {
                    is CompletionCycleMerge.LogicalKey.Cycle -> "1:${key.taskId}:${key.completionCycle}"
                    is CompletionCycleMerge.LogicalKey.Legacy -> "0:${key.id}"
                }
            },
            { it.id }
        )
}
