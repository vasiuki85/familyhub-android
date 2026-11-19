package com.familyhub.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AwesomenessEntry(
    val id: String,
    val text: String,
    val createdAt: Long
)

