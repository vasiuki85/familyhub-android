package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val cost: Int,
    val createdByUserId: String,
    val isActive: Boolean,
    val createdAt: Long
)
