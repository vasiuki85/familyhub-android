package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus

enum class TaskListSectionKey {
    OVERDUE,
    TODAY,
    TOMORROW,
    LATER,
    NO_DATE,
    TODO,
    WAITING,
    DONE,
}

data class TaskListSection(
    val key: TaskListSectionKey,
    val tasks: List<Task>,
)

object TaskListSectionMapper {
    fun map(
        tasks: List<Task>,
        sortType: TaskSortType,
    ): List<TaskListSection> {
        if (tasks.isEmpty() || sortType != TaskSortType.STATUS) return emptyList()

        val groups = linkedMapOf<TaskListSectionKey, MutableList<Task>>()
        tasks.forEach { task ->
            val key = when (task.status) {
                TaskStatus.TODO -> TaskListSectionKey.TODO
                TaskStatus.WAITING_FOR_APPROVAL -> TaskListSectionKey.WAITING
                TaskStatus.DONE -> TaskListSectionKey.DONE
            }
            groups.getOrPut(key) { mutableListOf() }.add(task)
        }

        val order = listOf(
            TaskListSectionKey.TODO,
            TaskListSectionKey.WAITING,
            TaskListSectionKey.DONE,
        )

        return order.mapNotNull { key ->
            groups[key]?.takeIf { it.isNotEmpty() }?.let { TaskListSection(key, it) }
        }
    }
}
