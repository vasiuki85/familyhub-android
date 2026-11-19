package com.familyhub.data.sync

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncExportSanitizerTest {

    @Test
    fun sanitize_stripsContentUrisFromMembersAndAttachments() {
        val snapshot = ExportData(
            members = listOf(
                ExportMember(
                    id = "user-1",
                    photoUrl = "content://com.familyhub/avatar"
                )
            ),
            tasks = listOf(
                ExportTask(
                    id = "task-1",
                    attachments = listOf(
                        ExportTaskAttachment(
                            id = "att-1",
                            name = "note.txt",
                            uri = "content://com.familyhub/doc"
                        ),
                        ExportTaskAttachment(
                            id = "att-2",
                            name = "remote.png",
                            uri = "https://example.com/remote.png"
                        )
                    )
                )
            )
        )

        val sanitized = SyncExportSanitizer.sanitize(snapshot)

        assertNull(sanitized.members.single().photoUrl)
        assertEquals(1, sanitized.tasks.single().attachments.size)
        assertEquals("https://example.com/remote.png", sanitized.tasks.single().attachments.single().uri)
    }

    @Test
    fun isRemoteUrl_acceptsHttpAndHttpsOnly() {
        assertTrue(SyncExportSanitizer.isRemoteUrl("https://cdn.example/avatar.png"))
        assertTrue(SyncExportSanitizer.isRemoteUrl("http://cdn.example/avatar.png"))
    }

    @Test
    fun sanitize_keepsBundledMemberAvatarButNotForTaskAttachment() {
        val token = "familyhub-avatar://08"
        val snapshot = ExportData(
            members = listOf(ExportMember(id = "user-1", photoUrl = token)),
            tasks = listOf(
                ExportTask(
                    id = "task-1",
                    attachments = listOf(
                        ExportTaskAttachment(id = "att-1", name = "bad", uri = token)
                    ),
                )
            ),
        )

        val sanitized = SyncExportSanitizer.sanitize(snapshot)

        assertEquals(token, sanitized.members.single().photoUrl)
        assertTrue(sanitized.tasks.single().attachments.isEmpty())
    }
}
