package com.familyhub.data.backup

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Password encoding and wipe helpers.
 *
 * Ownership: callers that receive a password [CharArray] from the UI/API **must** clear that
 * array in a `finally` block after [EncryptedBackupCodec] returns. This type clears only the
 * temporary UTF-8 bytes / encoder buffers it allocates.
 */
internal object PasswordEncoding {
    fun validateAndEncodeUtf8(password: CharArray): ByteArray {
        validatePassword(password)
        var encoded: ByteBuffer? = null
        return try {
            val encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            encoded = encoder.encode(CharBuffer.wrap(password))
            val bytes = ByteArray(encoded.remaining())
            encoded.get(bytes)
            if (bytes.size > EncryptedBackupFormat.MAX_PASSWORD_UTF8_BYTES) {
                wipe(bytes)
                throw EncryptedBackupPasswordException("Password UTF-8 encoding exceeds maximum size")
            }
            bytes
        } catch (e: EncryptedBackupPasswordException) {
            throw e
        } catch (_: Exception) {
            throw EncryptedBackupPasswordException("Password is not valid UTF-16 for UTF-8 encoding")
        } finally {
            wipe(encoded)
        }
    }

    fun validatePassword(password: CharArray) {
        val length = password.size
        if (length < EncryptedBackupFormat.MIN_PASSWORD_CHARS) {
            throw EncryptedBackupPasswordException("Password is too short")
        }
        if (length > EncryptedBackupFormat.MAX_PASSWORD_CHARS) {
            throw EncryptedBackupPasswordException("Password is too long")
        }
        var i = 0
        while (i < length) {
            val ch = password[i]
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= length || !Character.isLowSurrogate(password[i + 1])) {
                    throw EncryptedBackupPasswordException("Password contains a malformed UTF-16 surrogate")
                }
                i += 2
            } else if (Character.isLowSurrogate(ch)) {
                throw EncryptedBackupPasswordException("Password contains a malformed UTF-16 surrogate")
            } else {
                i += 1
            }
        }
    }

    fun wipe(bytes: ByteArray?) {
        if (bytes != null) {
            Arrays.fill(bytes, 0)
        }
    }

    fun wipe(chars: CharArray?) {
        if (chars != null) {
            Arrays.fill(chars, '\u0000')
        }
    }

    fun wipe(buffer: ByteBuffer?) {
        if (buffer == null) {
            return
        }
        if (buffer.hasArray()) {
            val array = buffer.array()
            val offset = buffer.arrayOffset()
            Arrays.fill(array, offset, offset + buffer.capacity(), 0)
        } else {
            val duplicate = buffer.duplicate()
            duplicate.clear()
            while (duplicate.hasRemaining()) {
                duplicate.put(0)
            }
        }
    }
}

internal fun ByteBuffer.putUtf8Magic(magic: String) {
    val bytes = magic.toByteArray(StandardCharsets.US_ASCII)
    require(bytes.size == EncryptedBackupFormat.MAGIC.length)
    put(bytes)
}
