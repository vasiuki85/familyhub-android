package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.domain.model.RewardRequestStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RewardRequestMergeTest {

    private val requesterId = "child"

    @Test
    fun resolver_concurrentApprovedDeclined_approvedWins() {
        val approved = request(
            status = RewardRequestStatus.APPROVED.name,
            processedAt = 100L,
            processedByUserId = "parent"
        )
        val declined = request(
            status = RewardRequestStatus.DECLINED.name,
            processedAt = 100L,
            processedByUserId = "parent"
        )

        assertEquals(
            RewardRequestStatus.APPROVED.name,
            RewardRequestMerge.resolveRewardRequest(approved, declined).status
        )
        assertEquals(
            RewardRequestStatus.APPROVED.name,
            RewardRequestMerge.resolveRewardRequest(declined, approved).status
        )
    }

    @Test
    fun resolver_equalTimestampsDifferentNote_isCommutative() {
        val left = request(note = "left-note", requestedAt = 100L)
        val right = request(note = "right-note", requestedAt = 100L)
        assertEquals(
            RewardRequestMerge.resolveRewardRequest(left, right),
            RewardRequestMerge.resolveRewardRequest(right, left)
        )
    }

    @Test
    fun resolver_nullNoteVersusEmpty_isCommutative() {
        val withNull = request(note = null, requestedAt = 100L)
        val withEmpty = request(note = "", requestedAt = 100L)
        assertEquals(
            RewardRequestMerge.resolveRewardRequest(withNull, withEmpty),
            RewardRequestMerge.resolveRewardRequest(withEmpty, withNull)
        )
        assertEquals("", RewardRequestMerge.resolveRewardRequest(withNull, withEmpty).note)
    }

    @Test
    fun resolver_nullProcessedByUserIdVersusEmpty_isCommutative() {
        val withNull = request(
            status = RewardRequestStatus.APPROVED.name,
            processedAt = 100L,
            processedByUserId = null
        )
        val withEmpty = request(
            status = RewardRequestStatus.APPROVED.name,
            processedAt = 100L,
            processedByUserId = ""
        )
        assertEquals(
            RewardRequestMerge.resolveRewardRequest(withNull, withEmpty),
            RewardRequestMerge.resolveRewardRequest(withEmpty, withNull)
        )
        assertEquals("", RewardRequestMerge.resolveRewardRequest(withNull, withEmpty).processedByUserId)
    }

    @Test
    fun resolver_isCommutative() {
        val left = request(requestedAt = 100L)
        val right = request(requestedAt = 200L)
        assertEquals(
            RewardRequestMerge.resolveRewardRequest(left, right),
            RewardRequestMerge.resolveRewardRequest(right, left)
        )
    }

    @Test
    fun resolver_isAssociative() {
        val a = request(
            id = "req-1",
            status = RewardRequestStatus.PENDING.name,
            requestedAt = 100L
        )
        val b = request(
            id = "req-1",
            status = RewardRequestStatus.DECLINED.name,
            processedAt = 100L,
            requestedAt = 100L,
            processedByUserId = "parent"
        )
        val c = request(
            id = "req-1",
            status = RewardRequestStatus.APPROVED.name,
            processedAt = 100L,
            requestedAt = 100L,
            processedByUserId = "parent"
        )
        val abThenC = RewardRequestMerge.resolveRewardRequest(
            RewardRequestMerge.resolveRewardRequest(a, b),
            c
        )
        val aThenBc = RewardRequestMerge.resolveRewardRequest(
            a,
            RewardRequestMerge.resolveRewardRequest(b, c)
        )
        assertEquals(abThenC, aThenBc)
    }

    @Test
    fun resolver_isIdempotent() {
        val value = request(requestedAt = 100L)
        assertEquals(value, RewardRequestMerge.resolveRewardRequest(value, value))
    }

    @Test
    fun mergeRewardRequests_orderIndependentListEquality() {
        val local = listOf(
            request(id = "req-a", requestedAt = 100L),
            request(id = "req-b", requestedAt = 200L)
        )
        val remote = listOf(
            request(id = "req-c", requestedAt = 300L),
            request(id = "req-d", requestedAt = 400L)
        )

        val localFirst = RewardRequestMerge.mergeRewardRequests(local, remote)
        val remoteFirst = RewardRequestMerge.mergeRewardRequests(remote, local)
        assertEquals(localFirst, remoteFirst)
        assertEquals(listOf("req-a", "req-b", "req-c", "req-d"), localFirst.map { it.id })
    }

    @Test
    fun mergeRewardRequests_repeatedMerge_isStable() {
        val local = listOf(
            request(
                id = "req-1",
                status = RewardRequestStatus.APPROVED.name,
                processedAt = 100L,
                processedByUserId = "parent"
            )
        )
        val remote = listOf(
            request(
                id = "req-1",
                status = RewardRequestStatus.DECLINED.name,
                processedAt = 100L,
                processedByUserId = "parent"
            )
        )
        val first = RewardRequestMerge.mergeRewardRequests(local, remote)
        val second = RewardRequestMerge.mergeRewardRequests(first, first)
        assertEquals(first, second)
    }

    private fun request(
        id: String = "req-1",
        status: String = RewardRequestStatus.PENDING.name,
        cost: Int = 50,
        requestedAt: Long = 1L,
        processedAt: Long? = null,
        processedByUserId: String? = null,
        note: String? = null,
        rewardTitle: String = "Ice Cream"
    ) = ExportRewardRequest(
        id = id,
        rewardId = "reward-active",
        rewardTitle = rewardTitle,
        rewardCost = cost,
        requesterUserId = requesterId,
        note = note,
        status = status,
        requestedAt = requestedAt,
        processedAt = processedAt,
        processedByUserId = processedByUserId
    )
}
