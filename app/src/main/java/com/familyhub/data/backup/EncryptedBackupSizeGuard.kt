package com.familyhub.data.backup

/**
 * Pure size checks for encrypted backup framing (no I/O, no KDF).
 */
object EncryptedBackupSizeGuard {
    fun maxPlaintextBytes(): Long =
        EncryptedBackupFormat.MAX_FILE_BYTES -
            EncryptedBackupFormat.HEADER_SIZE -
            EncryptedBackupFormat.GCM_TAG_BYTES

    fun ensurePlaintextFits(plaintextByteCount: Int) {
        if (plaintextByteCount < 0) {
            throw EncryptedBackupFormatException("Encrypted backup plaintext size is invalid")
        }
        if (plaintextByteCount.toLong() > maxPlaintextBytes()) {
            throw EncryptedBackupFormatException("Encrypted backup plaintext exceeds maximum size")
        }
    }
}
