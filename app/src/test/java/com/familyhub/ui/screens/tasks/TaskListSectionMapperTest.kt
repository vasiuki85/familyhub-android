package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListSectionMapperTest {
    private val now = 1_700_000_000_000L

    @Test
    fun nonStatusSorts_preserveTheExistingGlobalOrderWithoutSections() {
        listOf(
            TaskSortType.DATE_NEWEST,
            TaskSortType.DATE_OLDEST,
            TaskSortType.ASSIGNEE,
            TaskSortType.PRIORITY,
            TaskSortType.MANUAL,
        ).forEach { sortType ->
            val sections = TaskListSectionMapper.map(
                tasks = listOf(task("first"), task("second")),
                sortType = sortType,
            )

            assertTrue("Expected no visual regrouping for $sortType", sections.isEmpty())
        }
    }

    @Test
    fun statusSort_groupsTodoWaitingDone() {
        val sections = TaskListSectionMapper.map(
            tasks = listOf(
                task("done", status = TaskStatus.DONE),
                task("todo", status = TaskStatus.TODO),
                task("waiting", status = TaskStatus.WAITING_FOR_APPROVAL),
            ),
            sortType = TaskSortType.STATUS,
        )

        assertEquals(
            listOf(
                TaskListSectionKey.TODO,
                TaskListSectionKey.WAITING,
                TaskListSectionKey.DONE,
            ),
            sections.map { it.key },
        )
    }

    private fun task(
        id: String,
        dueDate: Long? = now,
        status: TaskStatus = TaskStatus.TODO,
    ) = Task(
        id = id,
        title = id,
        description = null,
        createdByUserId = "parent",
        assignedToUserId = "child",
        dueDate = dueDate,
        repeatRule = RepeatRule.NONE,
        status = status,
        createdAt = now,
    )
}
