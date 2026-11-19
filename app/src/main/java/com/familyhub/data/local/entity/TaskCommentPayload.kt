package com.familyhub.data.local.entity

import com.familyhub.domain.model.TaskComment
import kotlinx.serialization.Serializable
import com.familyhub.util.fixMojibakeIfNeeded

@Serializable
data class TaskCommentPayload(
    val id: String,
    val authorId: String,
    val message: String,
    val createdAt: Long
)

fun TaskCommentPayload.toDomain(): TaskComment {
    return TaskComment(
        id = id,
        authorId = authorId,
        message = message.fixMojibakeIfNeeded(),
        createdAt = createdAt
    )
}

fun TaskComment.toPayload(): TaskCommentPayload {
    return TaskCommentPayload(
        id = id,
        authorId = authorId,
        message = message,
        createdAt = createdAt
    )
}

