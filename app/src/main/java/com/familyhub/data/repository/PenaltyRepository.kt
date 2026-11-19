package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.PenaltyDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.domain.model.Penalty
import com.familyhub.domain.model.PenaltyCancelResult
import com.familyhub.domain.model.PenaltyCreateResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PenaltyRepository @Inject constructor(
    private val penaltyDao: PenaltyDao,
    private val coordinator: PenaltyTransitionCoordinator
) {
    fun getAllPenalties(): Flow<List<Penalty>> =
        penaltyDao.getAll().map { list -> list.map { it.toDomain() } }

    fun getPenaltiesForUser(userId: String): Flow<List<Penalty>> =
        penaltyDao.getForUser(userId).map { list -> list.map { it.toDomain() } }

    suspend fun createPenalty(
        userId: String,
        actorUserId: String,
        amount: Int,
        comment: String?,
        originType: String? = null,
        originId: String? = null,
        createdAt: Long = com.familyhub.util.TimeUtils.now()
    ): Penalty {
        return when (
            val result = coordinator.create(
                userId = userId,
                actorUserId = actorUserId,
                amount = amount,
                comment = comment,
                originType = originType,
                originId = originId,
                createdAt = createdAt
            )
        ) {
            is PenaltyCreateResult.Created -> result.penalty
            is PenaltyCreateResult.IdempotentNoOp -> result.penalty
        }
    }

    suspend fun cancelPenalty(
        id: String,
        actorUserId: String
    ): PenaltyCancelResult = coordinator.cancel(id, actorUserId)
}
