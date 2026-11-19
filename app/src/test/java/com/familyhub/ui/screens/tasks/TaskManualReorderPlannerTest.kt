package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskOrderChange
import com.familyhub.domain.model.TaskOrderConflictException
import com.familyhub.domain.model.TaskOrderSnapshotEntry
import com.familyhub.domain.model.TaskOrderTransactionGuard
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskManualReorderPlannerTest {

    @Test
    fun moveDown_preservesRelativeOrderAndSlots() {
        val tasks = listOf(
            task("a", orderIndex = 10),
            task("b", orderIndex = 20),
            task("c", orderIndex = 30),
        )

        val plan = TaskManualReorderPlanner.plan(tasks, fromIndex = 0, toIndex = 2)
            as TaskManualReorderPlan.Applied

        assertEquals(
            listOf(
                TaskOrderChange("b", 20, 10),
                TaskOrderChange("c", 30, 20),
                TaskOrderChange("a", 10, 30),
            ),
            plan.changes,
        )
    }

    @Test
    fun moveUp_reusesUniqueSlots() {
        val tasks = listOf(
            task("a", orderIndex = 10),
            task("b", orderIndex = 20),
            task("c", orderIndex = 30),
        )

        val plan = TaskManualReorderPlanner.plan(tasks, fromIndex = 2, toIndex = 0)
            as TaskManualReorderPlan.Applied

        assertEquals(
            listOf(
                TaskOrderChange("c", 30, 10),
                TaskOrderChange("a", 10, 20),
                TaskOrderChange("b", 20, 30),
            ),
            plan.changes,
        )
    }

    @Test
    fun fromEqualsTo_returnsNoOp() {
        val tasks = listOf(task("a", 1), task("b", 2))
        assertEquals(
            TaskManualReorderPlan.NoOp,
            TaskManualReorderPlanner.plan(tasks, fromIndex = 1, toIndex = 1),
        )
    }

    @Test
    fun invalidIndices_returnInvalid() {
        val tasks = listOf(task("a", 1), task("b", 2))
        assertEquals(TaskManualReorderPlan.Invalid, TaskManualReorderPlanner.plan(tasks, -1, 0))
        assertEquals(TaskManualReorderPlan.Invalid, TaskManualReorderPlanner.plan(tasks, 0, 5))
        assertEquals(TaskManualReorderPlan.Invalid, TaskManualReorderPlanner.plan(emptyList(), 0, 0))
    }

    @Test
    fun duplicateIds_returnInvalid() {
        val tasks = listOf(task("a", 1), task("a", 2))
        assertEquals(TaskManualReorderPlan.Invalid, TaskManualReorderPlanner.plan(tasks, 0, 1))
    }

    @Test
    fun returnsOnlyOrderChangeTriples() {
        val tasks = listOf(
            task("a", orderIndex = 1, title = "Alpha"),
            task("b", orderIndex = 2, title = "Beta"),
        )
        val plan = TaskManualReorderPlanner.plan(tasks, 0, 1) as TaskManualReorderPlan.Applied
        assertEquals(
            listOf(
                TaskOrderChange("b", 2, 1),
                TaskOrderChange("a", 1, 2),
            ),
            plan.changes,
        )
        plan.changes.forEach { change ->
            assertTrue(change.taskId.isNotBlank())
            assertTrue(change.expectedOrderIndex != change.newOrderIndex)
        }
    }

    @Test
    fun reusesUniqueSlotValues() {
        val tasks = listOf(
            task("a", orderIndex = 100),
            task("b", orderIndex = 200),
            task("c", orderIndex = 300),
        )
        // Move b before a: sorted unique slots {100,200,300} are reused; c keeps 300.
        val plan = TaskManualReorderPlanner.plan(tasks, 1, 0) as TaskManualReorderPlan.Applied
        assertEquals(
            listOf(
                TaskOrderChange("b", 200, 100),
                TaskOrderChange("a", 100, 200),
            ),
            plan.changes,
        )
        assertFalse(plan.changes.any { it.taskId == "c" })
    }

    @Test
    fun duplicateOrderIndices_normalizedSequentially() {
        val tasks = listOf(
            task("a", orderIndex = 1),
            task("b", orderIndex = 1),
            task("c", orderIndex = 2),
        )
        val plan = TaskManualReorderPlanner.plan(tasks, 0, 2) as TaskManualReorderPlan.Applied
        assertEquals(listOf(0, 1, 2), plan.changes.map { it.newOrderIndex }.sorted())
    }

    @Test
    fun changedEntries_excludeUnchangedRows() {
        val tasks = listOf(
            task("a", orderIndex = 10),
            task("b", orderIndex = 20),
            task("c", orderIndex = 30),
        )
        val plan = TaskManualReorderPlanner.plan(tasks, 1, 0) as TaskManualReorderPlan.Applied
        assertFalse(plan.changes.any { it.taskId == "c" })
        assertTrue(plan.changes.any { it.taskId == "a" })
        assertTrue(plan.changes.any { it.taskId == "b" })
    }

    @Test
    fun snapshotMismatch_throwsConflict() {
        val expected = listOf(
            TaskOrderSnapshotEntry("a", 1),
            TaskOrderSnapshotEntry("b", 2),
        )
        assertThrows(TaskOrderConflictException::class.java) {
            TaskOrderTransactionGuard.ensureSnapshotMatches(
                expected,
                listOf(TaskOrderSnapshotEntry("a", 1)),
            )
        }
        assertThrows(TaskOrderConflictException::class.java) {
            TaskOrderTransactionGuard.ensureSnapshotMatches(
                expected,
                listOf(
                    TaskOrderSnapshotEntry("a", 1),
                    TaskOrderSnapshotEntry("b", 99),
                ),
            )
        }
    }

    @Test
    fun secondSnapshotAfterCommit_isDetectedAsStale() {
        val first = listOf(
            TaskOrderSnapshotEntry("a", 10),
            TaskOrderSnapshotEntry("b", 20),
        )
        val afterCommit = listOf(
            TaskOrderSnapshotEntry("b", 10),
            TaskOrderSnapshotEntry("a", 20),
        )
        assertFalse(TaskManualReorderPlanner.snapshotsEqual(first, afterCommit))
        assertThrows(TaskOrderConflictException::class.java) {
            TaskOrderTransactionGuard.ensureSnapshotMatches(first, afterCommit)
        }
    }

    private fun task(
        id: String,
        orderIndex: Int,
        title: String = "Task $id",
    ): Task = Task(
        id = id,
        title = title,
        description = null,
        createdByUserId = "creator",
        assignedToUserId = "user",
        dueDate = null,
        repeatRule = RepeatRule.NONE,
        status = TaskStatus.TODO,
        createdAt = id.hashCode().toLong(),
        orderIndex = orderIndex,
        taskType = TaskType.ROUTINE,
        priority = TaskPriority.MEDIUM,
    )
}
