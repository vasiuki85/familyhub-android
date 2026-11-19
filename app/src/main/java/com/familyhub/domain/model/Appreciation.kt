package com.familyhub.domain.model

data class Appreciation(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val message: String,
    val createdAt: Long
)
