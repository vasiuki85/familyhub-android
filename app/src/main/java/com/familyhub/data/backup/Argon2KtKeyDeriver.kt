package com.familyhub.data.backup

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2KtResult
import com.lambdapioneer.argon2kt.Argon2Mode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production Argon2id via [argon2kt](https://github.com/lambdapioneer/argon2kt) (MIT),
 * JNI binding to the PHC reference implementation. Suitable for minSdk 24.
 *
 * After copying the derived key, direct buffers inside [Argon2KtResult] are overwritten
 * (argon2kt FAQ: direct buffers exist so secrets can be wiped after use).
 */
@Singleton
class Argon2KtKeyDeriver @Inject constructor() : Argon2idKeyDeriver {
    private val argon2 = Argon2Kt()

    override fun deriveKey(
        passwordUtf8: ByteArray,
        salt: ByteArray,
        memoryCostKiB: Int,
        iterations: Int,
        parallelism: Int,
        keyLengthBytes: Int
    ): ByteArray {
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = passwordUtf8,
            salt = salt,
            tCostInIterations = iterations,
            mCostInKibibyte = memoryCostKiB,
            parallelism = parallelism,
            hashLengthInBytes = keyLengthBytes
        )
        return Argon2KtResultExtractor.copyRawHashAndWipe(result)
    }
}

/**
 * Copies the raw hash out of an [Argon2KtResult], then overwrites both direct buffers
 * for the full [java.nio.ByteBuffer.capacity], including when extraction fails.
 */
internal object Argon2KtResultExtractor {
    fun copyRawHashAndWipe(result: Argon2KtResult): ByteArray =
        extractAndWipe(result) { it.rawHashAsByteArray() }

    fun <T> extractAndWipe(
        result: Argon2KtResult,
        extract: (Argon2KtResult) -> T
    ): T {
        try {
            return extract(result)
        } finally {
            PasswordEncoding.wipe(result.rawHash)
            PasswordEncoding.wipe(result.encodedOutput)
        }
    }
}
