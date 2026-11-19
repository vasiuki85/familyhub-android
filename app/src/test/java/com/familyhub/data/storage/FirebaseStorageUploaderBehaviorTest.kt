package com.familyhub.data.storage

import com.familyhub.data.sync.SyncExportSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirebaseStorageUploaderBehaviorTest {

    @Test
    fun remoteUrlsArePassedThroughUnchanged() {
        val url = "https://firebasestorage.googleapis.com/v0/b/demo/o/avatar.jpg"
        assertEquals(url, SyncExportSanitizer.sanitize(
            com.familyhub.data.export.ExportData(
                members = listOf(
                    com.familyhub.data.export.ExportMember(id = "u1", photoUrl = url)
                )
            )
        ).members.single().photoUrl)
    }

    @Test
    fun localContentUrisAreNotSynced() {
        val sanitized = SyncExportSanitizer.sanitize(
            com.familyhub.data.export.ExportData(
                members = listOf(
                    com.familyhub.data.export.ExportMember(
                        id = "u1",
                        photoUrl = "content://com.familyhub/avatar"
                    )
                )
            )
        )
        assertNull(sanitized.members.single().photoUrl)
    }
}
