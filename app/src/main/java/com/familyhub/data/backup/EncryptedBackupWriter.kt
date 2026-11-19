package com.familyhub.data.backup

import android.content.Context
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportTempFileFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates encrypted .fhb backup files under filesDir/backups.
 * No UI and no Room restore in FH2-19B1.
 */
@Singleton
class EncryptedBackupWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val codec: EncryptedBackupCodec
) {
    fun write(
        exportData: ExportData,
        sourceFamilyId: String?,
        password: CharArray,
        createdAt: Long = System.currentTimeMillis()
    ): File {
        val backupsDir = File(context.filesDir, "backups")
        val exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(createdAt))
        val outputFile = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, exportDate)
        try {
            val payload = EncryptedBackupPayload(
                payloadVersion = EncryptedBackupFormat.PAYLOAD_VERSION,
                createdAt = createdAt,
                sourceFamilyId = sourceFamilyId,
                data = exportData
            )
            codec.encryptToFile(payload, password, outputFile)
            return outputFile
        } catch (e: Exception) {
            outputFile.delete()
            throw e
        }
    }
}
