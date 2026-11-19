package com.familyhub.data.backup

import com.lambdapioneer.argon2kt.Argon2KtResult
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Argon2KtResultExtractorTest {

    @Test
    fun copyRawHashAndWipe_returnsCopyAndZeroesBothDirectBuffers() {
        val expectedKey = ByteArray(32) { index -> (index + 1).toByte() }
        val encodedBytes = ByteArray(48) { index -> (0xA0 + index).toByte() }
        val rawHash = directBufferOf(expectedKey)
        val encodedOutput = directBufferOf(encodedBytes)
        val result = Argon2KtResult(rawHash, encodedOutput)

        val copied = Argon2KtResultExtractor.copyRawHashAndWipe(result)

        assertArrayEquals(expectedKey, copied)
        assertBufferFullyZero(rawHash)
        assertBufferFullyZero(encodedOutput)
        assertTrue(rawHash.isDirect)
        assertTrue(encodedOutput.isDirect)
    }

    @Test
    fun extractAndWipe_zeroesFullCapacityWhenExtractThrowsRegardlessOfPositionLimit() {
        val rawHash = directBufferOf(ByteArray(32) { 0x11 })
        val encodedOutput = directBufferOf(ByteArray(24) { 0x22 })
        // Wipe must clear full capacity even when position/limit are not at extremes.
        rawHash.position(8)
        rawHash.limit(16)
        encodedOutput.position(4)
        encodedOutput.limit(12)
        val result = Argon2KtResult(rawHash, encodedOutput)

        val thrown = assertThrows(IllegalStateException::class.java) {
            Argon2KtResultExtractor.extractAndWipe(result) {
                throw IllegalStateException("copy failed")
            }
        }
        assertEquals("copy failed", thrown.message)
        assertBufferFullyZero(rawHash)
        assertBufferFullyZero(encodedOutput)
    }

    private fun directBufferOf(bytes: ByteArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes)
        buffer.flip()
        return buffer
    }

    private fun assertBufferFullyZero(buffer: ByteBuffer) {
        val view = buffer.duplicate()
        view.clear()
        while (view.hasRemaining()) {
            assertEquals(0, view.get().toInt())
        }
    }
}
