package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.familyhub.data.local.entity.RewardRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardRequestDao {
    @Query("SELECT * FROM reward_requests ORDER BY requestedAt DESC")
    fun getAll(): Flow<List<RewardRequestEntity>>

    @Query("SELECT * FROM reward_requests WHERE requesterUserId = :userId ORDER BY requestedAt DESC")
    fun getByRequester(userId: String): Flow<List<RewardRequestEntity>>

    @Query("SELECT * FROM reward_requests WHERE status = :status ORDER BY requestedAt ASC")
    fun getByStatus(status: String): Flow<List<RewardRequestEntity>>

    @Query("SELECT * FROM reward_requests WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RewardRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: RewardRequestEntity)

    @Update
    suspend fun update(request: RewardRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(requests: List<RewardRequestEntity>)

    @Query("DELETE FROM reward_requests")
    suspend fun clear()
}
