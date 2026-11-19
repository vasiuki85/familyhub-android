package com.familyhub.data.backup

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportShareFileGuard
import com.familyhub.data.export.ExportTempFileFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EncryptedBackupCodecTest {

    private lateinit var rootDir: File
    private lateinit var backupsDir: File
    private lateinit var cacheDir: File
    private lateinit var fakeDeriver: FakeArgon2idKeyDeriver
    private lateinit var codec: EncryptedBackupCodec
    private lateinit var guard: ExportShareFileGuard
    private val productionHeader: EncryptedBackupHeader
        get() = EncryptedBackupHeaderCodec.createProductionHeader()

    @Before
    fun setUp() {
        rootDir = createTempDirectory(prefix = "fh_fhb_").toFile()
        backupsDir = File(rootDir, "backups").apply { mkdirs() }
        cacheDir = File(rootDir, "cache").apply { mkdirs() }
        fakeDeriver = FakeArgon2idKeyDeriver()
        codec = EncryptedBackupCodec(keyDeriver = fakeDeriver)
        guard = ExportShareFileGuard(cacheDir = cacheDir, backupsDir = backupsDir)
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun roundTrip_preservesCyrillicEmojiAndNullFamilyId() {
        val password = "пароль🔐ok".toCharArray()
        val payload = samplePayload(
            familyId = null,
            displayName = "Имя 👨‍👩‍👧‍👦"
        )
        val file = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")

        codec.encryptToFile(payload, password, file, productionHeader)
        val decrypted = codec.decryptFromFile(file, password)

        assertNull(decrypted.sourceFamilyId)
        assertEquals("Имя 👨‍👩‍👧‍👦", decrypted.data.members.single().displayName)
        assertEquals(payload.createdAt, decrypted.createdAt)
        assertTrue(fakeDeriver.calls >= 2)
    }

    @Test
    fun roundTrip_withSourceFamilyId() {
        val password = "password1".toCharArray()
        val payload = samplePayload(familyId = "family-кириллица-🏠")
        val file = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")
        codec.encryptToFile(payload, password, file, productionHeader)
        assertEquals("family-кириллица-🏠", codec.decryptFromFile(file, password).sourceFamilyId)
    }

    @Test
    fun samePayloadAndPassword_produceDifferentCiphertexts() {
        val password = "same-password".toCharArray()
        val payload = samplePayload()
        val first = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")
        val second = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")

        codec.encryptToFile(payload, password, first, productionHeader)
        codec.encryptToFile(payload, password, second, productionHeader)

        assertNotEquals(first.readBytes().toList(), second.readBytes().toList())
    }

    @Test
    fun wrongPassword_throwsAuthException() {
        val file = encryptSample("correct1".toCharArray())
        assertThrows(EncryptedBackupAuthException::class.java) {
            codec.decryptFromFile(file, "wrongpwd1".toCharArray())
        }
    }

    @Test
    fun tamperHeader_throwsAuthException() {
        val file = encryptSample("secret12".toCharArray())
        val bytes = file.readBytes()
        bytes[EncryptedBackupFormat.HEADER_SIZE - 1] =
            (bytes[EncryptedBackupFormat.HEADER_SIZE - 1].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)

        assertThrows(EncryptedBackupAuthException::class.java) {
            codec.decryptFromFile(file, "secret12".toCharArray())
        }
    }

    @Test
    fun tamperCiphertext_throwsAuthException() {
        val file = encryptSample("secret12".toCharArray())
        val bytes = file.readBytes()
        val index = EncryptedBackupFormat.HEADER_SIZE + 2
        bytes[index] = (bytes[index].toInt() xor 0xFF).toByte()
        file.writeBytes(bytes)

        assertThrows(EncryptedBackupAuthException::class.java) {
            codec.decryptFromFile(file, "secret12".toCharArray())
        }
    }

    @Test
    fun truncatedFile_rejected() {
        val file = encryptSample("secret12".toCharArray())
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(EncryptedBackupFormat.HEADER_SIZE + 4))

        assertThrows(EncryptedBackupFormatException::class.java) {
            codec.decryptFromFile(file, "secret12".toCharArray())
        }
    }

    @Test
    fun oversizeFile_rejectedBeforeDecrypt() {
        val file = File(backupsDir, "familyhub_backup_oversize.fhb")
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(EncryptedBackupFormat.MAX_FILE_BYTES + 1)
        }
        val before = fakeDeriver.calls
        assertThrows(EncryptedBackupFormatException::class.java) {
            codec.decryptFromFile(file, "secret12".toCharArray())
        }
        assertEquals(before, fakeDeriver.calls)
    }

    @Test
    fun unsupportedFormatVersion_rejectedBeforeKdf() {
        val file = encryptSample("secret12".toCharArray())
        val bytes = file.readBytes()
        bytes[8] = 99
        file.writeBytes(bytes)
        val before = fakeDeriver.calls
        assertThrows(EncryptedBackupUnsupportedFormatException::class.java) {
            codec.decryptFromFile(file, "secret12".toCharArray())
        }
        assertEquals(before, fakeDeriver.calls)
    }

    @Test
    fun unknownArgon2Version_rejectedBeforeKdf() {
        val file = encryptSample("secret12".toCharArray())
        val bytes = file.readBytes()
        bytes[11] = 0x10
        file.writeBytes(bytes)
        val before = fakeDeriver.calls
        assertThrows(EncryptedBackupUnsupportedFormatException::class.java) {
            codec.decryptFromFile(file, "secret12".toCharArray())
        }
        assertEquals(before, fakeDeriver.calls)
    }

    @Test
    fun weakMemory_rejectedBeforeKdf() {
        val deriver = FakeArgon2idKeyDeriver()
        val limitedCodec = EncryptedBackupCodec(keyDeriver = deriver)
        val file = File(backupsDir, "familyhub_backup_weak_mem.fhb")
        file.writeBytes(
            buildHeaderBytes(
                memoryCostKiB = EncryptedBackupFormat.MIN_KDF_MEMORY_KIB - 1,
                iterations = EncryptedBackupFormat.PRODUCTION_ITERATIONS,
                parallelism = EncryptedBackupFormat.PRODUCTION_PARALLELISM
            ) + ByteArray(EncryptedBackupFormat.GCM_TAG_BYTES + 8)
        )
        assertThrows(EncryptedBackupFormatException::class.java) {
            limitedCodec.decryptFromFile(file, "secret12".toCharArray())
        }
        assertEquals(0, deriver.calls)
    }

    @Test
    fun weakIterations_rejectedBeforeKdf() {
        val deriver = FakeArgon2idKeyDeriver()
        val limitedCodec = EncryptedBackupCodec(keyDeriver = deriver)
        val file = File(backupsDir, "familyhub_backup_weak_iter.fhb")
        file.writeBytes(
            buildHeaderBytes(
                memoryCostKiB = EncryptedBackupFormat.PRODUCTION_MEMORY_KIB,
                iterations = EncryptedBackupFormat.MIN_KDF_ITERATIONS - 1,
                parallelism = EncryptedBackupFormat.PRODUCTION_PARALLELISM
            ) + ByteArray(EncryptedBackupFormat.GCM_TAG_BYTES + 8)
        )
        assertThrows(EncryptedBackupFormatException::class.java) {
            limitedCodec.decryptFromFile(file, "secret12".toCharArray())
        }
        assertEquals(0, deriver.calls)
    }

    @Test
    fun wrongKeyLengthInHeader_rejectedBeforeKdf() {
        val deriver = FakeArgon2idKeyDeriver()
        val limitedCodec = EncryptedBackupCodec(keyDeriver = deriver)
        val file = File(backupsDir, "familyhub_backup_bad_keylen.fhb")
        file.writeBytes(
            buildHeaderBytes(
                memoryCostKiB = EncryptedBackupFormat.PRODUCTION_MEMORY_KIB,
                iterations = EncryptedBackupFormat.PRODUCTION_ITERATIONS,
                parallelism = EncryptedBackupFormat.PRODUCTION_PARALLELISM,
                keyLengthBytes = 16
            ) + ByteArray(EncryptedBackupFormat.GCM_TAG_BYTES + 8)
        )
        assertThrows(EncryptedBackupFormatException::class.java) {
            limitedCodec.decryptFromFile(file, "secret12".toCharArray())
        }
        assertEquals(0, deriver.calls)
    }

    @Test
    fun wrongDerivedKeyLength_throwsFormatException() {
        val shortKeyDeriver = FakeArgon2idKeyDeriver(keyLengthOverride = 16)
        val shortCodec = EncryptedBackupCodec(keyDeriver = shortKeyDeriver)
        val file = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")
        assertThrows(EncryptedBackupFormatException::class.java) {
            shortCodec.encryptToFile(samplePayload(), "password1".toCharArray(), file, productionHeader)
        }
        assertFalse(file.exists())
        assertEquals(1, shortKeyDeriver.calls)
    }

    @Test
    fun familyId_absentFromPlaintextFileBytes() {
        val familyId = "family-secret-id-xyz"
        val password = "password1".toCharArray()
        val payload = samplePayload(familyId = familyId)
        val file = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")
        codec.encryptToFile(payload, password, file, productionHeader)

        val ascii = file.readBytes().toString(StandardCharsets.ISO_8859_1)
        assertFalse(ascii.contains(familyId))
        assertFalse(ascii.contains(payload.data.members.single().displayName))
    }

    @Test
    fun oversizedPlaintext_rejectedBeforeKdfAndNoPartialFile() {
        val deriver = FakeArgon2idKeyDeriver()
        val oversizedBytes = (EncryptedBackupSizeGuard.maxPlaintextBytes() + 1).toInt()
        val limitedCodec = EncryptedBackupCodec(
            keyDeriver = deriver,
            plaintextEncoder = { ByteArray(oversizedBytes) }
        )
        val file = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")
        assertThrows(EncryptedBackupFormatException::class.java) {
            limitedCodec.encryptToFile(samplePayload(), "password1".toCharArray(), file, productionHeader)
        }
        assertEquals(0, deriver.calls)
        assertFalse(file.exists())
    }

    @Test
    fun emptyPassword_rejectedBeforeKdf() {
        val before = fakeDeriver.calls
        assertThrows(EncryptedBackupPasswordException::class.java) {
            codec.encryptToFile(samplePayload(), CharArray(0), File(backupsDir, "x.fhb"), productionHeader)
        }
        assertEquals(before, fakeDeriver.calls)
    }

    @Test
    fun shortPassword_rejectedBeforeKdf() {
        val before = fakeDeriver.calls
        assertThrows(EncryptedBackupPasswordException::class.java) {
            codec.encryptToFile(samplePayload(), "short".toCharArray(), File(backupsDir, "x.fhb"), productionHeader)
        }
        assertEquals(before, fakeDeriver.calls)
    }

    @Test
    fun longPassword_rejectedBeforeKdf() {
        val before = fakeDeriver.calls
        val tooLong = CharArray(EncryptedBackupFormat.MAX_PASSWORD_CHARS + 1) { 'a' }
        assertThrows(EncryptedBackupPasswordException::class.java) {
            codec.encryptToFile(samplePayload(), tooLong, File(backupsDir, "x.fhb"), productionHeader)
        }
        assertEquals(before, fakeDeriver.calls)
    }

    @Test
    fun malformedSurrogatePassword_rejectedBeforeKdf() {
        val before = fakeDeriver.calls
        val malformed = charArrayOf('a', 'b', 'c', 'd', 'e', 'f', 'g', '\uD800')
        assertThrows(EncryptedBackupPasswordException::class.java) {
            codec.encryptToFile(samplePayload(), malformed, File(backupsDir, "x.fhb"), productionHeader)
        }
        assertEquals(before, fakeDeriver.calls)
    }

    @Test
    fun guard_allowsFhbBackupAndRejectsArbitraryFiles() {
        val fhb = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")
        fhb.writeBytes(ByteArray(8))
        assertEquals(EncryptedBackupFormat.FHB_MIME_TYPE, guard.validateShareable(fhb).mimeType)

        val rogue = File(backupsDir, "not_backup.fhb")
        rogue.writeBytes(ByteArray(8))
        assertThrows(SecurityException::class.java) {
            guard.validateShareable(rogue)
        }
    }

    @Test
    fun sizeGuard_pureHelperBounds() {
        val max = EncryptedBackupSizeGuard.maxPlaintextBytes()
        assertEquals(
            EncryptedBackupFormat.MAX_FILE_BYTES -
                EncryptedBackupFormat.HEADER_SIZE -
                EncryptedBackupFormat.GCM_TAG_BYTES,
            max
        )
        EncryptedBackupSizeGuard.ensurePlaintextFits(max.toInt())
        assertThrows(EncryptedBackupFormatException::class.java) {
            EncryptedBackupSizeGuard.ensurePlaintextFits((max + 1).toInt())
        }
    }

    private fun encryptSample(password: CharArray): File {
        val file = ExportTempFileFactory.createEncryptedBackupFile(backupsDir, "2026-01-01 12:00:00")
        codec.encryptToFile(samplePayload(), password, file, productionHeader)
        return file
    }

    private fun samplePayload(
        familyId: String? = "family-1",
        displayName: String = "Member"
    ): EncryptedBackupPayload {
        return EncryptedBackupPayload(
            payloadVersion = EncryptedBackupFormat.PAYLOAD_VERSION,
            createdAt = 1_700_000_000_000L,
            sourceFamilyId = familyId,
            data = ExportData(
                members = listOf(
                    ExportMember(
                        id = "m1",
                        displayName = displayName,
                        roleLabel = "Parent",
                        avatarColor = "#2196F3",
                        role = "PARENT"
                    )
                ),
                exportDate = "2026-01-01 12:00:00"
            )
        )
    }

    private fun buildHeaderBytes(
        memoryCostKiB: Int,
        iterations: Int,
        parallelism: Int,
        argon2Version: Byte = EncryptedBackupFormat.ARGON2_VERSION_13,
        keyLengthBytes: Int = EncryptedBackupFormat.KEY_BYTES
    ): ByteArray {
        val buffer = ByteBuffer.allocate(EncryptedBackupFormat.HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.put(EncryptedBackupFormat.MAGIC.toByteArray(StandardCharsets.US_ASCII))
        buffer.put(EncryptedBackupFormat.FORMAT_VERSION)
        buffer.put(EncryptedBackupFormat.KDF_ARGON2ID)
        buffer.put(EncryptedBackupFormat.CIPHER_AES_256_GCM)
        buffer.put(argon2Version)
        buffer.putInt(memoryCostKiB)
        buffer.putInt(iterations)
        buffer.putShort(parallelism.toShort())
        buffer.putShort(keyLengthBytes.toShort())
        buffer.put(ByteArray(EncryptedBackupFormat.SALT_BYTES))
        buffer.put(ByteArray(EncryptedBackupFormat.NONCE_BYTES))
        return buffer.array()
    }
}
