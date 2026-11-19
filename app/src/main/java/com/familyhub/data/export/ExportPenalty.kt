package com.familyhub.data.export

import com.familyhub.domain.model.Penalty
import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ExportPenalty(
    val id: String = "",
    val userId: String = "",
    val issuedByUserId: String = "",
    val amount: Int = 0,
    val comment: String? = null,
    val createdAt: Long = 0L,
    val canceled: Boolean = false,
    val canceledAt: Long? = null,
    val originType: String? = null,
    val originId: String? = null
)

fun Penalty.toExport(): ExportPenalty = ExportPenalty(
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

fun ExportPenalty.toDomain(): Penalty = Penalty(
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

