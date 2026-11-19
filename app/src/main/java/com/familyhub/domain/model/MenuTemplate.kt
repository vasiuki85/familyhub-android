package com.familyhub.domain.model

data class MenuTemplate(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val entries: List<MenuEntry>
)
