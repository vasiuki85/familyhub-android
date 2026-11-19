package com.familyhub.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportTempFileFactory
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production JNI crypto path for .fhb (argon2kt + Android AES-GCM).
 * Mandatory in CI via verify_android_test_results.py.
 */
@RunWith(AndroidJUnit4::class)
class Argon2KtEncryptedBackupInstrumentedTest {

    private lateinit var backupsDir: File
    private lateinit var deriver: Argon2KtKeyDeriver
    private lateinit var codec: EncryptedBackupCodec

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        backupsDir = File(context.filesDir, "backups").apply {
            deleteRecursively()
            mkdirs()
        }
        deriver = Argon2KtKeyDeriver()
        codec = EncryptedBackupCodec(keyDeriver = deriver)
    }

    @After
    fun tearDown() {
        backupsDir.deleteRecursively()
    }

    @Test
    fun argon2Kt_matchesIndependentArgon2idV13KnownVector() {
        // Argon2id v1.3, empty secret/AD. Independent expected digest from Bouncy Castle
        // Argon2BytesGenerator (same PHC parameters as argon2kt).
        val password = "password".toByteArray(StandardCharsets.UTF_8)
        val salt = "somesalt".toByteArray(StandardCharsets.UTF_8)
        val expected = hexToBytes(
            "bb0cc80a3e671149526915418c6eefe761bb19d5d2d567a017703e0cea6ab05c"
        )
        val actual = deriver.deriveKey(
            passwordUtf8 = password,
            salt = salt,
            memoryCostKiB = 32,
            iterations = 3,
            parallelism = 4,
            keyLengthBytes = 32
        )
        assertArrayEquals(expected, actual)
    }

    @Test
    fun fhbRoundTrip_realArgon2Kt_preservesCyrillicAndNullFamilyId() {
        val password = "пароль🔐ok".toCharArray()
        try {
            val payload = EncryptedBackupPayload(
                payloadVersion = EncryptedBackupFormat.PAYLOAD_VERSION,
                createdAt = 1_700_000_000_000L,
                sourceFamilyId = null,
                data = ExportData(
                    members = listOf(
                        ExportMember(
                            id = "m1",
                            displayName = "Имя 👨‍👩‍👧‍👦",
                            roleLabel = "Parent",
                            avatarColor = "#2196F3",
                            role = "PARENT"
                        )
                    ),
                    exportDate = "2026-01-01 12:00:00"
                )
            )
            val file = ExportTempFileFactory.createEncryptedBackupFile(
                backupsDir,
                "2026-01-01 12:00:00"
            )
            codec.encryptToFile(payload, password, file)
            assertTrue(file.length() > EncryptedBackupFormat.HEADER_SIZE.toLong())

            val decrypted = codec.decryptFromFile(file, password)
            assertNull(decrypted.sourceFamilyId)
            assertEquals("Имя 👨‍👩‍👧‍👦", decrypted.data.members.single().displayName)
            assertEquals(payload.createdAt, decrypted.createdAt)
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun wrongPassword_onRealAndroidCryptoPath_throwsAuthException() {
        val password = "correct12".toCharArray()
        val wrong = "wrongpwd1".toCharArray()
        try {
            val file = encryptSample(password)
            assertThrows(EncryptedBackupAuthException::class.java) {
                codec.decryptFromFile(file, wrong)
            }
        } finally {
            password.fill('\u0000')
            wrong.fill('\u0000')
        }
    }

    @Test
    fun tamperedCiphertext_onRealAndroidCryptoPath_throwsAuthException() {
        val password = "secret12".toCharArray()
        try {
            val file = encryptSample(password)
            val bytes = file.readBytes()
            val index = EncryptedBackupFormat.HEADER_SIZE + 2
            bytes[index] = (bytes[index].toInt() xor 0xFF).toByte()
            file.writeBytes(bytes)
            assertThrows(EncryptedBackupAuthException::class.java) {
                codec.decryptFromFile(file, password)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun encryptSample(password: CharArray): File {
        val payload = EncryptedBackupPayload(
            payloadVersion = EncryptedBackupFormat.PAYLOAD_VERSION,
            createdAt = 1_700_000_000_000L,
            sourceFamilyId = "family-instrumented",
            data = ExportData(
                members = listOf(
                    ExportMember(
                        id = "m1",
                        displayName = "Member",
                        roleLabel = "Parent",
                        avatarColor = "#2196F3",
                        role = "PARENT"
                    )
                ),
                exportDate = "2026-01-01 12:00:00"
            )
        )
        val file = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")
        codec.encryptToFile(payload, password, file)
        return file
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
