package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.familyhub.domain.model.Appreciation

@Entity(tableName = "appreciations")
data class AppreciationEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val recipientId: String,
    val message: String,
    val createdAt: Long
)

fun AppreciationEntity.toDomain(): Appreciation = Appreciation(
    id = id,
    senderId = senderId,
    recipientId = recipientId,
    message = message,
    createdAt = createdAt
)

fun Appreciation.toEntity(): AppreciationEntity = AppreciationEntity(
    id = id,
    senderId = senderId,
    recipientId = recipientId,
    message = message,
    createdAt = createdAt
)
