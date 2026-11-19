package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reward_requests")
data class RewardRequestEntity(
    @PrimaryKey val id: String,
    val rewardId: String,
    val rewardTitle: String,
    val rewardCost: Int,
    val requesterUserId: String,
    val note: String?,
    val status: String,
    val requestedAt: Long,
    val processedAt: Long?,
    val processedByUserId: String?
)
