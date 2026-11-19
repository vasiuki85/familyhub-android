package com.familyhub.data.local.entity

import com.familyhub.domain.model.TaskAttachment
import kotlinx.serialization.Serializable

@Serializable
data class TaskAttachmentPayload(
    val id: String,
    val name: String,
    val uri: String,
    val mimeType: String? = null,
    val addedAt: Long
)

fun TaskAttachmentPayload.toDomain(): TaskAttachment {
    return TaskAttachment(
        id = id,
        name = name,
        uri = uri,
        mimeType = mimeType,
        addedAt = addedAt
    )
}

fun TaskAttachment.toPayload(): TaskAttachmentPayload {
    return TaskAttachmentPayload(
        id = id,
        name = name,
        uri = uri,
        mimeType = mimeType,
        addedAt = addedAt
    )
}
