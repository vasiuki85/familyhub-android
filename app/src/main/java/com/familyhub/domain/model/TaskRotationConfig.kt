package com.familyhub.domain.model

data class TaskRotationConfig(
    val taskId: String,
    val memberIds: List<String>,
    val currentIndex: Int,
    val frequency: TaskRotationFrequency,
    val startDateTime: Long,
    val nextRotationAt: Long,
    val lastRotationAt: Long?
)
