package com.familyhub.data.backup

/**
 * Binary layout for FamilyHub encrypted backups (.fhb).
 *
 * ```
 * Offset Size Field
 * 0      8    Magic ASCII "FHUBBKP1"
 * 8      1    Format version (1)
 * 9      1    KDF id (1 = Argon2id)
 * 10     1    Cipher id (1 = AES-256-GCM)
 * 11     1    Argon2 version (0x13 = v1.3), included in AAD
 * 12     4    memoryCostKiB (uint32 BE)
 * 16     4    iterations (uint32 BE)
 * 20     2    parallelism (uint16 BE)
 * 22     2    keyLengthBytes (uint16 BE, must be 32)
 * 24     16   salt
 * 40     12   nonce
 * 52     …    ciphertext || GCM tag (16 bytes)
 * ```
 *
 * The entire 52-byte header is used as AES-GCM AAD.
 */
object EncryptedBackupFormat {
    const val MAGIC = "FHUBBKP1"
    const val FORMAT_VERSION: Byte = 1
    const val KDF_ARGON2ID: Byte = 1
    const val CIPHER_AES_256_GCM: Byte = 1
    /** Argon2 version 1.3 (`0x13` / decimal 19). */
    const val ARGON2_VERSION_13: Byte = 0x13
    const val HEADER_SIZE = 52
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val KEY_BYTES = 32
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_BYTES = 16
    const val PAYLOAD_VERSION = 1

    /** OWASP-oriented production Argon2id memory (19 MiB). Also the format-v1 minimum. */
    const val PRODUCTION_MEMORY_KIB = 19 * 1024
    const val PRODUCTION_ITERATIONS = 2
    const val PRODUCTION_PARALLELISM = 1

    const val MAX_FILE_BYTES = 64L * 1024L * 1024L
    /** Reject KDF params that would allow memory DoS before running Argon2. */
    const val MAX_KDF_MEMORY_KIB = 64 * 1024
    const val MAX_KDF_ITERATIONS = 6
    const val MAX_KDF_PARALLELISM = 4
    /** Format v1 must not be weaker than production. */
    const val MIN_KDF_MEMORY_KIB = PRODUCTION_MEMORY_KIB
    const val MIN_KDF_ITERATIONS = PRODUCTION_ITERATIONS
    const val MIN_KDF_PARALLELISM = PRODUCTION_PARALLELISM

    const val MIN_PASSWORD_CHARS = 8
    const val MAX_PASSWORD_CHARS = 256
    const val MAX_PASSWORD_UTF8_BYTES = 1024

    const val FHB_EXTENSION = "fhb"
    const val FHB_MIME_TYPE = "application/vnd.familyhub.backup"
}
