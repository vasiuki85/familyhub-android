package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskOrderChange
import com.familyhub.domain.model.TaskOrderSnapshotEntry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Production drag-session + single in-flight mutation barrier for manual reorder.
 */
class TaskManualReorderBarrier {
    private val sessionRef = AtomicReference<TaskManualDragSession?>(null)
    private val inFlight = AtomicBoolean(false)

    fun begin(session: TaskManualDragSession) {
        sessionRef.set(session)
    }

    fun cancel() {
        sessionRef.set(null)
    }

    fun takeSession(): TaskManualDragSession? = sessionRef.getAndSet(null)

    fun peekSession(): TaskManualDragSession? = sessionRef.get()

    fun tryAcquireInFlight(): Boolean = inFlight.compareAndSet(false, true)

    fun releaseInFlight() {
        inFlight.set(false)
    }

    fun isInFlight(): Boolean = inFlight.get()
}

data class TaskManualReorderPersistRequest(
    val expectedSnapshot: List<TaskOrderSnapshotEntry>,
    val changes: List<TaskOrderChange>,
    val currentUserId: String,
)

object TaskManualReorderCommitEvaluator {
    fun displayedTasksForTab(ui: TasksUiState, effectiveTab: TaskTab): List<Task> =
        when (effectiveTab) {
            TaskTab.MY -> ui.myTasks
            TaskTab.ALL -> ui.allTasks
            TaskTab.AWAITING -> ui.awaitingApprovalTasks
        }

    /**
     * Returns a persist request when commit may proceed; null means no-op / Invalid.
     */
    fun evaluate(
        session: TaskManualDragSession,
        ui: TasksUiState,
        toIndex: Int,
    ): TaskManualReorderPersistRequest? {
        if (session.currentUserId != ui.currentUserId) return null

        val effectiveTab = TaskTabPolicy.resolveEffectiveTab(ui.currentUserRole, ui.selectedTab)
        val displayedTasks = displayedTasksForTab(ui, effectiveTab)

        val canReorder = TaskManualReorderPolicy.canReorder(
            isLoading = ui.isLoading || ui.manualReorderInFlight,
            role = ui.currentUserRole,
            effectiveTab = effectiveTab,
            sortType = ui.sortType,
            filterType = ui.filterType,
            searchQuery = ui.searchQuery,
            selectedCategoryFilterId = ui.selectedCategoryFilterId,
            displayedCount = displayedTasks.size,
        )
        if (!canReorder) return null

        val currentSnapshot = TaskManualReorderPlanner.snapshotOf(displayedTasks)
        if (!TaskManualReorderPlanner.snapshotsEqual(session.snapshot, currentSnapshot)) {
            return null
        }
        if (displayedTasks.getOrNull(session.startIndex)?.id != session.draggedTaskId) {
            return null
        }

        return when (
            val plan = TaskManualReorderPlanner.plan(
                displayedTasks = displayedTasks,
                fromIndex = session.startIndex,
                toIndex = toIndex,
            )
        ) {
            is TaskManualReorderPlan.Applied -> {
                if (plan.changes.isEmpty()) null
                else TaskManualReorderPersistRequest(
                    expectedSnapshot = session.snapshot,
                    changes = plan.changes,
                    currentUserId = ui.currentUserId,
                )
            }
            TaskManualReorderPlan.NoOp, TaskManualReorderPlan.Invalid -> null
        }
    }
}
