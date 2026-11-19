package com.familyhub.domain.model

data class EmergencyContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val description: String? = null,
    val createdAt: Long
)


