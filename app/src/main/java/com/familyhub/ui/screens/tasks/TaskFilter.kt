package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus

enum class TaskFilterType {
    ALL,
    TODO,
    DONE
}

enum class TaskSortType {
    DATE_NEWEST,    // По дате создания (новые сначала)
    DATE_OLDEST,    // По дате создания (старые сначала)
    STATUS,         // По статусу (TODO сначала)
    ASSIGNEE,       // По исполнителю (алфавит)
    PRIORITY,       // По приоритету (высокий выше)
    MANUAL,         // Ручной порядок (orderIndex)
}

fun List<Task>.filterTasks(filterType: TaskFilterType): List<Task> {
    return when (filterType) {
        TaskFilterType.ALL -> this
        TaskFilterType.TODO -> this.filter { it.status == TaskStatus.TODO }
        TaskFilterType.DONE -> this.filter { it.status == TaskStatus.DONE }
    }
}

fun List<Task>.sortTasks(sortType: TaskSortType): List<Task> {
    return when (sortType) {
        TaskSortType.DATE_NEWEST -> this.sortedByDescending { it.createdAt }
        TaskSortType.DATE_OLDEST -> this.sortedBy { it.createdAt }
        TaskSortType.STATUS -> this.sortedBy { it.status } // TODO идёт перед DONE
        TaskSortType.ASSIGNEE -> this.sortedBy { it.assignedToUserId }
        TaskSortType.PRIORITY -> this.sortedByDescending { it.priority.order }
        TaskSortType.MANUAL -> this.sortedWith(
            compareBy<Task> { it.orderIndex }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
    }
}

