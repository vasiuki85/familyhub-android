package com.familyhub.data.menu

import com.familyhub.data.local.entity.DailyAssignmentEntity
import com.familyhub.domain.model.DailyAssignment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyAssignmentPayloadTest {

    @Test
    fun samePersisted_ignoresInputUpdatedAt() {
        val persisted = entity(updatedAt = 5_000L)
        val desired = assignment(updatedAt = 0L)

        assertTrue(DailyAssignmentPayload.samePersisted(persisted, desired))
    }

    @Test
    fun samePersisted_detectsPayloadDrift() {
        val persisted = entity(assignedMemberId = "member-a")
        val desired = assignment(assignedMemberId = "member-b")

        assertFalse(DailyAssignmentPayload.samePersisted(persisted, desired))
    }

    @Test
    fun payload_comparesAllFieldsExceptUpdatedAt() {
        val base = assignment()
        assertTrue(
            DailyAssignmentPayload.from(base.copy(updatedAt = 1L)) ==
                DailyAssignmentPayload.from(base.copy(updatedAt = 9_999L))
        )
    }

    private fun assignment(
        weekStartMillis: Long = WEEK_START,
        dayOfWeek: Int = 2,
        assignedMemberId: String? = "member-a",
        rewardPoints: Int? = 10,
        isRated: Boolean = true,
        updatedAt: Long = 0L,
    ) = DailyAssignment(
        id = "da-ignored",
        weekStartMillis = weekStartMillis,
        dayOfWeek = dayOfWeek,
        assignedMemberId = assignedMemberId,
        rewardPoints = rewardPoints,
        isRated = isRated,
        updatedAt = updatedAt,
    )

    private fun entity(
        weekStartMillis: Long = WEEK_START,
        dayOfWeek: Int = 2,
        assignedMemberId: String? = "member-a",
        rewardPoints: Int? = 10,
        isRated: Boolean = true,
        updatedAt: Long = 0L,
    ) = DailyAssignmentEntity(
        id = "da-1",
        weekStartMillis = weekStartMillis,
        dayOfWeek = dayOfWeek,
        assignedMemberId = assignedMemberId,
        rewardPoints = rewardPoints,
        isRated = isRated,
        updatedAt = updatedAt,
    )

    private companion object {
        const val WEEK_START = 1_700_000_000_000L
    }
}
