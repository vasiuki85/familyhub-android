package com.familyhub.domain.model

/**
 * Кошелёк пользователя с доступным количеством баллов.
 */
data class UserWallet(
    val userId: String,
    val balance: Int,
    val updatedAt: Long = 0L
)
