package com.familyhub.data.backup

import androidx.annotation.VisibleForTesting
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Encrypts/decrypts versioned .fhb backups. Does not touch UI or Room restore.
 *
 * Password ownership: the caller must clear the password [CharArray] in a `finally` block after
 * these methods return. This class only clears temporary UTF-8 / key / plaintext buffers.
 */
class EncryptedBackupCodec(
    private val keyDeriver: Argon2idKeyDeriver,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val json: Json = defaultJson,
    /**
     * Test seam for size-guard coverage without allocating huge payloads.
     * Production DI uses the `@Inject` constructor (JSON encoder only).
     */
    private val plaintextEncoder: ((EncryptedBackupPayload) -> ByteArray)? = null
) {
    @Inject
    constructor(keyDeriver: Argon2idKeyDeriver) : this(
        keyDeriver = keyDeriver,
        secureRandom = SecureRandom(),
        json = defaultJson,
        plaintextEncoder = null
    )

    /** Production encrypt path: always uses a freshly generated production header. */
    fun encryptToFile(
        payload: EncryptedBackupPayload,
        password: CharArray,
        outputFile: File
    ) {
        encryptToFile(
            payload = payload,
            password = password,
            outputFile = outputFile,
            header = EncryptedBackupHeaderCodec.createProductionHeader(secureRandom)
        )
    }

    /**
     * Test/internal encrypt path with an explicit header.
     * Validates the full header fail-closed before password encoding / KDF.
     */
    @VisibleForTesting
    internal fun encryptToFile(
        payload: EncryptedBackupPayload,
        password: CharArray,
        outputFile: File,
        header: EncryptedBackupHeader
    ) {
        var passwordBytes: ByteArray? = null
        var key: ByteArray? = null
        var plaintext: ByteArray? = null
        try {
            EncryptedBackupHeaderCodec.validateHeaderForEncryption(header)
            passwordBytes = PasswordEncoding.validateAndEncodeUtf8(password)
            plaintext = encodePayload(payload)
            EncryptedBackupSizeGuard.ensurePlaintextFits(plaintext.size)

            key = keyDeriver.deriveKey(
                passwordUtf8 = passwordBytes,
                salt = header.salt,
                memoryCostKiB = header.memoryCostKiB,
                iterations = header.iterations,
                parallelism = header.parallelism,
                keyLengthBytes = header.keyLengthBytes
            )
            EncryptedBackupHeaderCodec.requireDerivedKeyLength(key)

            val headerBytes = header.toByteArray()
            val ciphertext = AesGcmBackupCipher.encrypt(
                key = key,
                nonce = header.nonce,
                aad = headerBytes,
                plaintext = plaintext
            )
            outputFile.outputStream().use { out ->
                out.write(headerBytes)
                out.write(ciphertext)
            }
        } catch (e: EncryptedBackupPasswordException) {
            outputFile.delete()
            throw e
        } catch (e: EncryptedBackupFormatException) {
            outputFile.delete()
            throw e
        } catch (e: EncryptedBackupUnsupportedFormatException) {
            outputFile.delete()
            throw e
        } catch (e: EncryptedBackupAuthException) {
            outputFile.delete()
            throw e
        } catch (e: Exception) {
            outputFile.delete()
            if (e is IOException) throw e
            throw IOException("Failed to write encrypted backup", e)
        } finally {
            PasswordEncoding.wipe(passwordBytes)
            PasswordEncoding.wipe(key)
            PasswordEncoding.wipe(plaintext)
        }
    }

    fun decryptFromFile(
        inputFile: File,
        password: CharArray
    ): EncryptedBackupPayload {
        val fileLength = inputFile.length()
        if (fileLength <= 0L) {
            throw EncryptedBackupFormatException("Encrypted backup is empty")
        }
        if (fileLength > EncryptedBackupFormat.MAX_FILE_BYTES) {
            throw EncryptedBackupFormatException("Encrypted backup exceeds maximum size")
        }
        if (fileLength < EncryptedBackupFormat.HEADER_SIZE + EncryptedBackupFormat.GCM_TAG_BYTES) {
            throw EncryptedBackupFormatException("Encrypted backup is truncated")
        }
        val bytes = ByteArray(fileLength.toInt())
        RandomAccessFile(inputFile, "r").use { raf ->
            raf.readFully(bytes)
        }
        return decryptFromBytes(bytes, password)
    }

    /**
     * Decrypts an already-loaded header‖ciphertext buffer.
     * Callers that need a fingerprint must hash [fileBytes] before invoking this (before KDF).
     */
    fun decryptFromBytes(
        fileBytes: ByteArray,
        password: CharArray
    ): EncryptedBackupPayload {
        var passwordBytes: ByteArray? = null
        var key: ByteArray? = null
        var plaintext: ByteArray? = null
        try {
            if (fileBytes.isEmpty()) {
                throw EncryptedBackupFormatException("Encrypted backup is empty")
            }
            if (fileBytes.size > EncryptedBackupFormat.MAX_FILE_BYTES) {
                throw EncryptedBackupFormatException("Encrypted backup exceeds maximum size")
            }
            if (fileBytes.size < EncryptedBackupFormat.HEADER_SIZE + EncryptedBackupFormat.GCM_TAG_BYTES) {
                throw EncryptedBackupFormatException("Encrypted backup is truncated")
            }
            // Parse reads only the header window; ciphertext is decrypted in-place (no copy).
            val header = EncryptedBackupHeaderCodec.parse(fileBytes)
            passwordBytes = PasswordEncoding.validateAndEncodeUtf8(password)
            val ciphertextLength = fileBytes.size - EncryptedBackupFormat.HEADER_SIZE

            key = keyDeriver.deriveKey(
                passwordUtf8 = passwordBytes,
                salt = header.salt,
                memoryCostKiB = header.memoryCostKiB,
                iterations = header.iterations,
                parallelism = header.parallelism,
                keyLengthBytes = header.keyLengthBytes
            )
            EncryptedBackupHeaderCodec.requireDerivedKeyLength(key)
            plaintext = AesGcmBackupCipher.decrypt(
                key = key,
                nonce = header.nonce,
                input = fileBytes,
                aadBuffer = fileBytes,
                aadOffset = 0,
                aadLength = EncryptedBackupFormat.HEADER_SIZE,
                ciphertextOffset = EncryptedBackupFormat.HEADER_SIZE,
                ciphertextLength = ciphertextLength
            )
            return decodePayload(plaintext)
        } catch (e: EncryptedBackupPasswordException) {
            throw e
        } catch (e: EncryptedBackupFormatException) {
            throw e
        } catch (e: EncryptedBackupUnsupportedFormatException) {
            throw e
        } catch (e: EncryptedBackupAuthException) {
            throw e
        } finally {
            PasswordEncoding.wipe(passwordBytes)
            PasswordEncoding.wipe(key)
            PasswordEncoding.wipe(plaintext)
        }
    }

    private fun encodePayload(payload: EncryptedBackupPayload): ByteArray {
        return plaintextEncoder?.invoke(payload)
            ?: json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun decodePayload(plaintext: ByteArray): EncryptedBackupPayload {
        return try {
            val payload = ByteArrayInputStream(plaintext).use { stream ->
                json.decodeFromStream(EncryptedBackupPayload.serializer(), stream)
            }
            if (payload.payloadVersion != EncryptedBackupFormat.PAYLOAD_VERSION) {
                throw EncryptedBackupUnsupportedFormatException(
                    "Unsupported encrypted backup payload version"
                )
            }
            payload
        } catch (e: EncryptedBackupUnsupportedFormatException) {
            throw e
        } catch (_: Exception) {
            throw EncryptedBackupAuthException()
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val defaultJson = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
