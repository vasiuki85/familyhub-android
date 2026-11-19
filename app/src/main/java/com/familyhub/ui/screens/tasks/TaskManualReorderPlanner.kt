package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskOrderChange
import com.familyhub.domain.model.TaskOrderSnapshotEntry

sealed interface TaskManualReorderPlan {
    data object NoOp : TaskManualReorderPlan
    data object Invalid : TaskManualReorderPlan
    data class Applied(
        val changes: List<TaskOrderChange>,
    ) : TaskManualReorderPlan
}

object TaskManualReorderPlanner {
    fun snapshotOf(tasks: List<Task>): List<TaskOrderSnapshotEntry> =
        tasks.map { TaskOrderSnapshotEntry(taskId = it.id, orderIndex = it.orderIndex) }

    fun snapshotsEqual(
        left: List<TaskOrderSnapshotEntry>,
        right: List<TaskOrderSnapshotEntry>,
    ): Boolean = left.size == right.size && left.zip(right).all { (a, b) -> a == b }

    fun plan(
        displayedTasks: List<Task>,
        fromIndex: Int,
        toIndex: Int,
    ): TaskManualReorderPlan {
        if (displayedTasks.isEmpty()) return TaskManualReorderPlan.Invalid
        if (fromIndex !in displayedTasks.indices || toIndex !in displayedTasks.indices) {
            return TaskManualReorderPlan.Invalid
        }

        val expectedById = HashMap<String, Int>(displayedTasks.size * 2)
        for (task in displayedTasks) {
            if (expectedById.put(task.id, task.orderIndex) != null) {
                return TaskManualReorderPlan.Invalid
            }
        }
        if (fromIndex == toIndex) return TaskManualReorderPlan.NoOp

        val reordered = displayedTasks.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)

        val slots = resolveSlots(displayedTasks)
        val changes = ArrayList<TaskOrderChange>(reordered.size)
        for (index in reordered.indices) {
            val task = reordered[index]
            val expected = expectedById.getValue(task.id)
            val newIndex = slots[index]
            if (expected != newIndex) {
                changes.add(
                    TaskOrderChange(
                        taskId = task.id,
                        expectedOrderIndex = expected,
                        newOrderIndex = newIndex,
                    )
                )
            }
        }
        if (changes.isEmpty()) return TaskManualReorderPlan.NoOp
        return TaskManualReorderPlan.Applied(changes = changes)
    }

    private fun resolveSlots(displayedTasks: List<Task>): List<Int> {
        val existing = displayedTasks.map { it.orderIndex }
        val uniqueSorted = existing.distinct().sorted()
        return if (uniqueSorted.size == existing.size) {
            uniqueSorted
        } else {
            List(displayedTasks.size) { it }
        }
    }
}
