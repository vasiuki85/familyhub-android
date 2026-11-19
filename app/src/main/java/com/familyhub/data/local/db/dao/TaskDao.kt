package com.familyhub.data.local.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

data class TaskOrderIndexRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "orderIndex") val orderIndex: Int,
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY orderIndex ASC, createdAt DESC")
    fun getAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE assignedToUserId = :userId ORDER BY orderIndex ASC, createdAt DESC")
    fun getByAssignee(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY orderIndex ASC, createdAt DESC")
    suspend fun getAllSync(): List<TaskEntity>

    @Query(
        """
        SELECT id, orderIndex FROM tasks
        WHERE isPrivate = 0
           OR createdByUserId = :currentUserId
           OR assignedToUserId = :currentUserId
        """
    )
    suspend fun getVisibleOrderIndices(currentUserId: String): List<TaskOrderIndexRow>

    @Query(
        """
        UPDATE tasks
        SET orderIndex = :newOrderIndex
        WHERE id = :taskId AND orderIndex = :expectedOrderIndex
        """
    )
    suspend fun updateOrderIndexCas(
        taskId: String,
        expectedOrderIndex: Int,
        newOrderIndex: Int,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun clear()
}
