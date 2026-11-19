package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.UserAchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(achievement: UserAchievementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<UserAchievementEntity>)

    @Query("SELECT * FROM user_achievements WHERE userId = :userId")
    fun getAchievementsForUser(userId: String): Flow<List<UserAchievementEntity>>

    @Query("SELECT * FROM user_achievements ORDER BY earnedAt DESC")
    fun getAllAchievements(): Flow<List<UserAchievementEntity>>

    @Query("DELETE FROM user_achievements WHERE userId = :userId AND type = :type")
    suspend fun deleteByUserAndType(userId: String, type: String)

    @Query("DELETE FROM user_achievements")
    suspend fun clear()
}
