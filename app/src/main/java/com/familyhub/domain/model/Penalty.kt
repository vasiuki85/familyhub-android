package com.familyhub.domain.model

data class Penalty(
    val id: String,
    val userId: String,
    val issuedByUserId: String,
    val amount: Int, // negative for penalties
    val comment: String? = null,
    val createdAt: Long,
    val canceled: Boolean = false,
    val canceledAt: Long? = null,
    val originType: String? = null,
    val originId: String? = null
)

