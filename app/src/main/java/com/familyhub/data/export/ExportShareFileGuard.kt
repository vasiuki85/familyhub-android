package com.familyhub.data.export

import com.familyhub.data.backup.EncryptedBackupFormat
import java.io.File

data class ValidatedShareFile(
    val file: File,
    val mimeType: String,
    val extension: String
)

/**
 * Validates plaintext export/backup and encrypted .fhb files before sharing via FileProvider.
 */
class ExportShareFileGuard(
    private val cacheDir: File,
    private val backupsDir: File,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun validateShareable(file: File): ValidatedShareFile {
        val canonical = file.canonicalFile
        if (!canonical.isFile) {
            throw IllegalArgumentException("Share rejected: path is not a file: ${canonical.path}")
        }

        val extension = canonical.extension.lowercase()
        if (extension !in ALLOWED_EXTENSIONS) {
            throw IllegalArgumentException("Unsupported export file extension: .$extension")
        }

        val cacheRoot = cacheDir.canonicalFile
        val backupsRoot = backupsDir.canonicalFile
        val inCache = canonical.isInside(cacheRoot)
        val inBackups = canonical.isInside(backupsRoot)

        if (!inCache && !inBackups) {
            throw SecurityException("Share rejected for path outside export cache/backups: ${canonical.path}")
        }

        if (inBackups) {
            when (extension) {
                JSON_EXTENSION -> {
                    if (!canonical.name.startsWith(BACKUP_FILE_PREFIX)) {
                        throw SecurityException("Unexpected backup file name: ${canonical.name}")
                    }
                }
                EncryptedBackupFormat.FHB_EXTENSION -> {
                    if (!canonical.name.startsWith(BACKUP_FILE_PREFIX)) {
                        throw SecurityException("Unexpected backup file name: ${canonical.name}")
                    }
                }
                else -> throw IllegalArgumentException("Backup files must use .json or .fhb extension")
            }
        }

        if (inCache) {
            if (!canonical.name.startsWith(EXPORT_CACHE_PREFIX)) {
                throw SecurityException("Unexpected cache export file name: ${canonical.name}")
            }
            if (extension == EncryptedBackupFormat.FHB_EXTENSION) {
                throw SecurityException("Encrypted backups must live under filesDir/backups")
            }
        }

        return ValidatedShareFile(
            file = canonical,
            mimeType = mimeTypeFor(extension),
            extension = extension
        )
    }

    /**
     * Removes cache export files older than [STALE_EXPORT_MAX_AGE_MS].
     * Fresh exports are kept so an open share Intent is not invalidated.
     */
    fun cleanupStaleExportCache(now: Long = clock()) {
        if (!cacheDir.isDirectory) {
            return
        }
        val cutoff = now - STALE_EXPORT_MAX_AGE_MS
        cacheDir.listFiles().orEmpty().forEach { candidate ->
            if (!candidate.isFile) {
                return@forEach
            }
            if (!candidate.name.startsWith(EXPORT_CACHE_PREFIX)) {
                return@forEach
            }
            val extension = candidate.extension.lowercase()
            if (extension !in CACHE_CLEANUP_EXTENSIONS) {
                return@forEach
            }
            if (candidate.lastModified() < cutoff) {
                candidate.delete()
            }
        }
    }

    companion object {
        const val EXPORT_CACHE_PREFIX = "familyhub_export_"
        const val BACKUP_FILE_PREFIX = "familyhub_backup_"
        const val STALE_EXPORT_MAX_AGE_MS = 24L * 60L * 60L * 1000L
        private const val JSON_EXTENSION = "json"
        private val ALLOWED_EXTENSIONS = setOf("csv", JSON_EXTENSION, EncryptedBackupFormat.FHB_EXTENSION)
        private val CACHE_CLEANUP_EXTENSIONS = setOf("csv", JSON_EXTENSION)

        fun mimeTypeFor(extension: String): String {
            return when (extension.lowercase()) {
                "csv" -> "text/csv"
                JSON_EXTENSION -> "application/json"
                EncryptedBackupFormat.FHB_EXTENSION -> EncryptedBackupFormat.FHB_MIME_TYPE
                else -> throw IllegalArgumentException("Unsupported export file extension: .$extension")
            }
        }
    }
}

private fun File.isInside(root: File): Boolean {
    val filePath = canonicalFile.path
    val rootPath = root.canonicalFile.path.trimEnd(File.separatorChar)
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}
