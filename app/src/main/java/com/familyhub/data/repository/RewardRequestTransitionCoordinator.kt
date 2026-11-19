package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.entity.UserWalletEntity
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.domain.model.RewardApprovalResult
import com.familyhub.domain.model.RewardDeclineResult
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.util.TimeUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Atomic reward-request approve/decline with wallet updates.
 */
@Singleton
class RewardRequestTransitionCoordinator @Inject constructor(
    private val database: FamilyHubDatabase
) {
    suspend fun approve(
        requestId: String,
        actorUserId: String,
        processedAt: Long = TimeUtils.now()
    ): RewardApprovalResult = database.withTransaction {
        AccountingActorAuthorization.requireAdminOrParentActor(
            familyMemberDao(),
            actorUserId,
            "Only parent or admin can approve reward requests"
        )

        val request = rewardRequestDao().getById(requestId)?.toDomain()
            ?: return@withTransaction RewardApprovalResult.NotFound
        if (request.status != RewardRequestStatus.PENDING) {
            return@withTransaction RewardApprovalResult.AlreadyProcessed
        }
        if (request.rewardCost < 0) {
            return@withTransaction RewardApprovalResult.InvalidRewardCost
        }

        val wallet = userWalletDao().get(request.requesterUserId)
        val currentBalance = wallet?.balance ?: 0
        val newBalanceLong = currentBalance.toLong() - request.rewardCost.toLong()
        if (newBalanceLong < 0L) {
            return@withTransaction RewardApprovalResult.NotEnoughPoints
        }
        val newBalance = AccountingIntMath.toIntExact(newBalanceLong)

        userWalletDao().upsert(
            UserWalletEntity(
                userId = request.requesterUserId,
                balance = newBalance,
                updatedAt = processedAt
            )
        )

        rewardRequestDao().upsert(
            request.copy(
                status = RewardRequestStatus.APPROVED,
                processedAt = processedAt,
                processedByUserId = actorUserId
            ).toEntity()
        )

        RewardApprovalResult.Success
    }

    suspend fun decline(
        requestId: String,
        actorUserId: String,
        processedAt: Long = TimeUtils.now()
    ): RewardDeclineResult = database.withTransaction {
        AccountingActorAuthorization.requireAdminOrParentActor(
            familyMemberDao(),
            actorUserId,
            "Only parent or admin can decline reward requests"
        )

        val request = rewardRequestDao().getById(requestId)?.toDomain()
            ?: return@withTransaction RewardDeclineResult.NotFound
        if (request.status != RewardRequestStatus.PENDING) {
            return@withTransaction RewardDeclineResult.AlreadyProcessed
        }

        rewardRequestDao().upsert(
            request.copy(
                status = RewardRequestStatus.DECLINED,
                processedAt = processedAt,
                processedByUserId = actorUserId
            ).toEntity()
        )

        RewardDeclineResult.Success
    }

    private fun rewardRequestDao() = database.rewardRequestDao()
    private fun userWalletDao() = database.userWalletDao()
    private fun familyMemberDao() = database.familyMemberDao()
}
