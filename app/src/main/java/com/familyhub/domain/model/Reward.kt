package com.familyhub.domain.model

/**
 * Награда, которую можно приобрести за баллы.
 */
data class Reward(
    val id: String,
    val title: String,
    val description: String?,
    val cost: Int,
    val createdByUserId: String,
    val isActive: Boolean,
    val createdAt: Long
)
