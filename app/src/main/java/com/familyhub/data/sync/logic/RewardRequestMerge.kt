package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportRewardRequest

/**
 * Deterministic reward-request merge for FH2-14A.
 *
 * Status precedence: APPROVED > DECLINED > PENDING > unknown.
 */
object RewardRequestMerge {

    fun resolveRewardRequest(
        left: ExportRewardRequest,
        right: ExportRewardRequest
    ): ExportRewardRequest {
        return if (compareRewardRequests(left, right) >= 0) left else right
    }

    fun mergeRewardRequests(
        local: List<ExportRewardRequest>,
        remote: List<ExportRewardRequest>
    ): List<ExportRewardRequest> {
        return (local + remote)
            .groupBy { it.id }
            .entries
            .sortedBy { it.key }
            .map { (_, group) -> group.reduce { acc, next -> resolveRewardRequest(acc, next) } }
    }

    private fun compareRewardRequests(
        left: ExportRewardRequest,
        right: ExportRewardRequest
    ): Int {
        statusRank(left.status).compareTo(statusRank(right.status)).let { if (it != 0) return it }
        compareNullableLong(left.processedAt, right.processedAt).let { if (it != 0) return it }
        compareLong(left.requestedAt, right.requestedAt).let { if (it != 0) return it }
        return compareFullRecord(left, right)
    }

    private fun compareFullRecord(
        left: ExportRewardRequest,
        right: ExportRewardRequest
    ): Int {
        compareValuesBy(
            left,
            right,
            { it.rewardId },
            { it.rewardTitle },
            { it.rewardCost },
            { it.requesterUserId },
            { it.status }
        ).let { if (it != 0) return it }
        compareNullableString(left.note, right.note).let { if (it != 0) return it }
        compareNullableString(left.processedByUserId, right.processedByUserId).let { if (it != 0) return it }
        return left.id.compareTo(right.id)
    }

    private fun statusRank(status: String): Int = when (status) {
        "APPROVED" -> 3
        "DECLINED" -> 2
        "PENDING" -> 1
        else -> 0
    }

    private fun compareNullableLong(left: Long?, right: Long?): Int {
        return when {
            left == null && right == null -> 0
            left == null -> -1
            right == null -> 1
            else -> left.compareTo(right)
        }
    }

    private fun compareNullableString(left: String?, right: String?): Int {
        return when {
            left == null && right == null -> 0
            left == null -> -1
            right == null -> 1
            else -> left.compareTo(right)
        }
    }

    private fun compareLong(left: Long, right: Long): Int = left.compareTo(right)
}
