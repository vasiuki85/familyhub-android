package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_goals")
data class FamilyGoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val targetPoints: Int,
    val currentPoints: Int,
    val deadlineAt: Long?,
    val status: String,
    val createdByUserId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val personal: Boolean
)
