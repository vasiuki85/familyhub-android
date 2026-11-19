package com.familyhub.data.sync

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskAttachment
import com.familyhub.domain.utils.BundledFamilyAvatar

/**
 * Strips device-local URIs before cloud sync. Avatars and attachments must use
 * remote https URLs once Firebase Storage is implemented.
 */
object SyncExportSanitizer {
    fun sanitize(snapshot: ExportData): ExportData {
        return snapshot.copy(
            members = snapshot.members.map(::sanitizeMember),
            tasks = snapshot.tasks.map(::sanitizeTask)
        )
    }

    private fun sanitizeMember(member: ExportMember): ExportMember {
        return member.copy(photoUrl = member.photoUrl?.takeIf(::isShareableMemberPhoto))
    }

    private fun sanitizeTask(task: ExportTask): ExportTask {
        return task.copy(
            attachments = task.attachments.mapNotNull(::sanitizeAttachment)
        )
    }

    private fun sanitizeAttachment(attachment: ExportTaskAttachment): ExportTaskAttachment? {
        val remoteUri = attachment.uri.takeIf(::isRemoteUrl) ?: return null
        return attachment.copy(uri = remoteUri)
    }

    fun isRemoteUrl(value: String): Boolean {
        return value.startsWith("https://", ignoreCase = true)
            || value.startsWith("http://", ignoreCase = true)
    }

    fun isShareableMemberPhoto(value: String): Boolean {
        return isRemoteUrl(value) || BundledFamilyAvatar.isToken(value)
    }
}
