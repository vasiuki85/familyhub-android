package com.familyhub.data.export

import android.content.Intent

sealed interface PlaintextExportConfirmResult {
    data class Success(val intent: Intent) : PlaintextExportConfirmResult

    data object AlreadyInFlight : PlaintextExportConfirmResult
}
