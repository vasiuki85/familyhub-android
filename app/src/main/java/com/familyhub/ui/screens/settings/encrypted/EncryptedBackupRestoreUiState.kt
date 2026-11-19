package com.familyhub.ui.screens.settings.encrypted

import androidx.annotation.StringRes
import com.familyhub.R
import com.familyhub.data.backup.EncryptedBackupAuthException
import com.familyhub.data.backup.EncryptedBackupFormatException
import com.familyhub.data.backup.EncryptedBackupPasswordException
import com.familyhub.data.backup.EncryptedBackupUnsupportedFormatException
import com.familyhub.data.restore.RestoreBarrierConflictException
import com.familyhub.data.restore.RestoreCollectionCounts
import com.familyhub.data.restore.RestoreFingerprintMismatchException
import com.familyhub.data.restore.RestoreStagingException
import com.familyhub.data.restore.RestoreStagingFailure
import com.familyhub.data.restore.RestoreValidationException
import java.io.IOException

enum class EncryptedBackupRestoreError(@StringRes val messageRes: Int) {
    WRONG_PASSWORD(R.string.encrypted_backup_error_wrong_password),
    CORRUPT_OR_UNSUPPORTED(R.string.encrypted_backup_error_corrupt_or_unsupported),
    SIZE_EXCEEDED(R.string.encrypted_backup_error_size_exceeded),
    FILE_CHANGED(R.string.encrypted_backup_error_file_changed),
    AUTH_SESSION_CHANGED(R.string.encrypted_backup_error_auth_session_changed),
    NO_SPACE(R.string.encrypted_backup_error_no_space),
    RESTORE_IN_PROGRESS(R.string.encrypted_backup_error_restore_in_progress),
    PASSWORD_POLICY(R.string.encrypted_backup_error_password_policy),
    PASSWORD_MISMATCH(R.string.encrypted_backup_error_password_mismatch),
    GENERIC(R.string.encrypted_backup_error_generic)
}

/**
 * Single phase machine for encrypted backup/restore UI.
 * Dialog visibility is derived from [EncryptedBackupRestorePhase] only.
 */
sealed class EncryptedBackupRestorePhase {
    data object Hydrating : EncryptedBackupRestorePhase()

    data object Idle : EncryptedBackupRestorePhase()

    data class CreatePassword(
        val error: EncryptedBackupRestoreError? = null
    ) : EncryptedBackupRestorePhase()

    data object CreatingBackup : EncryptedBackupRestorePhase()

    data object Staging : EncryptedBackupRestorePhase()

    data class AwaitingInspectPassword(
        val stagingPath: String,
        val error: EncryptedBackupRestoreError? = null
    ) : EncryptedBackupRestorePhase()

    data class Inspecting(
        val stagingPath: String
    ) : EncryptedBackupRestorePhase()

    data class Preview(
        val stagingPath: String,
        val fingerprintSha256: String,
        val createdAt: Long,
        val sourceFamilyId: String?,
        val counts: RestoreCollectionCounts,
        val inspectAuthUid: String?,
        val containsAuthenticatedUser: Boolean
    ) : EncryptedBackupRestorePhase()

    data class ConfirmRestore(
        val preview: Preview,
        val requiresUidConfirmation: Boolean,
        val error: EncryptedBackupRestoreError? = null
    ) : EncryptedBackupRestorePhase()

    data class Restoring(
        val preview: Preview
    ) : EncryptedBackupRestorePhase()

    data object AddingLocalProfile : EncryptedBackupRestorePhase()

    data class Success(
        val needsLocalProfile: Boolean
    ) : EncryptedBackupRestorePhase()

    data class FatalError(
        val error: EncryptedBackupRestoreError
    ) : EncryptedBackupRestorePhase()
}

data class EncryptedBackupRestoreUiState(
    val phase: EncryptedBackupRestorePhase = EncryptedBackupRestorePhase.Hydrating,
    /** Persistent recovery from DataStore + members; survives process death. */
    val needsLocalProfileRecovery: Boolean = false,
    val addLocalProfileError: EncryptedBackupRestoreError? = null
) {
    val blocksDismiss: Boolean
        get() = when (phase) {
            is EncryptedBackupRestorePhase.Hydrating,
            is EncryptedBackupRestorePhase.Staging,
            is EncryptedBackupRestorePhase.CreatingBackup,
            is EncryptedBackupRestorePhase.Inspecting,
            is EncryptedBackupRestorePhase.Restoring,
            is EncryptedBackupRestorePhase.AddingLocalProfile -> true
            else -> false
        }

    val allowsPrimaryActions: Boolean
        get() = phase is EncryptedBackupRestorePhase.Idle ||
            phase is EncryptedBackupRestorePhase.CreatePassword ||
            phase is EncryptedBackupRestorePhase.AwaitingInspectPassword ||
            phase is EncryptedBackupRestorePhase.Preview ||
            phase is EncryptedBackupRestorePhase.ConfirmRestore ||
            phase is EncryptedBackupRestorePhase.Success ||
            phase is EncryptedBackupRestorePhase.FatalError
}

sealed class EncryptedBackupRestoreUiEvent {
    data class ShareBackup(val intent: android.content.Intent) : EncryptedBackupRestoreUiEvent()
}

object EncryptedBackupRestoreErrorMapper {
    fun map(error: Throwable): EncryptedBackupRestoreError = when (error) {
        is EncryptedBackupAuthException -> EncryptedBackupRestoreError.WRONG_PASSWORD
        is EncryptedBackupPasswordException -> EncryptedBackupRestoreError.PASSWORD_POLICY
        is EncryptedBackupUnsupportedFormatException -> EncryptedBackupRestoreError.CORRUPT_OR_UNSUPPORTED
        is EncryptedBackupFormatException -> EncryptedBackupRestoreError.CORRUPT_OR_UNSUPPORTED
        is RestoreValidationException -> EncryptedBackupRestoreError.CORRUPT_OR_UNSUPPORTED
        is RestoreFingerprintMismatchException -> EncryptedBackupRestoreError.FILE_CHANGED
        is RestoreBarrierConflictException -> EncryptedBackupRestoreError.RESTORE_IN_PROGRESS
        is RestoreStagingException -> when (error.failure) {
            RestoreStagingFailure.SIZE_EXCEEDED -> EncryptedBackupRestoreError.SIZE_EXCEEDED
            RestoreStagingFailure.NO_SPACE -> EncryptedBackupRestoreError.NO_SPACE
            RestoreStagingFailure.READ_FAILED,
            RestoreStagingFailure.INVALID_FILE -> EncryptedBackupRestoreError.CORRUPT_OR_UNSUPPORTED
        }
        is IOException -> {
            val message = error.message.orEmpty()
            if (message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("No space", ignoreCase = true)
            ) {
                EncryptedBackupRestoreError.NO_SPACE
            } else {
                EncryptedBackupRestoreError.GENERIC
            }
        }
        else -> EncryptedBackupRestoreError.GENERIC
    }
}
