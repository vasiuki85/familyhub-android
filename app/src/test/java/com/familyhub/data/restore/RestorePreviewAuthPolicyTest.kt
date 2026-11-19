package com.familyhub.data.restore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePreviewAuthPolicyTest {

    @Test
    fun authSessionMatches_whenSameUid() {
        assertTrue(RestorePreviewAuthPolicy.authSessionMatches("uid-a", "uid-a"))
    }

    @Test
    fun authSessionMismatch_whenUidChanged() {
        assertFalse(RestorePreviewAuthPolicy.authSessionMatches("uid-a", "uid-b"))
    }

    @Test
    fun authSessionMatches_whenBothNull() {
        assertTrue(RestorePreviewAuthPolicy.authSessionMatches(null, null))
    }
}
