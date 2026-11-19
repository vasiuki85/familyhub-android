package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.TaskCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCategoryDao {
    @Query("SELECT * FROM task_categories ORDER BY createdAt ASC")
    fun getAll(): Flow<List<TaskCategoryEntity>>

    @Query("SELECT * FROM task_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: TaskCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<TaskCategoryEntity>)

    @Delete
    suspend fun delete(category: TaskCategoryEntity)

    @Query("DELETE FROM task_categories")
    suspend fun clear()
}
