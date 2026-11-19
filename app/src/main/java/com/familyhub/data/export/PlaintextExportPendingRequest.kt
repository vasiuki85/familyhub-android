package com.familyhub.data.export

sealed interface PlaintextExportPendingRequest {
    data class Format(val format: ExportFormat) : PlaintextExportPendingRequest

    data object Backup : PlaintextExportPendingRequest
}
