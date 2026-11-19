package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskAttachment
import com.familyhub.data.export.ExportTaskComment
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.domain.utils.CompletionCycleIds
import com.familyhub.domain.model.TaskTransitionInvalidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class CompletionCycleMergeTest {

    private val taskId = "task-1"
    private val userId = "child"

    @Test
    fun dualDeviceSameCycle_producesOneCanonicalCompletion() {
        val idA = "uuid-a"
        val idB = "uuid-b"
        val local = completion(id = idA, cycle = 0L, points = 10, updatedAt = 100L, completionTime = 100L)
        val remote = completion(id = idB, cycle = 0L, points = 10, updatedAt = 200L, completionTime = 200L)

        val mergedLocalFirst = CompletionCycleMerge.mergeTaskCompletions(listOf(local), listOf(remote)).single()
        val mergedRemoteFirst = CompletionCycleMerge.mergeTaskCompletions(listOf(remote), listOf(local)).single()

        val canonicalId = CompletionCycleIds.deterministicId(taskId, 0L)
        assertEquals(canonicalId, mergedLocalFirst.id)
        assertEquals(canonicalId, mergedRemoteFirst.id)
        assertEquals(mergedLocalFirst, mergedRemoteFirst)
        assertEquals(200L, mergedLocalFirst.updatedAt)
    }

    @Test
    fun resolver_sameTimestampsDifferentReversedByUserId_isCommutative() {
        val canonicalId = CompletionCycleIds.deterministicId(taskId, 0L)
        val left = completion(
            id = canonicalId,
            cycle = 0L,
            points = 10,
            updatedAt = 500L,
            completionTime = 500L,
            reversedAt = 600L,
            reversedByUserId = "parent-a"
        )
        val right = completion(
            id = canonicalId,
            cycle = 0L,
            points = 10,
            updatedAt = 500L,
            completionTime = 500L,
            reversedAt = 600L,
            reversedByUserId = "parent-b"
        )

        val leftFirst = CompletionCycleMerge.resolveTaskCompletion(left, right)
        val rightFirst = CompletionCycleMerge.resolveTaskCompletion(right, left)
        assertEquals(leftFirst, rightFirst)
        assertEquals("parent-b", leftFirst.reversedByUserId)
    }

    @Test
    fun resolver_payloadWithPipeCharacter_isCommutative() {
        val left = completion(id = "a", cycle = 0L, points = 10, updatedAt = 100L).copy(
            taskType = "A|B"
        )
        val right = completion(id = "b", cycle = 0L, points = 10, updatedAt = 100L).copy(
            taskType = "A"
        )
        assertEquals(
            CompletionCycleMerge.resolveTaskCompletion(left, right),
            CompletionCycleMerge.resolveTaskCompletion(right, left)
        )
    }

    @Test
    fun resolver_threeWayPermutations_areAssociative() {
        val records = listOf(
            completion(id = "a", cycle = 0L, points = 10, updatedAt = 100L),
            completion(id = "b", cycle = 0L, points = 10, updatedAt = 150L),
            completion(id = "c", cycle = 0L, points = 10, updatedAt = 200L)
        )
        val expected = records.reduce { acc, next -> CompletionCycleMerge.resolveTaskCompletion(acc, next) }
        permutations(records).forEach { order ->
            val reduced = order.reduce { acc, next -> CompletionCycleMerge.resolveTaskCompletion(acc, next) }
            assertEquals(expected, reduced)
        }
    }

    @Test
    fun mergeTaskCompletions_orderIndependentListEquality() {
        val local = listOf(
            completion(id = "a", cycle = 0L, points = 10, updatedAt = 100L),
            completion(id = "legacy-a", cycle = null, points = 5, updatedAt = 50L)
        )
        val remote = listOf(
            completion(id = "b", cycle = 1L, points = 15, updatedAt = 200L),
            completion(id = "legacy-b", cycle = null, points = 7, updatedAt = 70L)
        )

        val localFirst = CompletionCycleMerge.mergeTaskCompletions(local, remote)
        val remoteFirst = CompletionCycleMerge.mergeTaskCompletions(remote, local)
        assertEquals(localFirst, remoteFirst)
    }

    @Test
    fun attachmentMerge_sameTimestampDifferentPayload_isCommutative() {
        val left = taskWithAttachment("att-1", addedAt = 100L, name = "left-name")
        val right = taskWithAttachment("att-1", addedAt = 100L, name = "right-name")
        val leftFirst = CompletionCycleMerge.mergeAttachments(left, right)
        val rightFirst = CompletionCycleMerge.mergeAttachments(right, left)
        assertEquals(leftFirst, rightFirst)
        assertEquals("right-name", leftFirst.single().name)
    }

    @Test
    fun commentMerge_sameTimestampDifferentPayload_isCommutative() {
        val left = taskWithComment("cmt-1", createdAt = 100L, message = "left")
        val right = taskWithComment("cmt-1", createdAt = 100L, message = "right")
        val leftFirst = CompletionCycleMerge.mergeComments(left, right)
        val rightFirst = CompletionCycleMerge.mergeComments(right, left)
        assertEquals(leftFirst, rightFirst)
        assertEquals("right", leftFirst.single().message)
    }

    @Test
    fun resolver_isCommutative() {
        val left = completion(id = "a", cycle = 0L, points = 10, updatedAt = 100L)
        val right = completion(id = "b", cycle = 0L, points = 10, updatedAt = 200L)
        assertEquals(
            CompletionCycleMerge.resolveTaskCompletion(left, right),
            CompletionCycleMerge.resolveTaskCompletion(right, left)
        )
    }

    @Test
    fun resolver_isAssociative() {
        val a = completion(id = "a", cycle = 0L, points = 10, updatedAt = 100L)
        val b = completion(id = "b", cycle = 0L, points = 10, updatedAt = 150L)
        val c = completion(id = "c", cycle = 0L, points = 10, updatedAt = 200L)
        val abThenC = CompletionCycleMerge.resolveTaskCompletion(
            CompletionCycleMerge.resolveTaskCompletion(a, b),
            c
        )
        val aThenBc = CompletionCycleMerge.resolveTaskCompletion(
            a,
            CompletionCycleMerge.resolveTaskCompletion(b, c)
        )
        assertEquals(abThenC, aThenBc)
    }

    @Test
    fun resolver_isIdempotent() {
        val value = completion(id = "a", cycle = 0L, points = 10, updatedAt = 100L)
        assertEquals(
            value,
            CompletionCycleMerge.resolveTaskCompletion(value, value)
        )
    }

    @Test
    fun activeVersusReversed_sameCycle_reversedWins() {
        val active = completion(id = "a", cycle = 0L, points = 10, updatedAt = 500L)
        val reversed = completion(
            id = "b",
            cycle = 0L,
            points = 10,
            updatedAt = 100L,
            reversedAt = 600L
        )
        val merged = CompletionCycleMerge.resolveTaskCompletion(active, reversed)
        assertEquals(600L, merged.reversedAt)
    }

    @Test
    fun doneCycle0VersusTodoCycle1_todoCycle1Wins() {
        val doneOld = task(status = "DONE", cycle = 0L)
        val todoNew = task(status = "TODO", cycle = 1L)
        val merged = CompletionCycleMerge.resolveTaskDifferentCycle(doneOld, todoNew)
        assertEquals("TODO", merged.status)
        assertEquals(1L, merged.completionCycle)
    }

    @Test
    fun legacyCompletionsWithDifferentIds_arePreserved() {
        val legacyA = completion(id = "legacy-a", cycle = null, points = 10, updatedAt = 100L)
        val legacyB = completion(id = "legacy-b", cycle = null, points = 20, updatedAt = 200L)
        val merged = CompletionCycleMerge.mergeTaskCompletions(listOf(legacyA), listOf(legacyB))
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.id == "legacy-a" })
        assertTrue(merged.any { it.id == "legacy-b" })
    }

    @Test
    fun projectionCountsAtMostOneCompletionPerLogicalCycle() {
        val local = completion(id = "uuid-a", cycle = 0L, points = 10, updatedAt = 100L)
        val remote = completion(id = "uuid-b", cycle = 0L, points = 10, updatedAt = 200L)
        val merged = CompletionCycleMerge.mergeTaskCompletions(listOf(local), listOf(remote))
        val active = CompletionCycleMerge.activeCompletionsForProjection(merged)
        assertEquals(1, active.size)
        assertEquals(10, active.single().totalPoints)
    }

    @Test
    fun twoFullCycles_countTwiceInProjection() {
        val cycle0 = completion(id = "a", cycle = 0L, points = 10, updatedAt = 100L)
        val cycle1 = completion(id = "b", cycle = 1L, points = 15, updatedAt = 200L)
        val active = CompletionCycleMerge.activeCompletionsForProjection(listOf(cycle0, cycle1))
        assertEquals(2, active.size)
        assertEquals(25, active.sumOf { it.totalPoints })
    }

    @Test
    fun resolver_nullReversedByUserIdVersusEmpty_isCommutative() {
        val base = completion(
            id = "a",
            cycle = 0L,
            points = 10,
            updatedAt = 100L,
            reversedAt = 600L
        )
        val withNull = base.copy(reversedByUserId = null)
        val withEmpty = base.copy(reversedByUserId = "")
        assertEquals(
            CompletionCycleMerge.resolveTaskCompletion(withNull, withEmpty),
            CompletionCycleMerge.resolveTaskCompletion(withEmpty, withNull)
        )
        assertEquals("", CompletionCycleMerge.resolveTaskCompletion(withNull, withEmpty).reversedByUserId)
    }

    @Test
    fun resolver_nullGeneratedRepeatTaskIdVersusEmpty_isCommutative() {
        val base = completion(id = "a", cycle = 0L, points = 10, updatedAt = 100L)
        val withNull = base.copy(generatedRepeatTaskId = null)
        val withEmpty = base.copy(generatedRepeatTaskId = "")
        assertEquals(
            CompletionCycleMerge.resolveTaskCompletion(withNull, withEmpty),
            CompletionCycleMerge.resolveTaskCompletion(withEmpty, withNull)
        )
        assertEquals("", CompletionCycleMerge.resolveTaskCompletion(withNull, withEmpty).generatedRepeatTaskId)
    }

    @Test
    fun attachmentMerge_nullMimeTypeVersusEmpty_isCommutative() {
        val left = taskWithAttachment("att-1", addedAt = 100L, name = "same", mimeType = null)
        val right = taskWithAttachment("att-1", addedAt = 100L, name = "same", mimeType = "")
        val leftFirst = CompletionCycleMerge.mergeAttachments(left, right)
        val rightFirst = CompletionCycleMerge.mergeAttachments(right, left)
        assertEquals(leftFirst, rightFirst)
        assertEquals("", leftFirst.single().mimeType)
    }

    @Test
    fun canonicalize_negativeCycle_throws() {
        val malformed = completion(id = "bad", cycle = -1L, points = 10, updatedAt = 1L)
        assertThrows(TaskTransitionInvalidException::class.java) {
            CompletionCycleMerge.canonicalize(malformed)
        }
    }

    @Test
    fun mergeTaskCompletions_negativeCycle_throws() {
        val malformed = completion(id = "bad", cycle = -1L, points = 10, updatedAt = 1L)
        assertThrows(TaskTransitionInvalidException::class.java) {
            CompletionCycleMerge.mergeTaskCompletions(listOf(malformed), emptyList())
        }
    }

    private fun permutations(values: List<ExportTaskCompletion>): List<List<ExportTaskCompletion>> {
        if (values.size <= 1) return listOf(values)
        val result = mutableListOf<List<ExportTaskCompletion>>()
        values.indices.forEach { index ->
            val head = values[index]
            val tail = values.filterIndexed { itemIndex, _ -> itemIndex != index }
            permutations(tail).forEach { tailPermutation ->
                result += listOf(head) + tailPermutation
            }
        }
        return result
    }

    private fun completion(
        id: String,
        cycle: Long?,
        points: Int,
        updatedAt: Long,
        completionTime: Long = updatedAt,
        reversedAt: Long? = null,
        reversedByUserId: String? = null
    ) = ExportTaskCompletion(
        id = id,
        taskId = taskId,
        userId = userId,
        taskType = "ROUTINE",
        completionTime = completionTime,
        weekStartDate = 1L,
        basePoints = points,
        totalPoints = points,
        updatedAt = updatedAt,
        completionCycle = cycle,
        reversedAt = reversedAt,
        reversedByUserId = reversedByUserId
    )

    private fun task(status: String, cycle: Long) = ExportTask(
        id = taskId,
        title = "Task",
        createdByUserId = "parent",
        assignedToUserId = userId,
        status = status,
        completionCycle = cycle
    )

    private fun taskWithAttachment(
        id: String,
        addedAt: Long,
        name: String,
        mimeType: String? = "text/plain"
    ) = ExportTask(
        id = taskId,
        title = "Task",
        createdByUserId = "parent",
        assignedToUserId = userId,
        status = "TODO",
        attachments = listOf(
            ExportTaskAttachment(
                id = id,
                name = name,
                uri = "uri",
                mimeType = mimeType,
                addedAt = addedAt
            )
        )
    )

    private fun taskWithComment(id: String, createdAt: Long, message: String) = ExportTask(
        id = taskId,
        title = "Task",
        createdByUserId = "parent",
        assignedToUserId = userId,
        status = "TODO",
        comments = listOf(
            ExportTaskComment(
                id = id,
                authorId = "parent",
                message = message,
                createdAt = createdAt
            )
        )
    )
}
