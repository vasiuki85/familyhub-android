package com.familyhub.data.restore

import com.familyhub.data.backup.EncryptedBackupFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.io.path.createTempDirectory

class RestoreFileAccessTest {

    @Test
    fun resolveCanonical_acceptsAppPrivateFhb() {
        val root = createTempDirectory(prefix = "fh_restore_access_").toFile()
        try {
            val filesDir = File(root, "files").apply { mkdirs() }
            val cacheDir = File(root, "cache").apply { mkdirs() }
            val file = File(filesDir, "a.fhb").apply { writeBytes(ByteArray(80) { 1 }) }

            val resolved = RestoreFileAccess.resolveCanonicalRestoreFile(file, filesDir, cacheDir)

            assertEquals(file.canonicalFile, resolved.canonicalFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readExactBounded_rejectsTruncatedFile() {
        val root = createTempDirectory(prefix = "fh_restore_trunc_").toFile()
        try {
            val file = File(root, "a.fhb").apply { writeBytes(ByteArray(10) { 1 }) }
            assertThrows(RestoreValidationException::class.java) {
                RestoreFileAccess.readExactBounded(file)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readExactBounded_rejectsOverMaxSize() {
        val root = createTempDirectory(prefix = "fh_restore_max_").toFile()
        try {
            val file = File(root, "a.fhb")
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(EncryptedBackupFormat.MAX_FILE_BYTES + 1)
            }
            assertThrows(RestoreValidationException::class.java) {
                RestoreFileAccess.readExactBounded(file)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readExactBounded_returnsExactBytesAndMatchingFingerprint() {
        val root = createTempDirectory(prefix = "fh_restore_exact_").toFile()
        try {
            val payload = ByteArray(80) { index -> index.toByte() }
            val file = File(root, "a.fhb").apply { writeBytes(payload) }
            val bytes = RestoreFileAccess.readExactBounded(file)
            assertArrayEquals(payload, bytes)
            assertEquals(RestoreFileAccess.sha256Hex(payload), RestoreFileAccess.sha256Hex(bytes))
            assertTrue(bytes.size <= EncryptedBackupFormat.MAX_FILE_BYTES)
        } finally {
            root.deleteRecursively()
        }
    }
}
