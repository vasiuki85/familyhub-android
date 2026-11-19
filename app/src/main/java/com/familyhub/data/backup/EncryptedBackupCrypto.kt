package com.familyhub.data.backup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedBackupHeader(
    val formatVersion: Byte,
    val kdfId: Byte,
    val cipherId: Byte,
    val argon2Version: Byte,
    val memoryCostKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val keyLengthBytes: Int,
    val salt: ByteArray,
    val nonce: ByteArray
) {
    fun toByteArray(): ByteArray {
        if (salt.size != EncryptedBackupFormat.SALT_BYTES) {
            throw EncryptedBackupFormatException("Encrypted backup salt length is invalid")
        }
        if (nonce.size != EncryptedBackupFormat.NONCE_BYTES) {
            throw EncryptedBackupFormatException("Encrypted backup nonce length is invalid")
        }
        if (keyLengthBytes != EncryptedBackupFormat.KEY_BYTES) {
            throw EncryptedBackupFormatException("Encrypted backup key length is invalid")
        }
        val buffer = ByteBuffer.allocate(EncryptedBackupFormat.HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.putUtf8Magic(EncryptedBackupFormat.MAGIC)
        buffer.put(formatVersion)
        buffer.put(kdfId)
        buffer.put(cipherId)
        buffer.put(argon2Version)
        buffer.putInt(memoryCostKiB)
        buffer.putInt(iterations)
        buffer.putShort(parallelism.toShort())
        buffer.putShort(keyLengthBytes.toShort())
        buffer.put(salt)
        buffer.put(nonce)
        if (buffer.position() != EncryptedBackupFormat.HEADER_SIZE) {
            throw EncryptedBackupFormatException("Encrypted backup header encoding is invalid")
        }
        return buffer.array()
    }
}

object EncryptedBackupHeaderCodec {
    fun createProductionHeader(secureRandom: SecureRandom = SecureRandom()): EncryptedBackupHeader {
        return createHeader(
            secureRandom = secureRandom,
            memoryCostKiB = EncryptedBackupFormat.PRODUCTION_MEMORY_KIB,
            iterations = EncryptedBackupFormat.PRODUCTION_ITERATIONS,
            parallelism = EncryptedBackupFormat.PRODUCTION_PARALLELISM
        )
    }

    fun createHeader(
        secureRandom: SecureRandom = SecureRandom(),
        memoryCostKiB: Int,
        iterations: Int,
        parallelism: Int,
        argon2Version: Byte = EncryptedBackupFormat.ARGON2_VERSION_13
    ): EncryptedBackupHeader {
        val salt = ByteArray(EncryptedBackupFormat.SALT_BYTES)
        val nonce = ByteArray(EncryptedBackupFormat.NONCE_BYTES)
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(nonce)
        return EncryptedBackupHeader(
            formatVersion = EncryptedBackupFormat.FORMAT_VERSION,
            kdfId = EncryptedBackupFormat.KDF_ARGON2ID,
            cipherId = EncryptedBackupFormat.CIPHER_AES_256_GCM,
            argon2Version = argon2Version,
            memoryCostKiB = memoryCostKiB,
            iterations = iterations,
            parallelism = parallelism,
            keyLengthBytes = EncryptedBackupFormat.KEY_BYTES,
            salt = salt,
            nonce = nonce
        )
    }

    fun parse(headerBytes: ByteArray): EncryptedBackupHeader {
        if (headerBytes.size < EncryptedBackupFormat.HEADER_SIZE) {
            throw EncryptedBackupFormatException("Encrypted backup header is truncated")
        }
        val buffer = ByteBuffer.wrap(headerBytes, 0, EncryptedBackupFormat.HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
        val magicBytes = ByteArray(EncryptedBackupFormat.MAGIC.length)
        buffer.get(magicBytes)
        val magic = String(magicBytes, StandardCharsets.US_ASCII)
        if (magic != EncryptedBackupFormat.MAGIC) {
            throw EncryptedBackupUnsupportedFormatException("Unsupported encrypted backup magic")
        }
        val formatVersion = buffer.get()
        if (formatVersion != EncryptedBackupFormat.FORMAT_VERSION) {
            throw EncryptedBackupUnsupportedFormatException(
                "Unsupported encrypted backup format version"
            )
        }
        val kdfId = buffer.get()
        val cipherId = buffer.get()
        val argon2Version = buffer.get()
        if (kdfId != EncryptedBackupFormat.KDF_ARGON2ID) {
            throw EncryptedBackupUnsupportedFormatException("Unsupported encrypted backup KDF")
        }
        if (cipherId != EncryptedBackupFormat.CIPHER_AES_256_GCM) {
            throw EncryptedBackupUnsupportedFormatException("Unsupported encrypted backup cipher")
        }
        if (argon2Version != EncryptedBackupFormat.ARGON2_VERSION_13) {
            throw EncryptedBackupUnsupportedFormatException(
                "Unsupported encrypted backup Argon2 version"
            )
        }
        val memoryCostKiB = buffer.int
        val iterations = buffer.int
        val parallelism = buffer.short.toInt() and 0xFFFF
        val keyLengthBytes = buffer.short.toInt() and 0xFFFF
        if (keyLengthBytes != EncryptedBackupFormat.KEY_BYTES) {
            throw EncryptedBackupFormatException("Unsupported encrypted backup key length")
        }
        validateKdfLimits(memoryCostKiB, iterations, parallelism)
        val salt = ByteArray(EncryptedBackupFormat.SALT_BYTES)
        buffer.get(salt)
        val nonce = ByteArray(EncryptedBackupFormat.NONCE_BYTES)
        buffer.get(nonce)
        return EncryptedBackupHeader(
            formatVersion = formatVersion,
            kdfId = kdfId,
            cipherId = cipherId,
            argon2Version = argon2Version,
            memoryCostKiB = memoryCostKiB,
            iterations = iterations,
            parallelism = parallelism,
            keyLengthBytes = keyLengthBytes,
            salt = salt,
            nonce = nonce
        )
    }

    fun validateKdfLimits(memoryCostKiB: Int, iterations: Int, parallelism: Int) {
        if (memoryCostKiB < EncryptedBackupFormat.MIN_KDF_MEMORY_KIB ||
            memoryCostKiB > EncryptedBackupFormat.MAX_KDF_MEMORY_KIB
        ) {
            throw EncryptedBackupFormatException("Encrypted backup KDF memory cost rejected")
        }
        if (iterations < EncryptedBackupFormat.MIN_KDF_ITERATIONS ||
            iterations > EncryptedBackupFormat.MAX_KDF_ITERATIONS
        ) {
            throw EncryptedBackupFormatException("Encrypted backup KDF iterations rejected")
        }
        if (parallelism < EncryptedBackupFormat.MIN_KDF_PARALLELISM ||
            parallelism > EncryptedBackupFormat.MAX_KDF_PARALLELISM
        ) {
            throw EncryptedBackupFormatException("Encrypted backup KDF parallelism rejected")
        }
    }

    /**
     * Fail-closed checks before password encoding / KDF on the encrypt path.
     * Rejects headers that would produce a file the decoder cannot read.
     */
    fun validateHeaderForEncryption(header: EncryptedBackupHeader) {
        if (header.formatVersion != EncryptedBackupFormat.FORMAT_VERSION) {
            throw EncryptedBackupUnsupportedFormatException(
                "Unsupported encrypted backup format version"
            )
        }
        if (header.kdfId != EncryptedBackupFormat.KDF_ARGON2ID) {
            throw EncryptedBackupUnsupportedFormatException("Unsupported encrypted backup KDF")
        }
        if (header.cipherId != EncryptedBackupFormat.CIPHER_AES_256_GCM) {
            throw EncryptedBackupUnsupportedFormatException("Unsupported encrypted backup cipher")
        }
        if (header.argon2Version != EncryptedBackupFormat.ARGON2_VERSION_13) {
            throw EncryptedBackupUnsupportedFormatException(
                "Unsupported encrypted backup Argon2 version"
            )
        }
        if (header.keyLengthBytes != EncryptedBackupFormat.KEY_BYTES) {
            throw EncryptedBackupFormatException("Unsupported encrypted backup key length")
        }
        if (header.salt.size != EncryptedBackupFormat.SALT_BYTES) {
            throw EncryptedBackupFormatException("Encrypted backup salt length is invalid")
        }
        if (header.nonce.size != EncryptedBackupFormat.NONCE_BYTES) {
            throw EncryptedBackupFormatException("Encrypted backup nonce length is invalid")
        }
        validateKdfLimits(header.memoryCostKiB, header.iterations, header.parallelism)
    }

    fun requireDerivedKeyLength(key: ByteArray) {
        if (key.size != EncryptedBackupFormat.KEY_BYTES) {
            throw EncryptedBackupFormatException("Derived key length is invalid")
        }
    }
}

internal object AesGcmBackupCipher {
    fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(EncryptedBackupFormat.GCM_TAG_BITS, nonce)
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray =
        decrypt(
            key = key,
            nonce = nonce,
            input = ciphertext,
            aadOffset = 0,
            aadLength = aad.size,
            aadBuffer = aad,
            ciphertextOffset = 0,
            ciphertextLength = ciphertext.size
        )

    /**
     * Decrypts a ciphertext‖tag region from [input] without copying the ciphertext.
     * AAD may come from the same buffer via [aadBuffer]/[aadOffset]/[aadLength].
     */
    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        aadBuffer: ByteArray,
        aadOffset: Int,
        aadLength: Int,
        ciphertextOffset: Int,
        ciphertextLength: Int
    ): ByteArray {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(EncryptedBackupFormat.GCM_TAG_BITS, nonce)
            )
            cipher.updateAAD(aadBuffer, aadOffset, aadLength)
            return cipher.doFinal(input, ciphertextOffset, ciphertextLength)
        } catch (_: Exception) {
            throw EncryptedBackupAuthException()
        }
    }
}
