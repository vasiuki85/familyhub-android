package com.familyhub.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class EncryptedBackupCryptoParametersContractTest {

    @Test
    fun productionParametersMatchOwaspOrientedDefaults() {
        assertEquals("FHUBBKP1", EncryptedBackupFormat.MAGIC)
        assertEquals(1.toByte(), EncryptedBackupFormat.FORMAT_VERSION)
        assertEquals(1.toByte(), EncryptedBackupFormat.KDF_ARGON2ID)
        assertEquals(1.toByte(), EncryptedBackupFormat.CIPHER_AES_256_GCM)
        assertEquals(0x13.toByte(), EncryptedBackupFormat.ARGON2_VERSION_13)
        assertEquals(52, EncryptedBackupFormat.HEADER_SIZE)
        assertEquals(16, EncryptedBackupFormat.SALT_BYTES)
        assertEquals(12, EncryptedBackupFormat.NONCE_BYTES)
        assertEquals(32, EncryptedBackupFormat.KEY_BYTES)
        assertEquals(19 * 1024, EncryptedBackupFormat.PRODUCTION_MEMORY_KIB)
        assertEquals(2, EncryptedBackupFormat.PRODUCTION_ITERATIONS)
        assertEquals(1, EncryptedBackupFormat.PRODUCTION_PARALLELISM)
        assertEquals(EncryptedBackupFormat.PRODUCTION_MEMORY_KIB, EncryptedBackupFormat.MIN_KDF_MEMORY_KIB)
        assertEquals(EncryptedBackupFormat.PRODUCTION_ITERATIONS, EncryptedBackupFormat.MIN_KDF_ITERATIONS)
        assertEquals(EncryptedBackupFormat.PRODUCTION_PARALLELISM, EncryptedBackupFormat.MIN_KDF_PARALLELISM)
        assertEquals(64L * 1024L * 1024L, EncryptedBackupFormat.MAX_FILE_BYTES)
        assertEquals("application/vnd.familyhub.backup", EncryptedBackupFormat.FHB_MIME_TYPE)
        assertEquals("fhb", EncryptedBackupFormat.FHB_EXTENSION)
        assertEquals(8, EncryptedBackupFormat.MIN_PASSWORD_CHARS)
        assertEquals(256, EncryptedBackupFormat.MAX_PASSWORD_CHARS)
    }

    @Test
    fun productionHeaderWritesArgon2Version13IntoAadByte() {
        val header = EncryptedBackupHeaderCodec.createProductionHeader()
        val bytes = header.toByteArray()
        assertEquals(EncryptedBackupFormat.ARGON2_VERSION_13, bytes[11])
        assertEquals(EncryptedBackupFormat.ARGON2_VERSION_13, header.argon2Version)
    }
}
