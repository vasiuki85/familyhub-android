package com.familyhub.domain.utils

import com.familyhub.domain.model.FamilyMember

/**
 * Presentation-only label for a family member: human [FamilyMember.displayName] when set,
 * otherwise the account login stored in [FamilyMember.roleLabel].
 */
fun FamilyMember.displayLabel(): String {
    val trimmedDisplayName = displayName.trim()
    if (trimmedDisplayName.isNotEmpty()) {
        return trimmedDisplayName
    }
    val trimmedLogin = roleLabel.trim()
    if (trimmedLogin.isNotEmpty()) {
        return trimmedLogin
    }
    return id
}

fun Map<String, FamilyMember>.displayLabelFor(memberId: String): String? =
    this[memberId]?.displayLabel()
