package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.familyhub.data.local.entity.UserWalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserWalletDao {
    @Query("SELECT * FROM user_wallets")
    fun getAll(): Flow<List<UserWalletEntity>>

    @Query("SELECT * FROM user_wallets WHERE userId = :userId LIMIT 1")
    fun observe(userId: String): Flow<UserWalletEntity?>

    @Query("SELECT * FROM user_wallets WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): UserWalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserWalletEntity)

    @Update
    suspend fun update(entity: UserWalletEntity)

    @Query("UPDATE user_wallets SET balance = balance - :amount, updatedAt = :updatedAt WHERE userId = :userId AND balance >= :amount")
    suspend fun decreaseBalance(userId: String, amount: Int, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UserWalletEntity>)

    @Query("DELETE FROM user_wallets")
    suspend fun clear()
}
