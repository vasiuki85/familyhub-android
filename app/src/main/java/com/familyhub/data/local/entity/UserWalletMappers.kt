package com.familyhub.data.local.entity

import com.familyhub.domain.model.UserWallet

fun UserWalletEntity.toDomain(): UserWallet = UserWallet(
    userId = userId,
    balance = balance,
    updatedAt = updatedAt
)

fun UserWallet.toEntity(): UserWalletEntity = UserWalletEntity(
    userId = userId,
    balance = balance,
    updatedAt = updatedAt
)
