package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.DailyAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyAssignmentDao {
    @Query("SELECT * FROM daily_assignments WHERE weekStartMillis = :weekStart ORDER BY dayOfWeek")
    fun observeByWeek(weekStart: Long): Flow<List<DailyAssignmentEntity>>

    @Query("SELECT * FROM daily_assignments WHERE weekStartMillis = :weekStart ORDER BY dayOfWeek")
    suspend fun getByWeek(weekStart: Long): List<DailyAssignmentEntity>

    @Query("DELETE FROM daily_assignments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(assignment: DailyAssignmentEntity)

    @Query("SELECT * FROM daily_assignments ORDER BY weekStartMillis, dayOfWeek")
    suspend fun getAllSnapshot(): List<DailyAssignmentEntity>

    @Query("DELETE FROM daily_assignments")
    suspend fun clearAll()

    @Query("DELETE FROM daily_assignments WHERE weekStartMillis = :weekStart")
    suspend fun deleteByWeek(weekStart: Long)
}

