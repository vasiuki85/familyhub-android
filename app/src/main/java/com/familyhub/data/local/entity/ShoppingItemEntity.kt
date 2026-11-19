package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val createdByUserId: String,
    val isBought: Boolean,
    val createdAt: Long,
    val categoryId: String?,
    val quantity: Double?,
    val quantityUnit: String?,
    val note: String?,
    val boughtAt: Long?
)

