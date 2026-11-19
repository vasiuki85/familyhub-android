package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_wallets")
data class UserWalletEntity(
    @PrimaryKey val userId: String,
    val balance: Int,
    val updatedAt: Long = 0L
)
