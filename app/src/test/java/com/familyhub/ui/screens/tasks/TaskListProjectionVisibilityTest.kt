package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListProjectionVisibilityTest {

    @Test
    fun foreignPrivateWaiting_isAbsentFromAwaitingBaseAndAwaiting() {
        val foreignPrivate = sampleTask(
            id = "foreign",
            assignedTo = "other",
            createdBy = "other",
            status = TaskStatus.WAITING_FOR_APPROVAL,
            isPrivate = true,
        )
        val ownWaiting = sampleTask(
            id = "own",
            assignedTo = USER,
            createdBy = USER,
            status = TaskStatus.WAITING_FOR_APPROVAL,
            isPrivate = true,
        )

        val projection = TaskListProjection.build(
            allTasksRaw = listOf(foreignPrivate, ownWaiting),
            currentUserId = USER,
            filterType = TaskFilterType.ALL,
            sortType = TaskSortType.DATE_NEWEST,
            searchQuery = "",
            selectedCategoryFilterId = null,
        )

        assertFalse(projection.awaitingBase.any { it.id == "foreign" })
        assertFalse(projection.awaiting.any { it.id == "foreign" })
        assertTrue(projection.awaitingBase.any { it.id == "own" })
        assertTrue(projection.awaiting.any { it.id == "own" })
    }

    @Test
    fun assignedPrivateWaiting_remainsVisible() {
        val assignedPrivate = sampleTask(
            id = "assigned",
            assignedTo = USER,
            createdBy = "parent",
            status = TaskStatus.WAITING_FOR_APPROVAL,
            isPrivate = true,
        )

        val projection = TaskListProjection.build(
            allTasksRaw = listOf(assignedPrivate),
            currentUserId = USER,
            filterType = TaskFilterType.ALL,
            sortType = TaskSortType.DATE_NEWEST,
            searchQuery = "",
            selectedCategoryFilterId = null,
        )

        assertEquals(listOf(assignedPrivate), projection.awaitingBase)
        assertEquals(listOf(assignedPrivate), projection.awaiting)
    }

    private fun sampleTask(
        id: String,
        assignedTo: String,
        createdBy: String,
        status: TaskStatus,
        isPrivate: Boolean,
    ): Task = Task(
        id = id,
        title = "Task $id",
        description = null,
        createdByUserId = createdBy,
        assignedToUserId = assignedTo,
        dueDate = null,
        repeatRule = RepeatRule.NONE,
        status = status,
        createdAt = 1L,
        orderIndex = 0,
        isPrivate = isPrivate,
        taskType = TaskType.ROUTINE,
        priority = TaskPriority.MEDIUM,
    )

    private companion object {
        const val USER = "user-1"
    }
}
