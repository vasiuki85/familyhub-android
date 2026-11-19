package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.RewardDao
import com.familyhub.data.local.db.dao.RewardRequestDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.Reward
import com.familyhub.domain.model.RewardRequest
import com.familyhub.domain.model.RewardRequestStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RewardRepository @Inject constructor(
    private val rewardDao: RewardDao,
    private val rewardRequestDao: RewardRequestDao,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    fun getAllRewards(): Flow<List<Reward>> {
        return rewardDao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    fun getActiveRewards(): Flow<List<Reward>> {
        return rewardDao.getActive().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getRewardById(id: String): Reward? {
        return rewardDao.getById(id)?.toDomain()
    }

    suspend fun upsertReward(reward: Reward) {
        rewardDao.upsert(reward.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.REWARDS, reward.id)
    }

    suspend fun deleteReward(reward: Reward) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.REWARDS, reward.id)
        rewardDao.delete(reward.toEntity())
    }

    fun getAllRequests(): Flow<List<RewardRequest>> {
        return rewardRequestDao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    fun getRequestsForUser(userId: String): Flow<List<RewardRequest>> {
        return rewardRequestDao.getByRequester(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getRequestsByStatus(status: RewardRequestStatus): Flow<List<RewardRequest>> {
        return rewardRequestDao.getByStatus(status.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getRequestById(id: String): RewardRequest? {
        return rewardRequestDao.getById(id)?.toDomain()
    }

    suspend fun upsertRequest(request: RewardRequest) {
        rewardRequestDao.upsert(request.toEntity())
    }
}
