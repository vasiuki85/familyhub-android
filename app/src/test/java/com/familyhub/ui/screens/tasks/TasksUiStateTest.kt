package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test

class TasksUiStateTest {

    @Test
    fun `myTasks filters by selected category for current user`() {
        val tasks = listOf(
            createTask(id = "1", assignedTo = "user1", createdAt = 3, categoryId = "cat1"),
            createTask(id = "2", assignedTo = "user1", createdAt = 2, categoryId = "cat2"),
            createTask(id = "3", assignedTo = "user2", createdAt = 1, categoryId = "cat1"),
        )

        val (_, myTasks, _) = calculateTaskLists(
            allTasksRaw = tasks,
            currentUserId = "user1",
            filterType = TaskFilterType.TODO,
            sortType = TaskSortType.DATE_NEWEST,
            searchQuery = "",
            selectedCategoryFilterId = "cat1"
        )

        assertEquals(listOf(tasks[0]), myTasks)
    }

    @Test
    fun `allTasks applies category filter but keeps visible tasks`() {
        val tasks = listOf(
            createTask(id = "1", assignedTo = "user1", createdAt = 3, categoryId = "cat1"),
            createTask(id = "2", assignedTo = "user2", createdAt = 2, categoryId = "cat1"),
            createTask(id = "3", assignedTo = "user2", createdAt = 1, categoryId = "cat2"),
        )

        val (_, _, allTasks) = calculateTaskLists(
            allTasksRaw = tasks,
            currentUserId = "user1",
            filterType = TaskFilterType.TODO,
            sortType = TaskSortType.DATE_NEWEST,
            searchQuery = "",
            selectedCategoryFilterId = "cat1"
        )

        assertEquals(listOf(tasks[0], tasks[1]), allTasks)
    }

    @Test
    fun `myTasks search applies to title and description ignoring case`() {
        val tasks = listOf(
            createTask(id = "1", assignedTo = "user1", createdAt = 3, title = "Buy Milk"),
            createTask(
                id = "2",
                assignedTo = "user1",
                createdAt = 2,
                title = "Plan dinner",
                description = "Need MILK for dessert"
            ),
            createTask(id = "3", assignedTo = "user1", createdAt = 1, title = "Walk the dog")
        )

        val (_, myTasks, _) = calculateTaskLists(
            allTasksRaw = tasks,
            currentUserId = "user1",
            filterType = TaskFilterType.TODO,
            sortType = TaskSortType.DATE_NEWEST,
            searchQuery = "milk",
            selectedCategoryFilterId = null
        )

        assertEquals(listOf(tasks[0], tasks[1]), myTasks)
    }

    @Test
    fun `allTasks hides private tasks when user is neither creator nor assignee`() {
        val tasks = listOf(
            createTask(id = "1", assignedTo = "user1", createdAt = 3, isPrivate = false),
            createTask(
                id = "2",
                assignedTo = "user2",
                createdAt = 2,
                createdBy = "user2",
                isPrivate = true
            ),
            createTask(
                id = "3",
                assignedTo = "user2",
                createdAt = 1,
                createdBy = "user1",
                isPrivate = true
            )
        )

        val (_, _, allTasks) = calculateTaskLists(
            allTasksRaw = tasks,
            currentUserId = "user1",
            filterType = TaskFilterType.TODO,
            sortType = TaskSortType.DATE_NEWEST,
            searchQuery = "",
            selectedCategoryFilterId = null
        )

        assertEquals(listOf(tasks[0], tasks[2]), allTasks)
    }

    @Test
    fun `allTasks sorting by status keeps TODO tasks before DONE`() {
        val tasks = listOf(
            createTask(id = "1", assignedTo = "user1", createdAt = 1, status = TaskStatus.DONE),
            createTask(id = "2", assignedTo = "user2", createdAt = 2, status = TaskStatus.TODO),
            createTask(id = "3", assignedTo = "user3", createdAt = 3, status = TaskStatus.DONE)
        )

        val (_, _, allTasks) = calculateTaskLists(
            allTasksRaw = tasks,
            currentUserId = "user1",
            filterType = TaskFilterType.ALL,
            sortType = TaskSortType.STATUS,
            searchQuery = "",
            selectedCategoryFilterId = null
        )

        assertEquals(listOf(tasks[1], tasks[0], tasks[2]), allTasks)
    }

    @Test
    fun `allTasks sorting by priority shows high before low`() {
        val low = createTask(id = "1", assignedTo = "user1", createdAt = 1, priority = TaskPriority.LOW)
        val medium = createTask(id = "2", assignedTo = "user2", createdAt = 2, priority = TaskPriority.MEDIUM)
        val high = createTask(id = "3", assignedTo = "user3", createdAt = 3, priority = TaskPriority.HIGH)

        val tasks = listOf(low, medium, high)

        val (_, _, allTasks) = calculateTaskLists(
            allTasksRaw = tasks,
            currentUserId = "user1",
            filterType = TaskFilterType.ALL,
            sortType = TaskSortType.PRIORITY,
            searchQuery = "",
            selectedCategoryFilterId = null
        )

        assertEquals(listOf(high, medium, low), allTasks)
    }

    private fun createTask(
        id: String,
        assignedTo: String,
        createdAt: Long,
        categoryId: String? = null,
        title: String = "Task $id",
        description: String? = null,
        status: TaskStatus = TaskStatus.TODO,
        createdBy: String = "creator",
        isPrivate: Boolean = false,
        priority: TaskPriority = TaskPriority.MEDIUM
    ): Task {
        return Task(
            id = id,
            title = title,
            description = description,
            createdByUserId = createdBy,
            assignedToUserId = assignedTo,
            dueDate = null,
            repeatRule = RepeatRule.NONE,
            status = status,
            createdAt = createdAt,
            orderIndex = 0,
            isPrivate = isPrivate,
            taskType = TaskType.ROUTINE,
            priority = priority,
            categoryId = categoryId
        )
    }
}
