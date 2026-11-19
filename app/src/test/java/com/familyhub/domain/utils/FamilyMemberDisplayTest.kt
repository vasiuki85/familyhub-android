package com.familyhub.domain.utils

import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyMemberDisplayTest {
    @Test
    fun displayLabel_prefersDisplayNameWhenPresent() {
        val member = member(displayName = "Anna", roleLabel = "Parent")

        assertEquals("Anna", member.displayLabel())
    }

    @Test
    fun displayLabel_fallsBackToRoleLabelWhenDisplayNameBlank() {
        val member = member(displayName = "   ", roleLabel = "Parent")

        assertEquals("Parent", member.displayLabel())
    }

    @Test
    fun displayLabel_fallsBackToIdWhenBothNamesBlank() {
        val member = member(displayName = "", roleLabel = "", id = "member-42")

        assertEquals("member-42", member.displayLabel())
    }

    private fun member(
        id: String = "member-1",
        displayName: String,
        roleLabel: String,
    ) = FamilyMember(
        id = id,
        displayName = displayName,
        roleLabel = roleLabel,
        avatarColor = "#000000",
        role = MemberRole.CHILD,
    )
}
