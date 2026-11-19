package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskAttachment
import com.familyhub.data.export.ExportTaskComment
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.domain.utils.CompletionCycleIds
import com.familyhub.domain.utils.CompletionCycleValidation

/**
 * Logical completion keys and merge helpers for FH2-13.
 *
 * - New rows: (taskId, completionCycle) when completionCycle is non-null
 * - Legacy rows: (legacy, id) when completionCycle is null
 */
object CompletionCycleMerge {

    sealed interface LogicalKey {
        data class Cycle(val taskId: String, val completionCycle: Long) : LogicalKey
        data class Legacy(val id: String) : LogicalKey
    }

    fun logicalKey(completion: ExportTaskCompletion): LogicalKey {
        val cycle = completion.completionCycle
        return if (cycle != null) {
            LogicalKey.Cycle(completion.taskId, cycle)
        } else {
            LogicalKey.Legacy(completion.id)
        }
    }

    fun effectiveTaskCycle(task: ExportTask): Long = task.completionCycle

    fun resolveTaskCompletion(
        left: ExportTaskCompletion,
        right: ExportTaskCompletion
    ): ExportTaskCompletion {
        return if (compareCompletions(left, right) >= 0) left else right
    }

    fun resolveTaskDifferentCycle(left: ExportTask, right: ExportTask): ExportTask {
        require(left.completionCycle != right.completionCycle) {
            "resolveTaskDifferentCycle requires different completionCycle values"
        }
        val base = if (left.completionCycle > right.completionCycle) left else right
        return base.copy(
            attachments = mergeAttachments(left, right),
            comments = mergeComments(left, right)
        )
    }

    fun mergeTaskCompletions(
        local: List<ExportTaskCompletion>,
        remote: List<ExportTaskCompletion>
    ): List<ExportTaskCompletion> {
        val grouped = (local + remote).groupBy { logicalKey(it) }
        return grouped.entries
            .sortedWith(logicalKeyEntryComparator())
            .map { (_, group) ->
                canonicalize(group.reduce { acc, next -> resolveTaskCompletion(acc, next) })
            }
    }

    fun activeCompletionsForProjection(
        completions: List<ExportTaskCompletion>
    ): List<ExportTaskCompletion> {
        return completions
            .groupBy { logicalKey(it) }
            .entries
            .sortedWith(logicalKeyEntryComparator())
            .map { (_, group) -> group.reduce { acc, next -> resolveTaskCompletion(acc, next) } }
            .filter { it.reversedAt == null }
    }

    fun canonicalize(completion: ExportTaskCompletion): ExportTaskCompletion {
        val cycle = completion.completionCycle ?: return completion
        CompletionCycleValidation.requireNonNegativeCompletionCycle(cycle)
        val canonicalId = CompletionCycleIds.deterministicId(completion.taskId, cycle)
        return if (completion.id == canonicalId) completion else completion.copy(id = canonicalId)
    }

    fun mergeAttachments(left: ExportTask, right: ExportTask): List<ExportTaskAttachment> =
        (left.attachments + right.attachments)
            .groupBy { it.id }
            .map { (_, values) -> values.maxWith(attachmentComparator()) }
            .sortedWith(attachmentComparator())

    fun mergeComments(left: ExportTask, right: ExportTask): List<ExportTaskComment> =
        (left.comments + right.comments)
            .groupBy { it.id }
            .map { (_, values) -> values.maxWith(commentComparator()) }
            .sortedWith(commentComparator())

    private fun logicalKeyEntryComparator(): Comparator<Map.Entry<LogicalKey, List<ExportTaskCompletion>>> =
        compareBy({ logicalKeySortKey(it.key) })

    private fun logicalKeySortKey(key: LogicalKey): String = when (key) {
        is LogicalKey.Legacy -> "0:${key.id}"
        is LogicalKey.Cycle -> {
            val canonicalId = CompletionCycleIds.deterministicId(key.taskId, key.completionCycle)
            "1:${key.taskId}:${key.completionCycle}:$canonicalId"
        }
    }

    private fun compareCompletions(
        left: ExportTaskCompletion,
        right: ExportTaskCompletion
    ): Int {
        val leftReversed = left.reversedAt != null
        val rightReversed = right.reversedAt != null
        when {
            leftReversed && !rightReversed -> return 1
            rightReversed && !leftReversed -> return -1
        }

        if (leftReversed && rightReversed) {
            compareNullableLong(left.reversedAt, right.reversedAt).let { if (it != 0) return it }
        }

        compareLong(left.updatedAt, right.updatedAt).let { if (it != 0) return it }
        compareLong(left.completionTime, right.completionTime).let { if (it != 0) return it }
        return compareFullRecord(left, right)
    }

    private fun compareFullRecord(
        left: ExportTaskCompletion,
        right: ExportTaskCompletion
    ): Int {
        compareValuesBy(
            left,
            right,
            { it.taskId },
            { it.userId },
            { it.taskType },
            { it.weekStartDate },
            { it.basePoints },
            { it.bonusPoints },
            { it.penaltyPoints },
            { it.totalPoints }
        ).let { if (it != 0) return it }
        compareNullableString(left.reversedByUserId, right.reversedByUserId).let { if (it != 0) return it }
        compareNullableString(left.generatedRepeatTaskId, right.generatedRepeatTaskId).let { if (it != 0) return it }
        compareNullableLong(left.completionCycle, right.completionCycle).let { if (it != 0) return it }
        return left.id.compareTo(right.id)
    }

    private fun compareNullableLong(left: Long?, right: Long?): Int {
        return when {
            left == null && right == null -> 0
            left == null -> -1
            right == null -> 1
            else -> left.compareTo(right)
        }
    }

    private fun compareNullableString(left: String?, right: String?): Int {
        return when {
            left == null && right == null -> 0
            left == null -> -1
            right == null -> 1
            else -> left.compareTo(right)
        }
    }

    private fun compareLong(left: Long, right: Long): Int = left.compareTo(right)

    private fun attachmentComparator(): Comparator<ExportTaskAttachment> =
        Comparator { left, right ->
            compareValuesBy(left, right, { it.addedAt }, { it.id }, { it.name }, { it.uri })
                .let { if (it != 0) return@Comparator it }
            compareNullableString(left.mimeType, right.mimeType)
        }

    private fun commentComparator(): Comparator<ExportTaskComment> =
        compareBy(
            { it.createdAt },
            { it.id },
            { it.authorId },
            { it.message }
        )
}
