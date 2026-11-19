package com.familyhub.domain.model

/**
 * Комментарий к задаче
 */
data class TaskComment(
    val id: String,
    val authorId: String,
    val message: String,
    val createdAt: Long
)

