package com.familyhub.data.backup

import com.familyhub.data.export.ExportData
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedBackupPayload(
    val payloadVersion: Int = EncryptedBackupFormat.PAYLOAD_VERSION,
    val createdAt: Long,
    val sourceFamilyId: String? = null,
    val data: ExportData
)
