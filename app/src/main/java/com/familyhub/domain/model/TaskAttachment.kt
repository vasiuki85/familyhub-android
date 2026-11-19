package com.familyhub.domain.model

data class TaskAttachment(
    val id: String,
    val name: String,
    val uri: String,
    val mimeType: String? = null,
    val addedAt: Long
)
