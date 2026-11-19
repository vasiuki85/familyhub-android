package com.familyhub.data.sync

import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncMemberExportTest {

    @Test
    fun toExportMember_keepsRemotePhotoUrl() {
        val member = member(photoUrl = "https://disk.yandex.ru/i/abc123")
        assertEquals("https://disk.yandex.ru/i/abc123", member.toExportMember().photoUrl)
    }

    @Test
    fun toExportMember_stripsLocalContentUri() {
        val member = member(photoUrl = "content://com.familyhub/avatar")
        assertNull(member.toExportMember().photoUrl)
    }

    @Test
    fun toExportMember_keepsBundledAvatarToken() {
        val member = member(photoUrl = "familyhub-avatar://03")
        assertEquals("familyhub-avatar://03", member.toExportMember().photoUrl)
    }

    private fun member(photoUrl: String?): FamilyMember = FamilyMember(
        id = "user-1",
        displayName = "Test",
        roleLabel = "Test",
        avatarColor = "#2196F3",
        role = MemberRole.PARENT,
        photoUrl = photoUrl
    )
}
