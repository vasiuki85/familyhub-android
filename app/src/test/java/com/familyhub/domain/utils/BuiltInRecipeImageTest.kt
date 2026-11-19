package com.familyhub.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInRecipeImageTest {
    @Test
    fun catalog_hasThirtyStableUniqueTokens() {
        assertEquals(30, BuiltInRecipeImage.tokens.size)
        assertEquals(30, BuiltInRecipeImage.tokens.toSet().size)
        assertEquals("familyhub-recipe://01", BuiltInRecipeImage.tokens.first())
        assertEquals("familyhub-recipe://30", BuiltInRecipeImage.tokens.last())
    }

    @Test
    fun isToken_failsClosed() {
        assertTrue(BuiltInRecipeImage.isToken("familyhub-recipe://17"))
        assertFalse(BuiltInRecipeImage.isToken("familyhub-recipe://31"))
        assertFalse(BuiltInRecipeImage.isToken("familyhub-recipe://1"))
        assertFalse(BuiltInRecipeImage.isToken(null))
    }
}
