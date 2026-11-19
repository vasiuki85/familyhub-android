package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.TaskRotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskRotationDao {
    @Query("SELECT * FROM task_rotations")
    fun getAll(): Flow<List<TaskRotationEntity>>

    @Query("SELECT * FROM task_rotations WHERE taskId = :taskId LIMIT 1")
    suspend fun getByTaskId(taskId: String): TaskRotationEntity?

    @Query("SELECT * FROM task_rotations WHERE nextRotationAt <= :timestamp")
    suspend fun getDue(timestamp: Long): List<TaskRotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskRotationEntity)

    @Query("DELETE FROM task_rotations WHERE taskId = :taskId")
    suspend fun delete(taskId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rotations: List<TaskRotationEntity>)

    @Query("DELETE FROM task_rotations")
    suspend fun clear()
}
