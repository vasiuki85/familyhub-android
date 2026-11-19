package com.familyhub.domain.model

data class HouseRule(
    val id: String,
    val title: String,
    val description: String?,
    val createdByUserId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val category: String? = null
)
