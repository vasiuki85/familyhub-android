package com.familyhub.data.restore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedsLocalProfilePolicyTest {

    @Test
    fun recoveryNeeded_whenSuppressedAndUidMissing() {
        assertTrue(
            NeedsLocalProfilePolicy.isRecoveryNeeded(
                suppressAuthMemberBootstrap = true,
                authenticatedUid = "uid-1",
                membersContainUid = false
            )
        )
    }

    @Test
    fun recoveryNotNeeded_whenUidPresentOrNotSuppressed() {
        assertFalse(
            NeedsLocalProfilePolicy.isRecoveryNeeded(
                suppressAuthMemberBootstrap = true,
                authenticatedUid = "uid-1",
                membersContainUid = true
            )
        )
        assertFalse(
            NeedsLocalProfilePolicy.isRecoveryNeeded(
                suppressAuthMemberBootstrap = false,
                authenticatedUid = "uid-1",
                membersContainUid = false
            )
        )
        assertFalse(
            NeedsLocalProfilePolicy.isRecoveryNeeded(
                suppressAuthMemberBootstrap = true,
                authenticatedUid = null,
                membersContainUid = false
            )
        )
    }
}
