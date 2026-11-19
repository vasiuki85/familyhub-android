package com.familyhub.data.backup

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportTempFileFactory
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class EncryptedBackupEncryptHeaderValidationTest(
    private val caseName: String,
    private val mutate: (EncryptedBackupHeader) -> EncryptedBackupHeader,
    private val expectedException: Class<out Exception>
) {

    private lateinit var rootDir: File
    private lateinit var backupsDir: File
    private lateinit var deriver: FakeArgon2idKeyDeriver
    private lateinit var codec: EncryptedBackupCodec

    @Before
    fun setUp() {
        rootDir = createTempDirectory(prefix = "fh_fhb_hdr_").toFile()
        backupsDir = File(rootDir, "backups").apply { mkdirs() }
        deriver = FakeArgon2idKeyDeriver()
        codec = EncryptedBackupCodec(keyDeriver = deriver)
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun invalidHeader_rejectedBeforeKdf_withoutPartialFile() {
        val file = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")
        val invalid = mutate(validProductionHeader())

        assertThrows(expectedException) {
            codec.encryptToFile(samplePayload(), "password1".toCharArray(), file, invalid)
        }

        assertEquals("$caseName must not call KDF", 0, deriver.calls)
        assertFalse("$caseName must not leave a partial file", file.exists())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any>> = listOf(
            arrayOf(
                "formatVersion",
                { h: EncryptedBackupHeader -> h.copy(formatVersion = 99) },
                EncryptedBackupUnsupportedFormatException::class.java
            ),
            arrayOf(
                "kdfId",
                { h: EncryptedBackupHeader -> h.copy(kdfId = 9) },
                EncryptedBackupUnsupportedFormatException::class.java
            ),
            arrayOf(
                "cipherId",
                { h: EncryptedBackupHeader -> h.copy(cipherId = 9) },
                EncryptedBackupUnsupportedFormatException::class.java
            ),
            arrayOf(
                "argon2Version",
                { h: EncryptedBackupHeader -> h.copy(argon2Version = 0x10) },
                EncryptedBackupUnsupportedFormatException::class.java
            ),
            arrayOf(
                "keyLengthBytes",
                { h: EncryptedBackupHeader -> h.copy(keyLengthBytes = 16) },
                EncryptedBackupFormatException::class.java
            ),
            arrayOf(
                "saltLength",
                { h: EncryptedBackupHeader -> h.copy(salt = ByteArray(8)) },
                EncryptedBackupFormatException::class.java
            ),
            arrayOf(
                "nonceLength",
                { h: EncryptedBackupHeader -> h.copy(nonce = ByteArray(8)) },
                EncryptedBackupFormatException::class.java
            ),
            arrayOf(
                "weakMemory",
                { h: EncryptedBackupHeader ->
                    h.copy(memoryCostKiB = EncryptedBackupFormat.MIN_KDF_MEMORY_KIB - 1)
                },
                EncryptedBackupFormatException::class.java
            ),
            arrayOf(
                "weakIterations",
                { h: EncryptedBackupHeader ->
                    h.copy(iterations = EncryptedBackupFormat.MIN_KDF_ITERATIONS - 1)
                },
                EncryptedBackupFormatException::class.java
            )
        )

        private fun validProductionHeader(): EncryptedBackupHeader =
            EncryptedBackupHeaderCodec.createProductionHeader()

        private fun samplePayload(): EncryptedBackupPayload =
            EncryptedBackupPayload(
                payloadVersion = EncryptedBackupFormat.PAYLOAD_VERSION,
                createdAt = 1_700_000_000_000L,
                sourceFamilyId = null,
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
    }
}

class EncryptedBackupHeaderToByteArrayFailClosedTest {

    @Test
    fun toByteArray_rejectsInvalidSaltLength() {
        val header = EncryptedBackupHeaderCodec.createProductionHeader()
            .copy(salt = ByteArray(8))
        assertThrows(EncryptedBackupFormatException::class.java) {
            header.toByteArray()
        }
    }

    @Test
    fun toByteArray_rejectsInvalidNonceLength() {
        val header = EncryptedBackupHeaderCodec.createProductionHeader()
            .copy(nonce = ByteArray(8))
        assertThrows(EncryptedBackupFormatException::class.java) {
            header.toByteArray()
        }
    }

    @Test
    fun toByteArray_rejectsInvalidKeyLength() {
        val header = EncryptedBackupHeaderCodec.createProductionHeader()
            .copy(keyLengthBytes = 16)
        assertThrows(EncryptedBackupFormatException::class.java) {
            header.toByteArray()
        }
    }
}
