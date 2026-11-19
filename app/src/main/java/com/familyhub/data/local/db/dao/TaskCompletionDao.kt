package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: TaskCompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(completions: List<TaskCompletionEntity>)

    @Query(
        """
        SELECT * FROM task_completions
        WHERE userId = :userId AND reversedAt IS NULL
        ORDER BY completionTime DESC
        """
    )
    fun getActiveCompletionsForUser(userId: String): Flow<List<TaskCompletionEntity>>

    @Query("SELECT COUNT(*) FROM task_completions WHERE userId = :userId AND reversedAt IS NULL")
    suspend fun getCompletionCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM task_completions WHERE userId = :userId AND bonusPoints > 0 AND reversedAt IS NULL")
    suspend fun getOnTimeCompletionCount(userId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM task_completions
        WHERE userId = :userId AND penaltyPoints > 0 AND weekStartDate = :weekStart AND reversedAt IS NULL
        """
    )
    suspend fun getLateCompletionCountForWeek(userId: String, weekStart: Long): Int

    @Query(
        """
        SELECT * FROM task_completions
        WHERE userId = :userId AND weekStartDate = :weekStart AND reversedAt IS NULL
        """
    )
    suspend fun getCompletionsForUserInWeek(userId: String, weekStart: Long): List<TaskCompletionEntity>

    @Query(
        """
        SELECT * FROM task_completions
        WHERE weekStartDate BETWEEN :fromWeek AND :toWeek AND reversedAt IS NULL
        """
    )
    fun getActiveCompletionsBetweenWeeks(fromWeek: Long, toWeek: Long): Flow<List<TaskCompletionEntity>>

    /** Full ledger including reversed rows — export/import/sync only. */
    @Query("SELECT * FROM task_completions ORDER BY completionTime DESC")
    fun getAllCompletions(): Flow<List<TaskCompletionEntity>>

    @Query(
        """
        SELECT * FROM task_completions
        WHERE reversedAt IS NULL
        ORDER BY completionTime DESC
        """
    )
    fun getAllActiveCompletions(): Flow<List<TaskCompletionEntity>>

    @Query(
        """
        SELECT * FROM task_completions
        WHERE taskId = :taskId AND reversedAt IS NULL
        ORDER BY completionTime DESC
        LIMIT 1
        """
    )
    suspend fun getLatestActiveCompletionForTask(taskId: String): TaskCompletionEntity?

    @Query(
        """
        SELECT * FROM task_completions
        WHERE taskId = :taskId
        ORDER BY completionTime DESC
        LIMIT 1
        """
    )
    suspend fun getLatestCompletionForTask(taskId: String): TaskCompletionEntity?

    @Query(
        """
        SELECT * FROM task_completions
        WHERE taskId = :taskId AND completionCycle = :cycle AND reversedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun getActiveCompletionForTaskCycle(taskId: String, cycle: Long): TaskCompletionEntity?

    @Query(
        """
        SELECT * FROM task_completions
        WHERE taskId = :taskId AND completionCycle IS NULL AND reversedAt IS NULL
        ORDER BY completionTime DESC
        LIMIT 1
        """
    )
    suspend fun getLatestLegacyActiveCompletionForTask(taskId: String): TaskCompletionEntity?

    @Query("DELETE FROM task_completions")
    suspend fun clear()
}
