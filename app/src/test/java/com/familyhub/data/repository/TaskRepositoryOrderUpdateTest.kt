package com.familyhub.data.repository

import com.familyhub.domain.model.TaskOrderChange
import com.familyhub.domain.model.TaskOrderConflictException
import com.familyhub.domain.model.TaskOrderSnapshotEntry
import com.familyhub.domain.model.TaskOrderTransactionGuard
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure plan-validation coverage for the order-only path.
 * Room transaction behaviour lives in androidTest.
 */
class TaskRepositoryOrderUpdateTest {

    @Test
    fun validatePlan_rejectsDuplicateSnapshotIds() {
        assertThrows(TaskOrderConflictException::class.java) {
            TaskOrderTransactionGuard.validatePlan(
                expectedSnapshot = listOf(
                    TaskOrderSnapshotEntry("a", 1),
                    TaskOrderSnapshotEntry("a", 2),
                ),
                changes = listOf(TaskOrderChange("a", 1, 2)),
            )
        }
    }

    @Test
    fun validatePlan_rejectsChangeOutsideSnapshot() {
        assertThrows(TaskOrderConflictException::class.java) {
            TaskOrderTransactionGuard.validatePlan(
                expectedSnapshot = listOf(TaskOrderSnapshotEntry("a", 1)),
                changes = listOf(TaskOrderChange("b", 1, 2)),
            )
        }
    }

    @Test
    fun validatePlan_rejectsExpectedOrderMismatch() {
        assertThrows(TaskOrderConflictException::class.java) {
            TaskOrderTransactionGuard.validatePlan(
                expectedSnapshot = listOf(TaskOrderSnapshotEntry("a", 1)),
                changes = listOf(TaskOrderChange("a", 99, 2)),
            )
        }
    }

    @Test
    fun validatePlan_rejectsDuplicateChangeIds() {
        assertThrows(TaskOrderConflictException::class.java) {
            TaskOrderTransactionGuard.validatePlan(
                expectedSnapshot = listOf(
                    TaskOrderSnapshotEntry("a", 1),
                    TaskOrderSnapshotEntry("b", 2),
                ),
                changes = listOf(
                    TaskOrderChange("a", 1, 2),
                    TaskOrderChange("a", 1, 3),
                ),
            )
        }
    }

    @Test
    fun validatePlan_rejectsNonUniqueProjectedOrderIndex() {
        assertThrows(TaskOrderConflictException::class.java) {
            TaskOrderTransactionGuard.validatePlan(
                expectedSnapshot = listOf(
                    TaskOrderSnapshotEntry("a", 1),
                    TaskOrderSnapshotEntry("b", 2),
                ),
                changes = listOf(
                    TaskOrderChange("a", 1, 2),
                ),
            )
        }
    }

    @Test
    fun validatePlan_acceptsSwap() {
        TaskOrderTransactionGuard.validatePlan(
            expectedSnapshot = listOf(
                TaskOrderSnapshotEntry("a", 1),
                TaskOrderSnapshotEntry("b", 2),
            ),
            changes = listOf(
                TaskOrderChange("a", 1, 2),
                TaskOrderChange("b", 2, 1),
            ),
        )
    }
}
