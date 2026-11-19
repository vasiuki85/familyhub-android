package com.familyhub.data.restore

import com.familyhub.data.backup.EncryptedBackupFormat
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class ResolvedRestoreFile(
    val canonicalFile: File
)

object RestoreFileAccess {
    /**
     * Validates app-private `.fhb` path and returns the canonical file.
     * Does not read file contents.
     */
    fun resolveCanonicalRestoreFile(file: File, filesDir: File, cacheDir: File): ResolvedRestoreFile {
        val canonical = file.canonicalFile
        if (!canonical.isFile) {
            throw RestoreValidationException("Restore file is missing")
        }
        if (canonical.extension.lowercase() != EncryptedBackupFormat.FHB_EXTENSION) {
            throw RestoreValidationException("Restore file must use .fhb extension")
        }
        val filesRoot = filesDir.canonicalFile
        val cacheRoot = cacheDir.canonicalFile
        val inAppPrivate = canonical.isInside(filesRoot) || canonical.isInside(cacheRoot)
        if (!inAppPrivate) {
            throw SecurityException("Restore rejected for path outside app-private storage")
        }
        return ResolvedRestoreFile(canonicalFile = canonical)
    }

    /**
     * Reads header+ciphertext exactly once with a hard 64 MiB bound.
     * Rejects truncate, trailing growth, or size change during the read.
     */
    fun readExactBounded(file: File): ByteArray {
        val expectedLength = file.length()
        if (expectedLength <= 0L) {
            throw RestoreValidationException("Restore file is empty")
        }
        if (expectedLength > EncryptedBackupFormat.MAX_FILE_BYTES) {
            throw RestoreValidationException("Restore file exceeds maximum size")
        }
        if (expectedLength < EncryptedBackupFormat.HEADER_SIZE + EncryptedBackupFormat.GCM_TAG_BYTES) {
            throw RestoreValidationException("Restore file is truncated")
        }
        if (expectedLength > Int.MAX_VALUE) {
            throw RestoreValidationException("Restore file exceeds maximum size")
        }
        val size = expectedLength.toInt()
        val bytes = ByteArray(size)
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < size) {
                val read = input.read(bytes, offset, size - offset)
                if (read < 0) {
                    throw RestoreValidationException("Restore file truncated during read")
                }
                offset += read
            }
            if (input.read() != -1) {
                throw RestoreValidationException("Restore file grew during read")
            }
        }
        if (file.length() != expectedLength) {
            throw RestoreValidationException("Restore file changed during read")
        }
        return bytes
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

private fun File.isInside(root: File): Boolean {
    val filePath = canonicalFile.path
    val rootPath = root.canonicalFile.path.trimEnd(File.separatorChar)
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}
