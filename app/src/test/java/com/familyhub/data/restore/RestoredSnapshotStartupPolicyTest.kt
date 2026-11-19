package com.familyhub.data.restore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoredSnapshotStartupPolicyTest {

    @Test
    fun suppressesSilentBootstrapAfterDetachedRestore() {
        assertFalse(
            RestoredSnapshotStartupPolicy.shouldAutoCreateAuthenticatedMember(
                authenticatedUid = "uid-1",
                membersContainUid = false,
                suppressAuthMemberBootstrap = true,
                restoreInProgress = false
            )
        )
    }

    @Test
    fun blocksBootstrapWhileRestoreInProgress() {
        assertFalse(
            RestoredSnapshotStartupPolicy.shouldAutoCreateAuthenticatedMember(
                authenticatedUid = "uid-1",
                membersContainUid = false,
                suppressAuthMemberBootstrap = false,
                restoreInProgress = true
            )
        )
    }

    @Test
    fun allowsBootstrapWhenNotSuppressedAndUidMissing() {
        assertTrue(
            RestoredSnapshotStartupPolicy.shouldAutoCreateAuthenticatedMember(
                authenticatedUid = "uid-1",
                membersContainUid = false,
                suppressAuthMemberBootstrap = false,
                restoreInProgress = false
            )
        )
    }

    @Test
    fun skipsWhenUidAlreadyPresent() {
        assertFalse(
            RestoredSnapshotStartupPolicy.shouldAutoCreateAuthenticatedMember(
                authenticatedUid = "uid-1",
                membersContainUid = true,
                suppressAuthMemberBootstrap = false,
                restoreInProgress = false
            )
        )
    }

    @Test
    fun skipsWhenNoAuthenticatedUser() {
        assertFalse(
            RestoredSnapshotStartupPolicy.shouldAutoCreateAuthenticatedMember(
                authenticatedUid = null,
                membersContainUid = false,
                suppressAuthMemberBootstrap = false,
                restoreInProgress = false
            )
        )
    }

    @Test
    fun clearsStaleSuppressionWhenUidPresentAndNotRestoring() {
        assertTrue(
            RestoredSnapshotStartupPolicy.shouldClearStaleSuppression(
                membersContainUid = true,
                suppressAuthMemberBootstrap = true,
                restoreInProgress = false
            )
        )
        assertFalse(
            RestoredSnapshotStartupPolicy.shouldClearStaleSuppression(
                membersContainUid = true,
                suppressAuthMemberBootstrap = true,
                restoreInProgress = true
            )
        )
        assertFalse(
            RestoredSnapshotStartupPolicy.shouldClearStaleSuppression(
                membersContainUid = false,
                suppressAuthMemberBootstrap = true,
                restoreInProgress = false
            )
        )
    }
}
