package com.familyhub.data.restore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreUidPresencePolicyTest {

    @Test
    fun containsAuthenticatedUser_trueWhenUidPresent() {
        assertTrue(
            RestoreUidPresencePolicy.containsAuthenticatedUser(
                memberIds = listOf("a", "uid-1"),
                authenticatedUid = "uid-1"
            )
        )
    }

    @Test
    fun containsAuthenticatedUser_falseWhenMissingOrBlank() {
        assertFalse(
            RestoreUidPresencePolicy.containsAuthenticatedUser(
                memberIds = listOf("a"),
                authenticatedUid = "uid-1"
            )
        )
        assertFalse(
            RestoreUidPresencePolicy.containsAuthenticatedUser(
                memberIds = listOf("uid-1"),
                authenticatedUid = null
            )
        )
        assertFalse(
            RestoreUidPresencePolicy.containsAuthenticatedUser(
                memberIds = listOf("uid-1"),
                authenticatedUid = "  "
            )
        )
    }

    @Test
    fun requiresExtraUidConfirmation_onlyWhenAuthPresentAndMissingFromBackup() {
        assertTrue(
            RestoreUidPresencePolicy.requiresExtraUidConfirmation(
                containsAuthenticatedUser = false,
                authenticatedUid = "uid-1"
            )
        )
        assertFalse(
            RestoreUidPresencePolicy.requiresExtraUidConfirmation(
                containsAuthenticatedUser = true,
                authenticatedUid = "uid-1"
            )
        )
        assertFalse(
            RestoreUidPresencePolicy.requiresExtraUidConfirmation(
                containsAuthenticatedUser = false,
                authenticatedUid = null
            )
        )
    }
}
