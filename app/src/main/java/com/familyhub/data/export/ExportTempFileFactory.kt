package com.familyhub.data.export

import java.io.File

object ExportTempFileFactory {
    fun createExportCacheFile(
        cacheDir: File,
        format: ExportFormat,
        exportDate: String
    ): File {
        require(cacheDir.mkdirs() || cacheDir.isDirectory) {
            "Export cache directory is not available: ${cacheDir.path}"
        }
        val safeDate = sanitizeExportDate(exportDate)
        val suffix = when (format) {
            ExportFormat.CSV -> ".csv"
            ExportFormat.JSON -> ".json"
        }
        val prefix = "${ExportShareFileGuard.EXPORT_CACHE_PREFIX}${safeDate}_"
        return File.createTempFile(prefix, suffix, cacheDir)
    }

    fun createBackupFile(
        backupsDir: File,
        exportDate: String
    ): File {
        require(backupsDir.mkdirs() || backupsDir.isDirectory) {
            "Backup directory is not available: ${backupsDir.path}"
        }
        val safeDate = sanitizeExportDate(exportDate)
        val prefix = "${ExportShareFileGuard.BACKUP_FILE_PREFIX}${safeDate}_"
        return File.createTempFile(prefix, ".json", backupsDir)
    }

    fun createEncryptedBackupFile(
        backupsDir: File,
        exportDate: String
    ): File {
        require(backupsDir.mkdirs() || backupsDir.isDirectory) {
            "Backup directory is not available: ${backupsDir.path}"
        }
        val safeDate = sanitizeExportDate(exportDate)
        val prefix = "${ExportShareFileGuard.BACKUP_FILE_PREFIX}${safeDate}_"
        return File.createTempFile(prefix, ".fhb", backupsDir)
    }

    private fun sanitizeExportDate(exportDate: String): String =
        exportDate.replace(":", "-").replace(" ", "_")
}
