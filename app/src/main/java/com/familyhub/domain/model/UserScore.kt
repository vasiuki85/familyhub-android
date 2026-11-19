package com.familyhub.domain.model

data class UserScore(
    val userId: String,
    val weekStartDate: Long,  // millis UTC понедельника начала недели
    val score: Int,
    val updatedAt: Long = 0L
)

