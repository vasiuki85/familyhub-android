package com.familyhub.data.local.entity

import com.familyhub.domain.model.FamilyMember
import com.familyhub.util.fixMojibakeIfNeeded

fun FamilyMemberEntity.toDomain(): FamilyMember = FamilyMember(
    id = id,
    displayName = displayName.fixMojibakeIfNeeded(),
    roleLabel = roleLabel.fixMojibakeIfNeeded(),
    avatarColor = avatarColor,
    role = role,
    age = age,
    birthDate = birthDate,
    photoUrl = photoUrl
)

fun FamilyMember.toEntity(): FamilyMemberEntity = FamilyMemberEntity(
    id = id,
    displayName = displayName,
    roleLabel = roleLabel,
    avatarColor = avatarColor,
    role = role,
    age = age,
    birthDate = birthDate,
    photoUrl = photoUrl
)
