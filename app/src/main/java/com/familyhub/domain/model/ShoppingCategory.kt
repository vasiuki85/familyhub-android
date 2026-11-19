package com.familyhub.domain.model

data class ShoppingCategory(
    val id: String,
    val listId: String,
    val title: String,
    val colorHex: String,
    val iconName: String,
    val sortOrder: Int
)
