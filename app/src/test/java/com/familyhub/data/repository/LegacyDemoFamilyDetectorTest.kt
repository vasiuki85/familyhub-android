package com.familyhub.data.repository

import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyDemoFamilyDetectorTest {
    @Test
    fun exactLegacySignatureWithDifferentAuthenticatedUid_matches() {
        assertTrue(
            LegacyDemoFamilyDetector.matches(
                members = legacyMembers().reversed(),
                authenticatedUid = "firebase-uid",
            )
        )
    }

    @Test
    fun authenticatedBootstrapMemberAlongsideExactLegacyFamily_matches() {
        val authenticated = member(
            id = "firebase-uid",
            name = "vasuiki888",
            color = "#2196F3",
            role = MemberRole.ADMIN,
        )

        assertTrue(
            LegacyDemoFamilyDetector.matches(
                members = legacyMembers() + authenticated,
                authenticatedUid = "firebase-uid",
            )
        )
    }

    @Test
    fun legacyMemberIdEqualsAuthenticatedUid_failsClosed() {
        val members = legacyMembers().toMutableList()
        members[2] = members[2].copy(id = "firebase-uid")

        assertFalse(LegacyDemoFamilyDetector.matches(members, "firebase-uid"))
    }

    @Test
    fun exactLegacySignatureWithoutAuthenticatedUid_matches() {
        assertTrue(LegacyDemoFamilyDetector.matches(legacyMembers(), null))
        assertTrue(LegacyDemoFamilyDetector.matches(legacyMembers(), ""))
    }

    @Test
    fun additionalOrMissingMember_failsClosed() {
        assertFalse(
            LegacyDemoFamilyDetector.matches(
                legacyMembers() + legacyMembers().first().copy(id = "extra"),
                "firebase-uid",
            )
        )
        assertFalse(
            LegacyDemoFamilyDetector.matches(
                legacyMembers().dropLast(1),
                "firebase-uid",
            )
        )
    }

    @Test
    fun editedProfileField_failsClosed() {
        val members = legacyMembers().toMutableList()
        members[0] = members[0].copy(photoUrl = "https://example.test/photo.jpg")

        assertFalse(LegacyDemoFamilyDetector.matches(members, "firebase-uid"))
    }

    @Test
    fun editedRoleOrColor_failsClosed() {
        val changedRole = legacyMembers().toMutableList().apply {
            this[1] = this[1].copy(role = MemberRole.CHILD)
        }
        val changedColor = legacyMembers().toMutableList().apply {
            this[3] = this[3].copy(avatarColor = "#ff9800")
        }

        assertFalse(LegacyDemoFamilyDetector.matches(changedRole, "firebase-uid"))
        assertFalse(LegacyDemoFamilyDetector.matches(changedColor, "firebase-uid"))
    }

    @Test
    fun duplicateSignatureWithFourRows_failsClosed() {
        val members = legacyMembers().toMutableList()
        members[3] = members[2].copy(id = "different-id")

        assertFalse(LegacyDemoFamilyDetector.matches(members, "firebase-uid"))
    }

    private fun legacyMembers(): List<FamilyMember> {
        return listOf(
            member("legacy-papa", "Папа", "#2196F3", MemberRole.ADMIN),
            member("legacy-mama", "Мама", "#E91E63", MemberRole.PARENT),
            member("legacy-son", "Сын", "#4CAF50", MemberRole.CHILD),
            member("legacy-daughter", "Дочь", "#FF9800", MemberRole.CHILD),
        )
    }

    private fun member(
        id: String,
        name: String,
        color: String,
        role: MemberRole,
    ): FamilyMember {
        return FamilyMember(
            id = id,
            displayName = name,
            roleLabel = name,
            avatarColor = color,
            role = role,
            age = null,
            birthDate = null,
            photoUrl = null,
        )
    }
}
