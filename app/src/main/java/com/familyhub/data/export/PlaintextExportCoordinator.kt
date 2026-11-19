package com.familyhub.data.export

import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.domain.usecase.CreateBackupUseCase
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PlaintextExportCoordinator @Inject constructor(
    private val exportService: DataExportService,
    private val createBackupUseCase: CreateBackupUseCase,
    private val appSettingsDataStore: AppSettingsDataStore
) {
    private val inFlight = AtomicBoolean(false)

    suspend fun confirmAndShare(
        request: PlaintextExportPendingRequest,
        onAcquired: suspend () -> Unit = {}
    ): PlaintextExportConfirmResult {
        if (!inFlight.compareAndSet(false, true)) {
            return PlaintextExportConfirmResult.AlreadyInFlight
        }
        return try {
            onAcquired()
            val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
            val file = when (request) {
                is PlaintextExportPendingRequest.Format -> {
                    exportService.exportData(request.format, currentUserId)
                }
                PlaintextExportPendingRequest.Backup -> {
                    createBackupUseCase()
                }
            }
            PlaintextExportConfirmResult.Success(exportService.shareFile(file))
        } finally {
            inFlight.set(false)
        }
    }
}
