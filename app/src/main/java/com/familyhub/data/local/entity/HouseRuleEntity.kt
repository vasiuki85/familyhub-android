package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "house_rules")
data class HouseRuleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val createdByUserId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val category: String? = null
)
