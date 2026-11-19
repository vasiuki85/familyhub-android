package com.familyhub.domain.model

data class ShoppingList(
    val id: String,
    val title: String,
    val colorHex: String,
    val iconName: String,
    val createdAt: Long,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val templateKey: String? = null,
    val participantIds: List<String> = emptyList()
)
