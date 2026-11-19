package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.entity.PenaltyEntity
import com.familyhub.data.local.entity.UserScoreEntity
import com.familyhub.data.local.entity.UserWalletEntity
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.domain.model.AccountingIdempotencyConflictException
import com.familyhub.domain.model.AccountingTransitionInvalidException
import com.familyhub.domain.model.Penalty
import com.familyhub.domain.model.PenaltyCancelResult
import com.familyhub.domain.model.PenaltyCreateResult
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.util.TimeUtils
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Atomic penalty create/cancel with wallet and weekly score updates.
 * All writes run inside a single Room [withTransaction]; callers must not
 * mutate penalties through repositories outside this coordinator.
 */
@Singleton
class PenaltyTransitionCoordinator @Inject constructor(
    private val database: FamilyHubDatabase
) {
    suspend fun create(
        userId: String,
        actorUserId: String,
        amount: Int,
        comment: String?,
        originType: String? = null,
        originId: String? = null,
        penaltyId: String = UUID.randomUUID().toString(),
        createdAt: Long = TimeUtils.now()
    ): PenaltyCreateResult = database.withTransaction {
        AccountingActorAuthorization.requireAdminOrParentActor(
            familyMemberDao(),
            actorUserId,
            "Only parent or admin can create penalties"
        )
        familyMemberDao().getById(userId)
            ?: throw AccountingTransitionInvalidException("Target member not found: $userId")

        val normalizedAmount = AccountingIntMath.normalizePenaltyMagnitude(amount)
        val penalty = Penalty(
            id = penaltyId,
            userId = userId,
            issuedByUserId = actorUserId,
            amount = normalizedAmount,
            comment = comment,
            createdAt = createdAt,
            canceled = false,
            canceledAt = null,
            originType = originType,
            originId = originId
        )

        val existing = penaltyDao().getById(penalty.id)
        if (existing != null) {
            if (existing.matchesActivePayload(penalty)) {
                return@withTransaction PenaltyCreateResult.IdempotentNoOp(existing.toDomain())
            }
            throw AccountingIdempotencyConflictException(
                "Penalty ${penalty.id} already exists with different payload"
            )
        }

        val timestamp = penalty.createdAt
        penaltyDao().upsert(penalty.toEntity())
        adjustWallet(penalty.userId, penalty.amount, timestamp)
        val weekStart = WeekUtils.getWeekStartMillis(penalty.createdAt)
        adjustScore(penalty.userId, weekStart, penalty.amount, timestamp)

        PenaltyCreateResult.Created(penalty)
    }

    suspend fun cancel(
        penaltyId: String,
        actorUserId: String,
        canceledAt: Long = TimeUtils.now()
    ): PenaltyCancelResult = database.withTransaction {
        AccountingActorAuthorization.requireAdminOrParentActor(
            familyMemberDao(),
            actorUserId,
            "Only parent or admin can cancel penalties"
        )

        val existing = penaltyDao().getById(penaltyId) ?: return@withTransaction PenaltyCancelResult.NotFound
        if (existing.canceled) {
            return@withTransaction PenaltyCancelResult.IdempotentNoOp
        }

        AccountingIntMath.requireActivePenaltyAmount(existing.amount)
        val refundDelta = AccountingIntMath.penaltyRefundDelta(existing.amount)

        val canceled = existing.copy(canceled = true, canceledAt = canceledAt)
        penaltyDao().upsert(canceled)
        adjustWallet(existing.userId, refundDelta, canceledAt)
        val weekStart = WeekUtils.getWeekStartMillis(existing.createdAt)
        adjustScore(existing.userId, weekStart, refundDelta, canceledAt)

        PenaltyCancelResult.Canceled
    }

    private suspend fun adjustWallet(userId: String, delta: Int, timestamp: Long) {
        val current = userWalletDao().get(userId)
        val newBalance = AccountingIntMath.applyDelta(current?.balance, delta)
        userWalletDao().upsert(
            UserWalletEntity(
                userId = userId,
                balance = newBalance,
                updatedAt = timestamp
            )
        )
    }

    private suspend fun adjustScore(
        userId: String,
        weekStartDate: Long,
        delta: Int,
        timestamp: Long
    ) {
        val existing = userScoreDao().getUserScoreForWeek(userId, weekStartDate)
        val newScore = AccountingIntMath.applyDelta(existing?.score, delta)
        userScoreDao().upsert(
            UserScoreEntity(
                userId = userId,
                weekStartDate = weekStartDate,
                score = newScore,
                updatedAt = timestamp
            )
        )
    }

    private fun PenaltyEntity.matchesActivePayload(penalty: Penalty): Boolean {
        return userId == penalty.userId &&
            issuedByUserId == penalty.issuedByUserId &&
            amount == penalty.amount &&
            comment == penalty.comment &&
            createdAt == penalty.createdAt &&
            !canceled &&
            originType == penalty.originType &&
            originId == penalty.originId
    }

    private fun penaltyDao() = database.penaltyDao()
    private fun userWalletDao() = database.userWalletDao()
    private fun userScoreDao() = database.userScoreDao()
    private fun familyMemberDao() = database.familyMemberDao()
}
