package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.PenaltyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PenaltyDao {
    @Query("SELECT * FROM penalties ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PenaltyEntity>>

    @Query("SELECT * FROM penalties WHERE userId = :userId ORDER BY createdAt DESC")
    fun getForUser(userId: String): Flow<List<PenaltyEntity>>

    @Query("SELECT * FROM penalties WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PenaltyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(penalty: PenaltyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(penalties: List<PenaltyEntity>)

    @Query("DELETE FROM penalties")
    suspend fun clear()
}

