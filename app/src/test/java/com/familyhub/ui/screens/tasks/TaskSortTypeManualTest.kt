package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSortTypeManualTest {

    @Test
    fun manual_sortsByOrderIndexThenCreatedAtThenId() {
        val tasks = listOf(
            task(id = "b", orderIndex = 1, createdAt = 2),
            task(id = "a", orderIndex = 1, createdAt = 1),
            task(id = "c", orderIndex = 0, createdAt = 9),
            task(id = "d", orderIndex = 2, createdAt = 1),
        )

        val sorted = tasks.sortTasks(TaskSortType.MANUAL)

        assertEquals(listOf("c", "a", "b", "d"), sorted.map { it.id })
    }

    @Test
    fun dateNewest_unchanged() {
        val newer = task(id = "new", orderIndex = 0, createdAt = 20)
        val older = task(id = "old", orderIndex = 5, createdAt = 10)

        assertEquals(
            listOf(newer, older),
            listOf(older, newer).sortTasks(TaskSortType.DATE_NEWEST),
        )
    }

    @Test
    fun priority_unchanged() {
        val low = task(id = "low", orderIndex = 0, createdAt = 1, priority = TaskPriority.LOW)
        val high = task(id = "high", orderIndex = 0, createdAt = 1, priority = TaskPriority.HIGH)

        assertEquals(
            listOf(high, low),
            listOf(low, high).sortTasks(TaskSortType.PRIORITY),
        )
    }

    private fun task(
        id: String,
        orderIndex: Int,
        createdAt: Long,
        priority: TaskPriority = TaskPriority.MEDIUM,
    ): Task = Task(
        id = id,
        title = id,
        description = null,
        createdByUserId = "creator",
        assignedToUserId = "user",
        dueDate = null,
        repeatRule = RepeatRule.NONE,
        status = TaskStatus.TODO,
        createdAt = createdAt,
        orderIndex = orderIndex,
        taskType = TaskType.ROUTINE,
        priority = priority,
    )
}
