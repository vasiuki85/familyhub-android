package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.familyhub.data.local.entity.RewardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {
    @Query("SELECT * FROM rewards ORDER BY createdAt DESC")
    fun getAll(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActive(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RewardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reward: RewardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rewards: List<RewardEntity>)

    @Update
    suspend fun update(reward: RewardEntity)

    @Delete
    suspend fun delete(reward: RewardEntity)

    @Query("DELETE FROM rewards")
    suspend fun clear()
}
