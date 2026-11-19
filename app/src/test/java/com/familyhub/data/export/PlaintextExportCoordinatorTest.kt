package com.familyhub.data.export

import android.content.Intent
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.usecase.CreateBackupUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaintextExportCoordinatorTest {

    private val exportService = mockk<DataExportService>()
    private val createBackupUseCase = mockk<CreateBackupUseCase>()
    private val appSettingsDataStore = mockk<AppSettingsDataStore>()
    private lateinit var coordinator: PlaintextExportCoordinator
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = createTempDirectory(prefix = "fh_export_coord_").toFile()
        every { appSettingsDataStore.settingsFlow } returns flowOf(AppSettings(currentUserId = "user-1"))
        coordinator = PlaintextExportCoordinator(
            exportService = exportService,
            createBackupUseCase = createBackupUseCase,
            appSettingsDataStore = appSettingsDataStore
        )
    }

    @Test
    fun doubleConfirm_generatesSingleExport() = runTest {
        val exportFile = File(cacheDir, "familyhub_export_test.json")
        exportFile.writeText("{}", StandardCharsets.UTF_8)
        val shareIntent = Intent(Intent.ACTION_SEND)
        val request = PlaintextExportPendingRequest.Format(ExportFormat.JSON)

        coEvery { exportService.exportData(ExportFormat.JSON, "user-1") } coAnswers {
            delay(100)
            exportFile
        }
        every { exportService.shareFile(exportFile) } returns shareIntent

        val first = async { coordinator.confirmAndShare(request) }
        val second = async { coordinator.confirmAndShare(request) }

        assertSame(shareIntent, (first.await() as PlaintextExportConfirmResult.Success).intent)
        assertSame(PlaintextExportConfirmResult.AlreadyInFlight, second.await())

        coVerify(exactly = 1) { exportService.exportData(ExportFormat.JSON, "user-1") }
        verify(exactly = 1) { exportService.shareFile(exportFile) }
    }

    @Test
    fun alreadyInFlight_doesNotInvokeOnAcquired() = runTest {
        val exportFile = File(cacheDir, "familyhub_export_inflight.json")
        exportFile.writeText("{}", StandardCharsets.UTF_8)
        val request = PlaintextExportPendingRequest.Format(ExportFormat.JSON)
        var onAcquiredCount = 0

        coEvery { exportService.exportData(ExportFormat.JSON, "user-1") } coAnswers {
            delay(100)
            exportFile
        }
        every { exportService.shareFile(exportFile) } returns Intent(Intent.ACTION_SEND)

        val first = async {
            coordinator.confirmAndShare(request) {
                onAcquiredCount++
            }
        }
        delay(10)
        val second = coordinator.confirmAndShare(request) {
            onAcquiredCount++
        }

        assertSame(PlaintextExportConfirmResult.AlreadyInFlight, second)
        first.await()
        assertEquals(1, onAcquiredCount)
    }

    @Test
    fun confirmBackup_exportsAndShares() = runTest {
        val backupFile = File(cacheDir, "familyhub_backup_test.json")
        backupFile.writeText("{}", StandardCharsets.UTF_8)
        val shareIntent = Intent(Intent.ACTION_SEND)

        coEvery { createBackupUseCase() } returns backupFile
        every { exportService.shareFile(backupFile) } returns shareIntent

        val result = coordinator.confirmAndShare(PlaintextExportPendingRequest.Backup)
        assertSame(shareIntent, (result as PlaintextExportConfirmResult.Success).intent)

        coVerify(exactly = 1) { createBackupUseCase() }
        verify(exactly = 1) { exportService.shareFile(backupFile) }
    }

    @Test
    fun exception_releasesInFlightGuard() = runTest {
        val request = PlaintextExportPendingRequest.Format(ExportFormat.JSON)
        coEvery { exportService.exportData(ExportFormat.JSON, "user-1") } throws RuntimeException("boom")

        val failure = runCatching { coordinator.confirmAndShare(request) }
        assertTrue(failure.isFailure)
        assertEquals("boom", failure.exceptionOrNull()?.message)

        val exportFile = File(cacheDir, "familyhub_export_retry.json")
        exportFile.writeText("{}", StandardCharsets.UTF_8)
        val shareIntent = Intent(Intent.ACTION_SEND)
        coEvery { exportService.exportData(ExportFormat.JSON, "user-1") } returns exportFile
        every { exportService.shareFile(exportFile) } returns shareIntent

        val retry = coordinator.confirmAndShare(request)
        assertTrue(retry is PlaintextExportConfirmResult.Success)
    }
}
