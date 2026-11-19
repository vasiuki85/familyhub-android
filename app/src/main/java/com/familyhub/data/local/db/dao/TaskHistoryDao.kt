package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.TaskHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskHistoryDao {
    @Query("SELECT * FROM task_history WHERE taskId = :taskId ORDER BY timestamp DESC")
    fun getHistoryForTask(taskId: String): Flow<List<TaskHistoryEntity>>
    
    @Query("SELECT * FROM task_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<TaskHistoryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: TaskHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(history: List<TaskHistoryEntity>)
    
    @Query("SELECT * FROM task_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSnapshot(limit: Int): List<TaskHistoryEntity>

    @Query("DELETE FROM task_history")
    suspend fun clearAll()
    
    @Query("DELETE FROM task_history WHERE taskId = :taskId")
    suspend fun deleteHistoryForTask(taskId: String)
}

