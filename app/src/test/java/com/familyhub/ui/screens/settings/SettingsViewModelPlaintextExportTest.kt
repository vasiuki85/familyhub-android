package com.familyhub.ui.screens.settings

import android.content.Context
import android.content.Intent
import com.familyhub.data.analytics.ActivationAnalytics
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.export.ExportFormat
import com.familyhub.data.export.PlaintextExportConfirmResult
import com.familyhub.data.export.PlaintextExportCoordinator
import com.familyhub.data.export.PlaintextExportPendingRequest
import com.familyhub.data.repository.AuthenticatedMemberBootstrapCoordinator
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.SyncState
import com.familyhub.domain.model.ThemeMode
import com.familyhub.domain.usecase.ObserveSyncStateUseCase
import com.google.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelPlaintextExportTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsFlow = MutableStateFlow(AppSettings(currentUserId = "user-1"))
    private val membersFlow = MutableStateFlow<List<FamilyMember>>(emptyList())
    private val syncStateFlow = MutableStateFlow(SyncState())
    private val plaintextExportCoordinator = mockk<PlaintextExportCoordinator>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun doubleConfirm_keepsProgressUntilFirstCompletes() = runTest {
        val exportStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        val shareIntent = Intent(Intent.ACTION_SEND)
        val request = PlaintextExportPendingRequest.Format(ExportFormat.JSON)

        var calls = 0
        coEvery { plaintextExportCoordinator.confirmAndShare(request, any()) } coAnswers {
            if (calls++ > 0) {
                return@coAnswers PlaintextExportConfirmResult.AlreadyInFlight
            }
            secondArg<suspend () -> Unit>().invoke()
            exportStarted.complete(Unit)
            allowFinish.await()
            PlaintextExportConfirmResult.Success(shareIntent)
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestPlaintextExport(request)

        val first = async { viewModel.confirmPlaintextExport() }
        exportStarted.await()
        assertTrue(viewModel.state.value.isExporting)

        val second = async { viewModel.confirmPlaintextExport() }
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isExporting)

        allowFinish.complete(Unit)
        assertSame(shareIntent, first.await())
        assertNull(second.await())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isExporting)
        assertNull(viewModel.state.value.pendingPlaintextExport)
    }

    @Test
    fun alreadyInFlight_doesNotSetProgress() = runTest {
        val request = PlaintextExportPendingRequest.Format(ExportFormat.JSON)
        coEvery { plaintextExportCoordinator.confirmAndShare(request, any()) } returns
            PlaintextExportConfirmResult.AlreadyInFlight

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.requestPlaintextExport(request)

        assertNull(viewModel.confirmPlaintextExport())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isExporting)
        assertEquals(request, viewModel.state.value.pendingPlaintextExport)
    }

    @Test
    fun cancellation_resetsProgressAndKeepsPendingWarning() = runTest {
        val request = PlaintextExportPendingRequest.Format(ExportFormat.JSON)
        coEvery { plaintextExportCoordinator.confirmAndShare(request, any()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
            throw CancellationException("cancelled")
        }

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.requestPlaintextExport(request)

        try {
            viewModel.confirmPlaintextExport()
            error("Expected CancellationException")
        } catch (_: CancellationException) {
        }
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isExporting)
        assertEquals(request, viewModel.state.value.pendingPlaintextExport)
    }

    @Test
    fun afterCompletion_nextExportIsAllowed() = runTest {
        val shareIntent = Intent(Intent.ACTION_SEND)
        val request = PlaintextExportPendingRequest.Format(ExportFormat.CSV)

        coEvery { plaintextExportCoordinator.confirmAndShare(request, any()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
            PlaintextExportConfirmResult.Success(shareIntent)
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestPlaintextExport(request)
        assertSame(shareIntent, viewModel.confirmPlaintextExport())
        advanceUntilIdle()

        viewModel.requestPlaintextExport(request)
        assertSame(shareIntent, viewModel.confirmPlaintextExport())
    }

    @Test
    fun contentEmission_preservesPendingExportAndProgress() = runTest {
        val exportStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        val shareIntent = Intent(Intent.ACTION_SEND)
        val request = PlaintextExportPendingRequest.Format(ExportFormat.JSON)

        coEvery { plaintextExportCoordinator.confirmAndShare(request, any()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
            exportStarted.complete(Unit)
            allowFinish.await()
            PlaintextExportConfirmResult.Success(shareIntent)
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestPlaintextExport(request)
        val exportJob = async { viewModel.confirmPlaintextExport() }
        exportStarted.await()

        settingsFlow.value = settingsFlow.value.copy(themeMode = ThemeMode.DARK)
        membersFlow.value = listOf(sampleMember())
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(request, state.pendingPlaintextExport)
        assertTrue(state.isExporting)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(1, state.members.size)

        allowFinish.complete(Unit)
        exportJob.await()
        advanceUntilIdle()
    }

    @Test
    fun cancelDuringProgress_doesNotClearPendingExport() = runTest {
        val exportStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        val request = PlaintextExportPendingRequest.Backup

        coEvery { plaintextExportCoordinator.confirmAndShare(request, any()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
            exportStarted.complete(Unit)
            allowFinish.await()
            PlaintextExportConfirmResult.Success(Intent(Intent.ACTION_SEND))
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestPlaintextExport(request)
        val exportJob = async { viewModel.confirmPlaintextExport() }
        exportStarted.await()

        viewModel.cancelPlaintextExport()
        advanceUntilIdle()

        assertEquals(request, viewModel.state.value.pendingPlaintextExport)
        assertTrue(viewModel.state.value.isBackupInProgress)

        allowFinish.complete(Unit)
        exportJob.await()
        advanceUntilIdle()
    }

    @Test
    fun applyContentSnapshot_preservesExportErrorsAndPendingState() {
        val pending = PlaintextExportPendingRequest.Format(ExportFormat.JSON)
        val original = SettingsUiState(
            pendingPlaintextExport = pending,
            isExporting = true,
            exportError = "export failed",
            backupError = "backup failed",
            syncActionError = "sync failed"
        )
        val content = SettingsContentSnapshot(
            themeMode = ThemeMode.DARK,
            themeCustomJson = null,
            biometricEnabled = true,
            biometricAvailable = true,
            members = listOf(sampleMember()),
            currentUserId = "user-1",
            currentUserRole = MemberRole.ADMIN,
            bottomNavRoutes = emptyList()
        )

        val updated = original.applyContentSnapshot(content)

        assertEquals(ThemeMode.DARK, updated.themeMode)
        assertEquals(pending, updated.pendingPlaintextExport)
        assertTrue(updated.isExporting)
        assertEquals("export failed", updated.exportError)
        assertEquals("backup failed", updated.backupError)
        assertEquals("sync failed", updated.syncActionError)
    }

    private fun createViewModel(): SettingsViewModel {
        val appSettingsDataStore = mockk<AppSettingsDataStore>()
        every { appSettingsDataStore.settingsFlow } returns settingsFlow

        val memberRepository = mockk<FamilyMemberRepository>()
        every { memberRepository.getAllMembers() } returns membersFlow

        val observeSyncStateUseCase = mockk<ObserveSyncStateUseCase>()
        every { observeSyncStateUseCase() } returns syncStateFlow

        return SettingsViewModel(
            appSettingsDataStore = appSettingsDataStore,
            syncSettingsDataStore = mockk(relaxed = true),
            memberRepository = memberRepository,
            plaintextExportCoordinator = plaintextExportCoordinator,
            observeSyncStateUseCase = observeSyncStateUseCase,
            enableSyncUseCase = mockk(relaxed = true),
            disableSyncUseCase = mockk(relaxed = true),
            pauseSyncUseCase = mockk(relaxed = true),
            performSyncNowUseCase = mockk(relaxed = true),
            signOutUseCase = mockk(relaxed = true),
            syncRepository = mockk(relaxed = true),
            authenticatedMemberBootstrapCoordinator = mockk(relaxed = true) {
                coEvery { ensureAuthenticatedMemberExists() } returns
                    AuthenticatedMemberBootstrapCoordinator.Outcome.Skipped
            },
            context = mockk<Context>(relaxed = true),
            firebaseAuth = mockk<FirebaseAuth>(relaxed = true) {
                every { currentUser } returns null
            },
            activationAnalytics = mockk<ActivationAnalytics>(relaxed = true),
        )
    }

    private fun sampleMember() = FamilyMember(
        id = "member-1",
        displayName = "Parent",
        roleLabel = "Parent",
        avatarColor = "#2196F3",
        role = MemberRole.PARENT,
        age = null,
        birthDate = null,
        photoUrl = null
    )
}
