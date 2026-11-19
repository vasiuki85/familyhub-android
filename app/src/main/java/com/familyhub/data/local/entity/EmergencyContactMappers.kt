package com.familyhub.data.local.entity

import com.familyhub.domain.model.EmergencyContact

fun EmergencyContactEntity.toDomain(): EmergencyContact = EmergencyContact(
    id = id,
    name = name,
    phoneNumber = phoneNumber,
    description = description,
    createdAt = createdAt
)

fun EmergencyContact.toEntity(): EmergencyContactEntity = EmergencyContactEntity(
    id = id,
    name = name,
    phoneNumber = phoneNumber,
    description = description,
    createdAt = createdAt
)


