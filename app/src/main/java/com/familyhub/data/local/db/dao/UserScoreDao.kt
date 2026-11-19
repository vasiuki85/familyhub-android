package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.UserScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserScoreDao {
    @Query("SELECT * FROM user_scores WHERE weekStartDate = :weekStartDate")
    fun getScoresForWeek(weekStartDate: Long): Flow<List<UserScoreEntity>>

    @Query("SELECT * FROM user_scores WHERE weekStartDate >= :weekStartDate")
    fun getScoresFromWeek(weekStartDate: Long): Flow<List<UserScoreEntity>>

    @Query("SELECT * FROM user_scores WHERE userId = :userId AND weekStartDate >= :weekStartDate ORDER BY weekStartDate ASC")
    fun getScoresForUserFromWeek(userId: String, weekStartDate: Long): Flow<List<UserScoreEntity>>

    @Query("SELECT * FROM user_scores WHERE userId = :userId AND weekStartDate = :weekStartDate LIMIT 1")
    suspend fun getUserScoreForWeek(userId: String, weekStartDate: Long): UserScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: UserScoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(scores: List<UserScoreEntity>)

    @Query("DELETE FROM user_scores")
    suspend fun clear()
}

