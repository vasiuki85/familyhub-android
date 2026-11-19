package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_categories")
data class ShoppingCategoryEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val colorHex: String,
    val iconName: String,
    val sortOrder: Int
)
