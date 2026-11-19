package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.HouseRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseRuleDao {
    @Query("SELECT * FROM house_rules ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<HouseRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: HouseRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<HouseRuleEntity>)

    @Query("DELETE FROM house_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM house_rules")
    suspend fun clear()
}
