package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskManualReorderSessionTest {

    @Test
    fun cancel_clearsProductionBarrierSession() {
        val barrier = TaskManualReorderBarrier()
        barrier.begin(sampleSession())
        assertNotNull(barrier.peekSession())

        barrier.cancel()

        assertNull(barrier.peekSession())
        assertNull(barrier.takeSession())
    }

    @Test
    fun inFlightBarrier_allowsOnlyOneAcquireUntilRelease() {
        val barrier = TaskManualReorderBarrier()
        assertTrue(barrier.tryAcquireInFlight())
        assertTrue(barrier.isInFlight())
        assertFalse(barrier.tryAcquireInFlight())

        barrier.releaseInFlight()

        assertFalse(barrier.isInFlight())
        assertTrue(barrier.tryAcquireInFlight())
    }

    @Test
    fun evaluate_staleSnapshot_returnsNull() {
        val tasks = listOf(task("a", 1), task("b", 2))
        val session = sampleSession(
            snapshot = TaskManualReorderPlanner.snapshotOf(tasks),
        )
        val ui = reorderReadyUi(
            allTasks = listOf(task("a", 1), task("b", 2), task("c", 3)),
        )

        assertNull(TaskManualReorderCommitEvaluator.evaluate(session, ui, toIndex = 1))
    }

    @Test
    fun evaluate_userChanged_returnsNull() {
        val tasks = listOf(task("a", 1), task("b", 2))
        val session = sampleSession(
            currentUserId = "user-1",
            snapshot = TaskManualReorderPlanner.snapshotOf(tasks),
        )
        val ui = reorderReadyUi(allTasks = tasks, currentUserId = "user-2")

        assertNull(TaskManualReorderCommitEvaluator.evaluate(session, ui, toIndex = 1))
    }

    @Test
    fun evaluate_draggedTaskNotAtStartIndex_returnsNull() {
        val tasks = listOf(task("a", 1), task("b", 2))
        val session = sampleSession(
            draggedTaskId = "a",
            startIndex = 0,
            snapshot = TaskManualReorderPlanner.snapshotOf(tasks),
        )
        val ui = reorderReadyUi(allTasks = listOf(task("b", 2), task("a", 1)))

        assertNull(TaskManualReorderCommitEvaluator.evaluate(session, ui, toIndex = 1))
    }

    @Test
    fun evaluate_validMove_returnsPersistRequest() {
        val tasks = listOf(task("a", 1), task("b", 2))
        val session = sampleSession(
            draggedTaskId = "a",
            startIndex = 0,
            snapshot = TaskManualReorderPlanner.snapshotOf(tasks),
        )
        val ui = reorderReadyUi(allTasks = tasks)

        val persist = TaskManualReorderCommitEvaluator.evaluate(session, ui, toIndex = 1)
        assertNotNull(persist)
        assertEquals("user-1", persist!!.currentUserId)
        assertEquals(session.snapshot, persist.expectedSnapshot)
        assertTrue(persist.changes.isNotEmpty())
    }

    @Test
    fun takeSession_afterCommitPath_leavesBarrierEmpty() {
        val barrier = TaskManualReorderBarrier()
        barrier.begin(sampleSession())
        val taken = barrier.takeSession()
        assertNotNull(taken)
        assertNull(barrier.peekSession())
    }

    private fun sampleSession(
        draggedTaskId: String = "a",
        startIndex: Int = 0,
        currentUserId: String = "user-1",
        snapshot: List<com.familyhub.domain.model.TaskOrderSnapshotEntry> =
            TaskManualReorderPlanner.snapshotOf(listOf(task("a", 1), task("b", 2))),
    ) = TaskManualDragSession(
        draggedTaskId = draggedTaskId,
        startIndex = startIndex,
        currentUserId = currentUserId,
        snapshot = snapshot,
    )

    private fun reorderReadyUi(
        allTasks: List<Task>,
        currentUserId: String = "user-1",
    ) = TasksUiState(
        isLoading = false,
        currentUserId = currentUserId,
        currentUserRole = MemberRole.PARENT,
        selectedTab = TaskTab.ALL,
        filterType = TaskFilterType.ALL,
        sortType = TaskSortType.MANUAL,
        searchQuery = "",
        selectedCategoryFilterId = null,
        allTasks = allTasks,
        manualReorderInFlight = false,
    )

    private fun task(id: String, orderIndex: Int) = Task(
        id = id,
        title = id,
        description = null,
        createdByUserId = "user-1",
        assignedToUserId = "user-1",
        dueDate = null,
        repeatRule = RepeatRule.NONE,
        status = TaskStatus.TODO,
        createdAt = 1L,
        orderIndex = orderIndex,
    )
}
