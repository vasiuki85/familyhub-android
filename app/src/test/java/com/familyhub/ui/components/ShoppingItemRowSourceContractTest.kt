package com.familyhub.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingItemRowSourceContractTest {

    private val source: String by lazy {
        File("src/main/java/com/familyhub/ui/components/ShoppingItemRow.kt")
            .readText(Charsets.UTF_8)
    }

    @Test
    fun categoryStripe_isInsideSurface_notDrawBehind() {
        assertFalse(source.contains("drawBehind"))
        assertTrue(source.contains(SHOPPING_CATEGORY_STRIPE_TEST_TAG))
        assertTrue(source.contains(".fillMaxHeight()"))
        assertTrue(source.contains(".background(categoryStripeColor)"))
    }

    @Test
    fun uncategorisedItems_skipStripeBranch() {
        assertTrue(source.contains("if (categoryStripeColor != null)"))
    }

    @Test
    fun creatorAttribution_shownWhenCreatorPresent() {
        assertTrue(source.contains(SHOPPING_CREATOR_ATTRIBUTION_TEST_TAG))
        assertTrue(source.contains("creator != null"))
        assertTrue(source.contains("displayLabel()"))
        assertFalse(source.contains("showCreatorAvatar"))
    }
}
