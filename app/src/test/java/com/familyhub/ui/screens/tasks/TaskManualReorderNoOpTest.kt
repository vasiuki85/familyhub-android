package com.familyhub.ui.screens.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskManualReorderNoOpTest {

    @Test
    fun plannerNoOp_hasNoChangesToPersist() {
        val tasks = listOf(
            com.familyhub.domain.model.Task(
                id = "a",
                title = "a",
                description = null,
                createdByUserId = "u",
                assignedToUserId = "u",
                dueDate = null,
                repeatRule = com.familyhub.domain.model.RepeatRule.NONE,
                status = com.familyhub.domain.model.TaskStatus.TODO,
                createdAt = 1L,
                orderIndex = 1,
            ),
            com.familyhub.domain.model.Task(
                id = "b",
                title = "b",
                description = null,
                createdByUserId = "u",
                assignedToUserId = "u",
                dueDate = null,
                repeatRule = com.familyhub.domain.model.RepeatRule.NONE,
                status = com.familyhub.domain.model.TaskStatus.TODO,
                createdAt = 1L,
                orderIndex = 2,
            ),
        )
        assertEquals(
            TaskManualReorderPlan.NoOp,
            TaskManualReorderPlanner.plan(tasks, fromIndex = 0, toIndex = 0),
        )
    }
}
