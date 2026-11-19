package com.familyhub.domain.model

data class TaskOrderSnapshotEntry(
    val taskId: String,
    val orderIndex: Int,
)

data class TaskOrderChange(
    val taskId: String,
    val expectedOrderIndex: Int,
    val newOrderIndex: Int,
)

class TaskOrderConflictException(
    message: String = "Task order conflict",
) : Exception(message)

object TaskOrderTransactionGuard {
    fun ensureSnapshotMatches(
        expected: List<TaskOrderSnapshotEntry>,
        current: List<TaskOrderSnapshotEntry>,
    ) {
        if (expected.size != current.size || expected.toSet() != current.toSet()) {
            throw TaskOrderConflictException("Reorder cohort snapshot mismatch")
        }
    }

    /**
     * Rejects an invalid reorder plan before any CAS UPDATE.
     * Empty [changes] is a caller-side no-op and should not reach this method.
     */
    fun validatePlan(
        expectedSnapshot: List<TaskOrderSnapshotEntry>,
        changes: List<TaskOrderChange>,
    ) {
        if (changes.isEmpty()) {
            throw TaskOrderConflictException("Reorder plan has no changes")
        }

        val snapshotById = HashMap<String, Int>(expectedSnapshot.size * 2)
        for (entry in expectedSnapshot) {
            if (snapshotById.put(entry.taskId, entry.orderIndex) != null) {
                throw TaskOrderConflictException(
                    "Duplicate taskId in expectedSnapshot: ${entry.taskId}"
                )
            }
        }

        val changeIds = HashSet<String>(changes.size * 2)
        val projected = HashMap(snapshotById)
        for (change in changes) {
            if (!changeIds.add(change.taskId)) {
                throw TaskOrderConflictException("Duplicate taskId in changes: ${change.taskId}")
            }
            val expected = snapshotById[change.taskId]
                ?: throw TaskOrderConflictException(
                    "Change taskId not in expectedSnapshot: ${change.taskId}"
                )
            if (expected != change.expectedOrderIndex) {
                throw TaskOrderConflictException(
                    "expectedOrderIndex mismatch for ${change.taskId}: " +
                        "plan=${change.expectedOrderIndex}, snapshot=$expected"
                )
            }
            projected[change.taskId] = change.newOrderIndex
        }

        if (projected.values.toSet().size != projected.size) {
            throw TaskOrderConflictException("Projected orderIndex values are not unique")
        }
    }
}
