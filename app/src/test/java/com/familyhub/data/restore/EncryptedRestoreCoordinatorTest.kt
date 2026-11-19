package com.familyhub.data.restore

import android.content.Context
import com.familyhub.data.backup.EncryptedBackupAuthException
import com.familyhub.data.backup.EncryptedBackupCodec
import com.familyhub.data.backup.EncryptedBackupFormat
import com.familyhub.data.backup.EncryptedBackupPayload
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportTask
import com.familyhub.data.importer.DataImportService
import com.familyhub.data.sync.CloudSyncScheduler
import com.familyhub.data.sync.SyncExclusiveLock
import com.familyhub.data.datastore.SyncSettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.File
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EncryptedRestoreCoordinatorTest {

    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var backupFile: File
    private val codec = mockk<EncryptedBackupCodec>()
    private val importService = mockk<DataImportService>()
    private val syncSettings = mockk<SyncSettingsDataStore>()
    private val scheduler = mockk<CloudSyncScheduler>(relaxed = true)
    private val lock = SyncExclusiveLock()
    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "restore-io-test")
    }
    private val ioDispatcher = ioExecutor.asCoroutineDispatcher()
    private lateinit var coordinator: EncryptedRestoreCoordinator

    @Before
    fun setUp() {
        root = createTempDirectory(prefix = "fh_restore_").toFile()
        filesDir = File(root, "files").apply { mkdirs() }
        cacheDir = File(root, "cache").apply { mkdirs() }
        val backups = File(filesDir, "backups").apply { mkdirs() }
        backupFile = File(backups, "familyhub_backup_test.fhb").apply {
            writeBytes(ByteArray(80) { 1 })
        }
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        coEvery { syncSettings.beginRestoreBarrier(any(), any()) } just runs
        coEvery { syncSettings.finalizeRestoreBinding(any(), any(), any()) } just runs
        coEvery { importService.applyValidatedRestoreSnapshot(any()) } just runs
        coordinator = EncryptedRestoreCoordinator(
            context = context,
            codec = codec,
            importService = importService,
            syncSettingsDataStore = syncSettings,
            cloudSyncScheduler = scheduler,
            syncExclusiveLock = lock,
            ioDispatcher = ioDispatcher
        )
    }

    @After
    fun tearDown() {
        ioDispatcher.close()
        ioExecutor.shutdownNow()
        root.deleteRecursively()
    }

    @Test
    fun inspect_returnsFingerprintOfSameBytesAndClearsPassword() = runBlocking {
        val password = "password1".toCharArray()
        val expectedFingerprint = fingerprintOf(backupFile)
        every { codec.decryptFromBytes(any(), any()) } returns validPayload()

        val preview = coordinator.inspect(backupFile, password)

        assertEquals(expectedFingerprint, preview.fingerprintSha256)
        assertEquals(validPayload().createdAt, preview.createdAt)
        assertNull(preview.sourceFamilyId)
        assertEquals(1, preview.counts.members)
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 0) { importService.applyValidatedRestoreSnapshot(any()) }
        coVerify(exactly = 0) { syncSettings.beginRestoreBarrier(any(), any()) }
        verify(exactly = 1) {
            codec.decryptFromBytes(match { it.contentEquals(backupFile.readBytes()) }, any())
        }
    }

    @Test
    fun inspect_codecRunsOnIoDispatcherNotCallerThread() = runBlocking {
        val callerThread = Thread.currentThread()
        var codecThread: Thread? = null
        every { codec.decryptFromBytes(any(), any()) } answers {
            codecThread = Thread.currentThread()
            validPayload()
        }
        val password = "password1".toCharArray()

        coordinator.inspect(backupFile, password)

        assertEquals("restore-io-test", codecThread?.name)
        assertNotEquals(callerThread, codecThread)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun restore_codecRunsOnIoDispatcherNotCallerThread() = runBlocking {
        val callerThread = Thread.currentThread()
        var codecThread: Thread? = null
        val fingerprint = fingerprintOf(backupFile)
        every { codec.decryptFromBytes(any(), any()) } answers {
            codecThread = Thread.currentThread()
            validPayload()
        }
        val password = "password1".toCharArray()

        coordinator.restore(backupFile, password, fingerprint)

        assertEquals("restore-io-test", codecThread?.name)
        assertNotEquals(callerThread, codecThread)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun inspect_wrongPassword_clearsPasswordAndDoesNotMutate() {
        val password = "password1".toCharArray()
        every { codec.decryptFromBytes(any(), any()) } throws EncryptedBackupAuthException()

        assertThrows(EncryptedBackupAuthException::class.java) {
            runBlocking { coordinator.inspect(backupFile, password) }
        }
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 0) { importService.applyValidatedRestoreSnapshot(any()) }
    }

    @Test
    fun restore_fingerprintMismatch_doesNotDecryptOrMutate() {
        val password = "password1".toCharArray()
        assertThrows(RestoreFingerprintMismatchException::class.java) {
            runBlocking {
                coordinator.restore(backupFile, password, expectedFingerprint = "deadbeef")
            }
        }
        assertTrue(password.all { it == '\u0000' })
        verify(exactly = 0) { codec.decryptFromBytes(any(), any()) }
        coVerify(exactly = 0) { importService.applyValidatedRestoreSnapshot(any()) }
        coVerify(exactly = 0) { syncSettings.beginRestoreBarrier(any(), any()) }
    }

    @Test
    fun restore_differentBarrierFingerprint_refusesWithoutMutation() {
        val password = "password1".toCharArray()
        val fingerprint = fingerprintOf(backupFile)
        every { codec.decryptFromBytes(any(), any()) } returns validPayload()
        coEvery {
            syncSettings.beginRestoreBarrier(fingerprint, null)
        } throws RestoreBarrierConflictException()

        assertThrows(RestoreBarrierConflictException::class.java) {
            runBlocking { coordinator.restore(backupFile, password, fingerprint) }
        }
        coVerify(exactly = 0) { importService.applyValidatedRestoreSnapshot(any()) }
        coVerify(exactly = 0) { syncSettings.finalizeRestoreBinding(any(), any(), any()) }
    }

    @Test
    fun restore_success_usesValidatedSnapshotApiAndFinalizes() = runBlocking {
        val fingerprint = fingerprintOf(backupFile)
        every { codec.decryptFromBytes(any(), any()) } returns validPayload(sourceFamilyId = "family-a")
        val password = "password1".toCharArray()

        val result = coordinator.restore(
            backupFile,
            password,
            fingerprint,
            authenticatedUserId = "missing-uid"
        ) as RestoreResult.Success

        assertEquals("family-a", result.sourceFamilyId)
        assertEquals(fingerprint, result.fingerprintSha256)
        assertTrue(result.needsLocalProfile)
        coVerifyOrder {
            syncSettings.beginRestoreBarrier(fingerprint, "family-a")
            importService.applyValidatedRestoreSnapshot(any())
            syncSettings.finalizeRestoreBinding(fingerprint, "family-a", true)
        }
        verify(exactly = 1) { scheduler.cancelPeriodicSync() }
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun restore_success_uidPresent_finalizesWithoutSuppression() = runBlocking {
        val fingerprint = fingerprintOf(backupFile)
        every { codec.decryptFromBytes(any(), any()) } returns validPayload()
        val password = "password1".toCharArray()

        val result = coordinator.restore(
            backupFile,
            password,
            fingerprint,
            authenticatedUserId = "m1"
        ) as RestoreResult.Success

        assertTrue(!result.needsLocalProfile)
        coVerify(exactly = 1) {
            syncSettings.finalizeRestoreBinding(fingerprint, null, false)
        }
    }

    @Test
    fun restore_roomFailure_keepsBarrierAndDoesNotFinalize() {
        val fingerprint = fingerprintOf(backupFile)
        every { codec.decryptFromBytes(any(), any()) } returns validPayload()
        coEvery {
            importService.applyValidatedRestoreSnapshot(any())
        } throws IllegalStateException("dao boom")
        val password = "password1".toCharArray()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.restore(backupFile, password, fingerprint) }
        }
        coVerify(exactly = 1) { syncSettings.beginRestoreBarrier(fingerprint, null) }
        coVerify(exactly = 0) { syncSettings.finalizeRestoreBinding(any(), any(), any()) }
    }

    @Test
    fun restore_finalizationFailure_propagatesAfterRoomCommit() {
        val fingerprint = fingerprintOf(backupFile)
        every { codec.decryptFromBytes(any(), any()) } returns validPayload()
        coEvery {
            syncSettings.finalizeRestoreBinding(fingerprint, null, any())
        } throws IllegalStateException("prefs boom")
        val password = "password1".toCharArray()

        assertThrows(RestoreFinalizationException::class.java) {
            runBlocking { coordinator.restore(backupFile, password, fingerprint) }
        }
        coVerify(exactly = 1) { importService.applyValidatedRestoreSnapshot(any()) }
    }

    private fun fingerprintOf(file: File): String =
        RestoreFileAccess.sha256Hex(file.readBytes())

    private fun validPayload(sourceFamilyId: String? = null): EncryptedBackupPayload =
        EncryptedBackupPayload(
            payloadVersion = EncryptedBackupFormat.PAYLOAD_VERSION,
            createdAt = 1_700_000_000_000L,
            sourceFamilyId = sourceFamilyId,
            data = ExportData(
                members = listOf(
                    ExportMember(
                        id = "m1",
                        displayName = "Admin",
                        roleLabel = "Admin",
                        avatarColor = "#2196F3",
                        role = "ADMIN"
                    )
                ),
                tasks = listOf(
                    ExportTask(
                        id = "t1",
                        title = "Task",
                        createdByUserId = "m1",
                        assignedToUserId = "m1",
                        repeatRule = "NONE",
                        status = "TODO",
                        createdAt = 1L,
                        taskType = "ROUTINE",
                        priority = "MEDIUM"
                    )
                ),
                exportDate = "2026-01-01 12:00:00"
            )
        )
}
