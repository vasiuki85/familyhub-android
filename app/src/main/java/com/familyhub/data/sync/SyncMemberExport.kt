package com.familyhub.data.sync

import com.familyhub.data.export.ExportMember
import com.familyhub.domain.model.FamilyMember

internal fun FamilyMember.toExportMember(): ExportMember = ExportMember(
    id = id,
    displayName = displayName,
    roleLabel = roleLabel,
    avatarColor = avatarColor,
    role = role.name,
    birthDate = birthDate,
    age = age,
    photoUrl = photoUrl?.takeIf(SyncExportSanitizer::isShareableMemberPhoto)
)
