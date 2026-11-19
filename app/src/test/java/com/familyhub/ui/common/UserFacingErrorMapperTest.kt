package com.familyhub.ui.common

import com.familyhub.R
import com.familyhub.data.sync.SyncFamilyInvariantException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFacingErrorMapperTest {
    @Test
    fun unknownThrowable_usesContextFallback() {
        assertEquals(
            R.string.error_task_update_failed,
            UserFacingErrorMapper.messageRes(
                IllegalStateException("Room cannot open database"),
                UserFacingErrorContext.TaskUpdate,
            ),
        )
    }

    @Test
    fun syncInvariant_delegatesToSettingsMapper() {
        assertEquals(
            R.string.settings_error_multiple_admins,
            UserFacingErrorMapper.messageRes(
                SyncFamilyInvariantException.MultipleAdmins(),
                UserFacingErrorContext.General,
            ),
        )
    }

    @Test
    fun networkIOException_usesNetworkResource() {
        assertEquals(
            R.string.error_network_unavailable,
            UserFacingErrorMapper.messageRes(UnknownHostException("firestore.googleapis.com")),
        )
    }

    @Test
    fun rawExceptionMessage_neverReturnedAsResource() {
        val res = UserFacingErrorMapper.messageRes(
            RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions."),
            UserFacingErrorContext.LoadData,
        )
        assertEquals(R.string.error_load_failed, res)
    }
}
