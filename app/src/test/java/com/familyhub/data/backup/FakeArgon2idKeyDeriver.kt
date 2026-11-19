package com.familyhub.data.backup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Deterministic non-Argon2 deriver for fast JVM unit tests.
 * Headers under test must still carry production-strength KDF parameters.
 */
class FakeArgon2idKeyDeriver(
    private val keyLengthOverride: Int? = null
) : Argon2idKeyDeriver {
    var calls: Int = 0
        private set

    override fun deriveKey(
        passwordUtf8: ByteArray,
        salt: ByteArray,
        memoryCostKiB: Int,
        iterations: Int,
        parallelism: Int,
        keyLengthBytes: Int
    ): ByteArray {
        calls++
        EncryptedBackupHeaderCodec.validateKdfLimits(memoryCostKiB, iterations, parallelism)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(passwordUtf8)
        digest.update(0)
        digest.update(salt)
        val params = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        params.putInt(memoryCostKiB)
        params.putInt(iterations)
        params.putInt(parallelism)
        digest.update(params.array())
        val full = digest.digest()
        val length = keyLengthOverride ?: keyLengthBytes
        return when {
            length == full.size -> full
            length < full.size -> full.copyOf(length)
            else -> ByteArray(length) { index -> full[index % full.size] }
        }
    }
}
