package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.familyhub.domain.model.Penalty

@Entity(tableName = "penalties")
data class PenaltyEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val issuedByUserId: String,
    val amount: Int,
    val comment: String?,
    val createdAt: Long,
    val canceled: Boolean,
    val canceledAt: Long?,
    val originType: String?,
    val originId: String?
)

fun PenaltyEntity.toDomain(): Penalty = Penalty(
    id = id,
    userId = userId,
    issuedByUserId = issuedByUserId,
    amount = amount,
    comment = comment,
    createdAt = createdAt,
    canceled = canceled,
    canceledAt = canceledAt,
    originType = originType,
    originId = originId
)

fun Penalty.toEntity(): PenaltyEntity = PenaltyEntity(
    id = id,
    userId = userId,
    issuedByUserId = issuedByUserId,
    amount = amount,
    comment = comment,
    createdAt = createdAt,
    canceled = canceled,
    canceledAt = canceledAt,
    originType = originType,
    originId = originId
)

