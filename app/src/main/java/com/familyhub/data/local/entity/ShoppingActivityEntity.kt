package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_activity")
data class ShoppingActivityEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val itemId: String?,
    val actorId: String,
    val actionType: String,
    val payload: String?,
    val occurredAt: Long
)
