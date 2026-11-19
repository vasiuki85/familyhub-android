package com.familyhub.ui.screens.settings.encrypted

import com.familyhub.data.backup.EncryptedBackupAuthException
import com.familyhub.data.backup.EncryptedBackupFormatException
import com.familyhub.data.restore.RestoreBarrierConflictException
import com.familyhub.data.restore.RestoreFingerprintMismatchException
import com.familyhub.data.restore.RestoreStagingException
import com.familyhub.data.restore.RestoreStagingFailure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class EncryptedBackupRestoreErrorMapperTest {

    @Test
    fun mapsSafeErrorsWithoutMessages() {
        assertEquals(
            EncryptedBackupRestoreError.WRONG_PASSWORD,
            EncryptedBackupRestoreErrorMapper.map(EncryptedBackupAuthException())
        )
        assertEquals(
            EncryptedBackupRestoreError.FILE_CHANGED,
            EncryptedBackupRestoreErrorMapper.map(RestoreFingerprintMismatchException())
        )
        assertEquals(
            EncryptedBackupRestoreError.RESTORE_IN_PROGRESS,
            EncryptedBackupRestoreErrorMapper.map(RestoreBarrierConflictException())
        )
        assertEquals(
            EncryptedBackupRestoreError.SIZE_EXCEEDED,
            EncryptedBackupRestoreErrorMapper.map(
                RestoreStagingException(RestoreStagingFailure.SIZE_EXCEEDED)
            )
        )
        assertEquals(
            EncryptedBackupRestoreError.NO_SPACE,
            EncryptedBackupRestoreErrorMapper.map(IOException("ENOSPC"))
        )
        assertEquals(
            EncryptedBackupRestoreError.CORRUPT_OR_UNSUPPORTED,
            EncryptedBackupRestoreErrorMapper.map(EncryptedBackupFormatException("x"))
        )
    }
}
