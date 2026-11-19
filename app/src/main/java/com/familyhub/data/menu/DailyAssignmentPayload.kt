package com.familyhub.data.menu

import com.familyhub.data.local.entity.DailyAssignmentEntity
import com.familyhub.domain.model.DailyAssignment

/**
 * Content identity for daily assignments. [updatedAt] is intentionally excluded.
 */
internal data class DailyAssignmentPayload(
    val weekStartMillis: Long,
    val dayOfWeek: Int,
    val assignedMemberId: String?,
    val rewardPoints: Int?,
    val isRated: Boolean,
) {
    companion object {
        fun from(entity: DailyAssignmentEntity): DailyAssignmentPayload = DailyAssignmentPayload(
            weekStartMillis = entity.weekStartMillis,
            dayOfWeek = entity.dayOfWeek,
            assignedMemberId = entity.assignedMemberId,
            rewardPoints = entity.rewardPoints,
            isRated = entity.isRated,
        )

        fun from(assignment: DailyAssignment): DailyAssignmentPayload = DailyAssignmentPayload(
            weekStartMillis = assignment.weekStartMillis,
            dayOfWeek = assignment.dayOfWeek,
            assignedMemberId = assignment.assignedMemberId,
            rewardPoints = assignment.rewardPoints,
            isRated = assignment.isRated,
        )

        fun samePersisted(entity: DailyAssignmentEntity, desired: DailyAssignment): Boolean =
            from(entity) == from(desired)
    }
}
