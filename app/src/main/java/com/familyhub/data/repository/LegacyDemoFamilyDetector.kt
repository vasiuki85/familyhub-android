package com.familyhub.data.repository

import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole

/**
 * Recognizes only the exact family-member snapshot created by the removed debug seeder.
 *
 * IDs are intentionally excluded because the old seeder generated them randomly. Every other
 * persisted member field is part of the signature. The only allowed extra row is the current
 * Firebase UID created by authenticated bootstrap; every other extra or edited demo row fails
 * closed. An unauthenticated startup can still recognize the exact four-row legacy snapshot.
 */
internal object LegacyDemoFamilyDetector {
    private data class MemberSignature(
        val displayName: String,
        val roleLabel: String,
        val avatarColor: String,
        val role: MemberRole,
        val age: Int?,
        val birthDate: String?,
        val photoUrl: String?,
    )

    private val expectedSignatures = setOf(
        MemberSignature(
            displayName = "Папа",
            roleLabel = "Папа",
            avatarColor = "#2196F3",
            role = MemberRole.ADMIN,
            age = null,
            birthDate = null,
            photoUrl = null,
        ),
        MemberSignature(
            displayName = "Мама",
            roleLabel = "Мама",
            avatarColor = "#E91E63",
            role = MemberRole.PARENT,
            age = null,
            birthDate = null,
            photoUrl = null,
        ),
        MemberSignature(
            displayName = "Сын",
            roleLabel = "Сын",
            avatarColor = "#4CAF50",
            role = MemberRole.CHILD,
            age = null,
            birthDate = null,
            photoUrl = null,
        ),
        MemberSignature(
            displayName = "Дочь",
            roleLabel = "Дочь",
            avatarColor = "#FF9800",
            role = MemberRole.CHILD,
            age = null,
            birthDate = null,
            photoUrl = null,
        ),
    )

    fun matches(
        members: List<FamilyMember>,
        authenticatedUid: String?,
    ): Boolean {
        if (members.any { it.id.isBlank() }) return false
        val normalizedUid = authenticatedUid?.takeIf { it.isNotBlank() }
        val authenticatedRows = normalizedUid?.let { uid ->
            members.count { it.id == uid }
        } ?: 0
        if (authenticatedRows > 1) return false
        val legacyCandidates = normalizedUid?.let { uid ->
            members.filterNot { it.id == uid }
        } ?: members
        if (legacyCandidates.size != expectedSignatures.size) return false
        val maximumSize = expectedSignatures.size + if (normalizedUid == null) 0 else 1
        if (members.size !in expectedSignatures.size..maximumSize) return false

        val actualSignatures = legacyCandidates.mapTo(mutableSetOf()) { member ->
            MemberSignature(
                displayName = member.displayName,
                roleLabel = member.roleLabel,
                avatarColor = member.avatarColor,
                role = member.role,
                age = member.age,
                birthDate = member.birthDate,
                photoUrl = member.photoUrl,
            )
        }
        return actualSignatures.size == expectedSignatures.size &&
            actualSignatures == expectedSignatures
    }
}
