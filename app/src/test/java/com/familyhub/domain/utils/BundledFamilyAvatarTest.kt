package com.familyhub.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledFamilyAvatarTest {
    @Test
    fun catalog_hasExactlyEightStableUniqueTokens() {
        assertEquals(8, BundledFamilyAvatar.tokens.size)
        assertEquals(8, BundledFamilyAvatar.tokens.toSet().size)
        assertEquals("familyhub-avatar://01", BundledFamilyAvatar.tokens.first())
        assertEquals("familyhub-avatar://08", BundledFamilyAvatar.tokens.last())
    }

    @Test
    fun isToken_rejectsUnknownOrMalformedValues() {
        assertTrue(BundledFamilyAvatar.isToken("familyhub-avatar://04"))
        assertFalse(BundledFamilyAvatar.isToken("familyhub-avatar://09"))
        assertFalse(BundledFamilyAvatar.isToken("https://example.com/avatar.png"))
        assertFalse(BundledFamilyAvatar.isToken(null))
    }
}
