package com.familyhub.ui.screens.settings.encrypted

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import com.familyhub.data.backup.EncryptedBackupAuthException
import com.familyhub.data.backup.EncryptedBackupWriter
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.export.DataExportService
import com.familyhub.data.importer.DataImportService
import com.familyhub.data.export.ExportData
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.restore.EncryptedRestoreCoordinator
import com.familyhub.data.restore.RestoreCollectionCounts
import com.familyhub.data.restore.RestoreFingerprintMismatchException
import com.familyhub.data.restore.RestorePreview
import com.familyhub.data.restore.RestoreResult
import com.familyhub.data.restore.RestoreStagingStore
import com.familyhub.data.sync.SyncChangeTracker
import com.familyhub.data.sync.SyncExclusiveLock
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.SyncSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedBackupRestoreViewModelTest {

    private lateinit var root: File
    private val writer = mockk<EncryptedBackupWriter>()
    private val coordinator = mockk<EncryptedRestoreCoordinator>()
    private val stagingStore = mockk<RestoreStagingStore>(relaxed = true)
    private val exportService = mockk<DataExportService>()
    private val syncSettings = mockk<SyncSettingsDataStore>(relaxed = true)
    private val appSettings = mockk<AppSettingsDataStore>(relaxed = true)
    private val members = mockk<FamilyMemberRepository>(relaxed = true)
    private val tracker = mockk<SyncChangeTracker>()
    private val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: EncryptedBackupRestoreViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        root = createTempDirectory(prefix = "fh_enc_ui_").toFile()
        every { appSettings.settingsFlow } returns flowOf(AppSettings(currentUserId = "u1"))
        every { syncSettings.settingsFlow } returns flowOf(SyncSettings())
        every { firebaseAuth.currentUser } returns null
        every { stagingStore.validateStagedPath(any()) } returns null
        every { members.getAllMembers() } returns flowOf(emptyList())
        coEvery { syncSettings.setSuppressAuthMemberBootstrap(any()) } just runs
        coEvery { tracker.runInTransactionWithoutTracking(any()) } coAnswers {
            (invocation.args[0] as suspend () -> Unit).invoke()
        }
        coEvery { tracker.runWithoutTracking(any()) } coAnswers {
            (invocation.args[0] as suspend () -> Unit).invoke()
        }
        coEvery { tracker.markSynchronized() } just runs
        viewModel = createViewModel(SavedStateHandle())
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    @Test
    fun cancelCreateDialog_doesNotWrite() {
        viewModel.onCreateBackupClicked()
        viewModel.dismissCreatePasswordDialog()
        verify(exactly = 0) { writer.write(any(), any(), any(), any()) }
        coVerify(exactly = 0) { coordinator.inspect(any(), any(), any()) }
        coVerify(exactly = 0) { coordinator.restore(any(), any(), any(), any()) }
    }

    @Test
    fun createPasswordMismatch_staysOnSingleCreatePasswordPhase() {
        viewModel.onCreateBackupClicked()
        viewModel.confirmCreateBackup("password1".toCharArray(), "password2".toCharArray())
        val phase = viewModel.state.value.phase as EncryptedBackupRestorePhase.CreatePassword
        assertEquals(EncryptedBackupRestoreError.PASSWORD_MISMATCH, phase.error)
        assertTrue(viewModel.state.value.phase !is EncryptedBackupRestorePhase.FatalError)
    }

    @Test
    fun doubleCreateConfirm_writesOnceAndEmitsOneShare() = runTest(testDispatcher) {
        val writes = AtomicInteger(0)
        val shareIntent = Intent(Intent.ACTION_SEND)
        val backupFile = File(root, "familyhub_backup_x.fhb").apply { writeBytes(ByteArray(80)) }
        coEvery { exportService.getExportData(any()) } returns ExportData(exportDate = "2026-01-01")
        every {
            writer.write(any(), any(), any(), any())
        } answers {
            writes.incrementAndGet()
            backupFile
        }
        every { exportService.shareFile(backupFile) } returns shareIntent

        val eventDeferred = async { viewModel.events.first() }
        val password = "password1".toCharArray()
        val confirm = "password1".toCharArray()
        viewModel.confirmCreateBackup(password.copyOf(), confirm.copyOf())
        viewModel.confirmCreateBackup(password, confirm)
        advanceUntilIdle()

        val event = eventDeferred.await()
        assertTrue(event is EncryptedBackupRestoreUiEvent.ShareBackup)
        assertEquals(1, writes.get())
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun processDeath_onlyRestoresStagingPathNotPreviewPermission() = runTest(testDispatcher) {
        val staged = File(root, "familyhub_restore_abc.fhb").apply { writeBytes(ByteArray(80)) }
        every { stagingStore.validateStagedPath(staged.absolutePath) } returns staged
        val vm = createViewModel(
            SavedStateHandle(
                mapOf(EncryptedBackupRestoreViewModel.KEY_STAGING_PATH to staged.absolutePath)
            )
        )
        advanceUntilIdle()
        val phase = vm.state.value.phase
        assertTrue(phase is EncryptedBackupRestorePhase.AwaitingInspectPassword)
        assertTrue(vm.state.value.phase !is EncryptedBackupRestorePhase.Preview)
        assertTrue(vm.state.value.phase !is EncryptedBackupRestorePhase.ConfirmRestore)
    }

    @Test
    fun restoreWrongPassword_keepsConfirmRestoreDialog() = runTest(testDispatcher) {
        val staged = File(root, "familyhub_restore_x.fhb").apply { writeBytes(ByteArray(80)) }
        reachPreview(staged, fingerprint = "aaa", containsAuthUser = true)
        viewModel.onRestoreClicked()
        coEvery {
            coordinator.restore(staged, any(), "aaa", any())
        } throws EncryptedBackupAuthException()

        val password = "password1".toCharArray()
        viewModel.confirmRestore(password, destructiveConfirmed = true, uidAbsenceConfirmed = true)
        advanceUntilIdle()

        val phase = viewModel.state.value.phase as EncryptedBackupRestorePhase.ConfirmRestore
        assertEquals(EncryptedBackupRestoreError.WRONG_PASSWORD, phase.error)
        assertEquals("aaa", phase.preview.fingerprintSha256)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun fingerprintMismatch_requiresReinspect_notOldPreview() = runTest(testDispatcher) {
        val staged = File(root, "familyhub_restore_x.fhb").apply { writeBytes(ByteArray(80)) }
        reachPreview(staged, fingerprint = "aaa", containsAuthUser = true)
        viewModel.onRestoreClicked()
        coEvery {
            coordinator.restore(staged, any(), "aaa", any())
        } throws RestoreFingerprintMismatchException()

        val password = "password1".toCharArray()
        viewModel.confirmRestore(password, destructiveConfirmed = true, uidAbsenceConfirmed = true)
        advanceUntilIdle()

        val phase = viewModel.state.value.phase as EncryptedBackupRestorePhase.AwaitingInspectPassword
        assertEquals(EncryptedBackupRestoreError.FILE_CHANGED, phase.error)
        assertEquals(staged.absolutePath, phase.stagingPath)
        assertTrue(viewModel.state.value.phase !is EncryptedBackupRestorePhase.Preview)
        assertTrue(viewModel.state.value.phase !is EncryptedBackupRestorePhase.ConfirmRestore)
        assertTrue(password.all { it == '\u0000' })
        verify(exactly = 0) { stagingStore.deleteStagedPath(any()) }
    }

    @Test
    fun success_deletesStagingAndDoesNotCallSetSuppressFromViewModel() = runTest(testDispatcher) {
        val staged = File(root, "familyhub_restore_ok.fhb").apply { writeBytes(ByteArray(80)) }
        val authUid = "uid-missing"
        every { firebaseAuth.currentUser } returns mockk<FirebaseUser> { every { uid } returns authUid }
        reachPreview(staged, fingerprint = "bbb", containsAuthUser = false, authUid = authUid)
        viewModel.onRestoreClicked()
        coEvery {
            coordinator.restore(staged, any(), "bbb", authUid)
        } returns RestoreResult.Success(
            fingerprintSha256 = "bbb",
            sourceFamilyId = null,
            counts = emptyCounts(),
            needsLocalProfile = true
        )

        val password = "password1".toCharArray()
        viewModel.confirmRestore(password, destructiveConfirmed = true, uidAbsenceConfirmed = true)
        advanceUntilIdle()

        val success = viewModel.state.value.phase as EncryptedBackupRestorePhase.Success
        assertTrue(success.needsLocalProfile)
        verify(exactly = 1) { stagingStore.deleteStagedPath(staged.absolutePath) }
        coVerify(exactly = 0) { syncSettings.setSuppressAuthMemberBootstrap(true) }
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun createCancellation_wipesPasswordAndReleasesGuard() = runTest(testDispatcher) {
        coEvery { exportService.getExportData(any()) } coAnswers {
            throw CancellationException("cancel")
        }
        val password = "password1".toCharArray()
        val confirm = "password1".toCharArray()
        viewModel.confirmCreateBackup(password, confirm)
        advanceUntilIdle()
        assertTrue(password.all { it == '\u0000' })

        every {
            writer.write(any(), any(), any(), any())
        } returns File(root, "b.fhb").apply { writeBytes(ByteArray(8)) }
        coEvery { exportService.getExportData(any()) } returns ExportData(exportDate = "2026-01-01")
        every { exportService.shareFile(any()) } returns Intent(Intent.ACTION_SEND)
        viewModel.confirmCreateBackup("password1".toCharArray(), "password1".toCharArray())
        advanceUntilIdle()
        verify(exactly = 1) { writer.write(any(), any(), any(), any()) }
    }

    @Test
    fun authSessionChanged_blocksRestoreWithoutCoordinatorCall() = runTest(testDispatcher) {
        val staged = File(root, "familyhub_restore_auth.fhb").apply { writeBytes(ByteArray(80)) }
        every { firebaseAuth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "user-a" }
        reachPreview(staged, fingerprint = "fp", containsAuthUser = true, authUid = "user-a")
        every { firebaseAuth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "user-b" }
        viewModel.onRestoreClicked()
        viewModel.confirmRestore(
            "password1".toCharArray(),
            destructiveConfirmed = true,
            uidAbsenceConfirmed = true
        )
        advanceUntilIdle()

        val phase = viewModel.state.value.phase as EncryptedBackupRestorePhase.AwaitingInspectPassword
        assertEquals(EncryptedBackupRestoreError.AUTH_SESSION_CHANGED, phase.error)
        coVerify(exactly = 0) { coordinator.restore(any(), any(), any(), any()) }
    }

    @Test
    fun acknowledgeError_clearsStagingPath() = runTest(testDispatcher) {
        val staged = File(root, "familyhub_restore_fatal.fhb").apply { writeBytes(ByteArray(80)) }
        every { stagingStore.validateStagedPath(staged.absolutePath) } returns staged
        viewModel = createViewModel(
            SavedStateHandle(mapOf(EncryptedBackupRestoreViewModel.KEY_STAGING_PATH to staged.absolutePath))
        )
        advanceUntilIdle()
        coEvery { coordinator.inspect(any(), any(), any()) } throws IllegalStateException("boom")
        viewModel.confirmInspectPassword("password1".toCharArray())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.phase is EncryptedBackupRestorePhase.FatalError)
        viewModel.acknowledgeError()
        advanceUntilIdle()
        verify { stagingStore.deleteStagedPath(staged.absolutePath) }
        assertTrue(viewModel.state.value.phase is EncryptedBackupRestorePhase.Idle)
    }

    @Test
    fun doubleAddLocalProfile_upsertsOnce() = runTest(testDispatcher) {
        val authUser = mockk<FirebaseUser>()
        every { authUser.uid } returns "uid-1"
        every { authUser.displayName } returns "User"
        every { authUser.email } returns null
        every { firebaseAuth.currentUser } returns authUser
        every { context.getString(any()) } returns "User"
        coEvery { members.upsertMember(any()) } just runs
        coEvery { appSettings.setCurrentUser(any()) } just runs
        coEvery { tracker.runInTransactionWithoutTracking(any()) } coAnswers {
            (invocation.args[0] as suspend () -> Unit).invoke()
        }
        coEvery { tracker.markSynchronized() } just runs

        val staged = File(root, "familyhub_restore_profile.fhb").apply { writeBytes(ByteArray(80)) }
        reachPreview(staged, fingerprint = "ccc", containsAuthUser = false, authUid = "uid-1")
        coEvery {
            coordinator.restore(staged, any(), "ccc", "uid-1")
        } returns RestoreResult.Success(
            fingerprintSha256 = "ccc",
            sourceFamilyId = null,
            counts = emptyCounts(),
            needsLocalProfile = true
        )
        viewModel.onRestoreClicked()
        viewModel.confirmRestore(
            "password1".toCharArray(),
            destructiveConfirmed = true,
            uidAbsenceConfirmed = true
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.phase is EncryptedBackupRestorePhase.Success)

        every { firebaseAuth.currentUser } returns authUser
        viewModel.addLocalProfile()
        viewModel.addLocalProfile()
        advanceUntilIdle()

        coVerify(exactly = 0) { tracker.markSynchronized() }
        coVerify(exactly = 1) { tracker.runInTransactionWithoutTracking(any()) }
        coVerify(exactly = 1) { members.upsertMember(any()) }
        coVerify(exactly = 1) { syncSettings.setSuppressAuthMemberBootstrap(false) }
        coVerify(exactly = 1) { appSettings.setCurrentUser(any()) }
    }

    @Test
    fun delayedHydration_staysHydratingUntilValidationCompletes() = runTest(testDispatcher) {
        val staged = File(root, "familyhub_restore_delay.fhb").apply { writeBytes(ByteArray(80)) }
        coEvery { stagingStore.validateStagedPath(staged.absolutePath) } coAnswers {
            delay(1_000)
            staged
        }
        val vm = createViewModel(
            SavedStateHandle(
                mapOf(EncryptedBackupRestoreViewModel.KEY_STAGING_PATH to staged.absolutePath)
            )
        )
        assertTrue(vm.state.value.phase is EncryptedBackupRestorePhase.Hydrating)
        assertFalse(vm.state.value.allowsPrimaryActions)
        vm.onCreateBackupClicked()
        verify(exactly = 0) { writer.write(any(), any(), any(), any()) }
        testDispatcher.scheduler.advanceTimeBy(1_000)
        advanceUntilIdle()
        assertTrue(vm.state.value.phase is EncryptedBackupRestorePhase.AwaitingInspectPassword)
    }

    @Test
    fun persistentRecovery_availableAfterViewModelRecreation() = runTest(testDispatcher) {
        every { syncSettings.settingsFlow } returns flowOf(
            SyncSettings(suppressAuthMemberBootstrap = true)
        )
        every { firebaseAuth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "uid-1" }
        every { members.getAllMembers() } returns flowOf(emptyList())
        val first = createViewModel(SavedStateHandle())
        advanceUntilIdle()
        assertTrue(first.state.value.needsLocalProfileRecovery)
        val recreated = createViewModel(SavedStateHandle())
        advanceUntilIdle()
        assertTrue(recreated.state.value.needsLocalProfileRecovery)
    }

    private suspend fun TestScope.reachPreview(
        staged: File,
        fingerprint: String,
        containsAuthUser: Boolean,
        authUid: String = "uid-1"
    ) {
        every { stagingStore.validateStagedPath(staged.absolutePath) } returns staged
        coEvery {
            coordinator.inspect(any(), any(), any())
        } returns RestorePreview(
            fingerprintSha256 = fingerprint,
            createdAt = 1L,
            sourceFamilyId = null,
            payloadVersion = 1,
            counts = emptyCounts(),
            containsAuthenticatedUser = containsAuthUser
        )
        viewModel = createViewModel(
            SavedStateHandle(
                mapOf(EncryptedBackupRestoreViewModel.KEY_STAGING_PATH to staged.absolutePath)
            )
        )
        advanceUntilIdle()
        every { firebaseAuth.currentUser } returns mockk<FirebaseUser>(relaxed = true) {
            every { uid } returns authUid
            every { displayName } returns "User"
            every { email } returns null
        }
        viewModel.confirmInspectPassword("password1".toCharArray())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.phase is EncryptedBackupRestorePhase.Preview)
    }

    private fun createViewModel(handle: SavedStateHandle): EncryptedBackupRestoreViewModel =
        EncryptedBackupRestoreViewModel(
            savedStateHandle = handle,
            encryptedBackupWriter = writer,
            restoreCoordinator = coordinator,
            stagingStore = stagingStore,
            exportService = exportService,
            syncSettingsDataStore = syncSettings,
            appSettingsDataStore = appSettings,
            memberRepository = members,
            syncExclusiveLock = SyncExclusiveLock(),
            syncChangeTracker = tracker,
            firebaseAuth = firebaseAuth,
            ioDispatcher = testDispatcher,
            context = context
        )

    private fun emptyCounts() = RestoreCollectionCounts(
        members = 0,
        tasks = 0,
        shoppingLists = 0,
        shoppingItems = 0,
        events = 0,
        recipes = 0,
        familyGoals = 0,
        rewards = 0,
        penalties = 0,
        weeklyMenus = 0,
        menuTemplates = 0
    )
}
