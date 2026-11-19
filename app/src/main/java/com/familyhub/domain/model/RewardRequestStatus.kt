package com.familyhub.domain.model

/**
 * Статус заявки на получение награды.
 */
enum class RewardRequestStatus {
    PENDING,
    APPROVED,
    DECLINED;

    companion object {
        fun fromName(name: String): RewardRequestStatus {
            return runCatching { valueOf(name) }.getOrElse { PENDING }
        }
    }
}
