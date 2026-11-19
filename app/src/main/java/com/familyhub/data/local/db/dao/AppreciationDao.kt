package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.AppreciationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppreciationDao {
    @Query("SELECT * FROM appreciations ORDER BY createdAt DESC")
    fun getAll(): Flow<List<AppreciationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppreciationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppreciationEntity>)

    @Query("DELETE FROM appreciations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM appreciations")
    suspend fun clear()
}
