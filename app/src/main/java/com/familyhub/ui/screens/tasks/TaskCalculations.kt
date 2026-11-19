package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.utils.filterByVisibility

internal fun List<Task>.filterByCategory(categoryId: String?): List<Task> {
    return if (categoryId.isNullOrEmpty()) {
        this
    } else {
        this.filter { it.categoryId == categoryId }
    }
}

data class TaskListProjection(
    val awaitingBase: List<Task>,
    val myBase: List<Task>,
    val allBase: List<Task>,
    val awaiting: List<Task>,
    val myTasks: List<Task>,
    val allTasks: List<Task>,
) {
    companion object {
        fun build(
            allTasksRaw: List<Task>,
            currentUserId: String,
            filterType: TaskFilterType,
            sortType: TaskSortType,
            searchQuery: String,
            selectedCategoryFilterId: String?,
        ): TaskListProjection {
            val myBase = allTasksRaw
                .filter { it.assignedToUserId == currentUserId }
                .filterByVisibility(currentUserId)
                .distinctBy { it.id }

            val allBase = allTasksRaw
                .filterByVisibility(currentUserId)
                .distinctBy { it.id }

            val awaitingBase = allTasksRaw
                .filterByVisibility(currentUserId)
                .filter { it.status == TaskStatus.WAITING_FOR_APPROVAL }
                .distinctBy { it.id }

            val myTasks = myBase
                .filterTasks(filterType)
                .filterByCategory(selectedCategoryFilterId)
                .sortTasks(sortType)
                .filterBySearch(searchQuery)
                .distinctBy { it.id }

            val allTasks = allBase
                .filterTasks(filterType)
                .filterByCategory(selectedCategoryFilterId)
                .sortTasks(sortType)
                .filterBySearch(searchQuery)
                .distinctBy { it.id }

            val awaiting = awaitingBase
                .sortTasks(sortType)
                .filterBySearch(searchQuery)
                .distinctBy { it.id }

            return TaskListProjection(
                awaitingBase = awaitingBase,
                myBase = myBase,
                allBase = allBase,
                awaiting = awaiting,
                myTasks = myTasks,
                allTasks = allTasks,
            )
        }
    }
}

internal fun List<Task>.filterBySearch(searchQuery: String): List<Task> {
    if (searchQuery.isBlank()) return this
    return filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
            it.description?.contains(searchQuery, ignoreCase = true) == true
    }
}

internal fun calculateTaskLists(
    allTasksRaw: List<Task>,
    currentUserId: String,
    filterType: TaskFilterType,
    sortType: TaskSortType,
    searchQuery: String,
    selectedCategoryFilterId: String?,
): Triple<List<Task>, List<Task>, List<Task>> {
    val projection = TaskListProjection.build(
        allTasksRaw = allTasksRaw,
        currentUserId = currentUserId,
        filterType = filterType,
        sortType = sortType,
        searchQuery = searchQuery,
        selectedCategoryFilterId = selectedCategoryFilterId,
    )
    return Triple(projection.awaiting, projection.myTasks, projection.allTasks)
}
