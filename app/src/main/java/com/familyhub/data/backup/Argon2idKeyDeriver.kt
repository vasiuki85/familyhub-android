package com.familyhub.data.backup

/**
 * Argon2id key derivation. Implementations must overwrite sensitive buffers they allocate
 * when the caller is done with the returned key (caller clears the key).
 */
fun interface Argon2idKeyDeriver {
    fun deriveKey(
        passwordUtf8: ByteArray,
        salt: ByteArray,
        memoryCostKiB: Int,
        iterations: Int,
        parallelism: Int,
        keyLengthBytes: Int
    ): ByteArray
}
