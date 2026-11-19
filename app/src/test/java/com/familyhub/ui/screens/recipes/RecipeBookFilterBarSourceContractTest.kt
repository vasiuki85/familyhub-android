package com.familyhub.ui.screens.recipes

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeBookFilterBarSourceContractTest {

    private val filterBarSource: String by lazy {
        val source = File(
            "src/main/java/com/familyhub/ui/screens/recipes/RecipeBookScreen.kt"
        ).readText(Charsets.UTF_8)
        source.substring(
            startIndex = source.indexOf("internal fun RecipeBookFilterBar"),
            endIndex = source.indexOf("internal fun RecipeCard"),
        )
    }

    @Test
    fun searchField_isFilledAndHasNoIndicator() {
        assertTrue(filterBarSource.contains("TextField("))
        assertFalse(filterBarSource.contains("OutlinedTextField("))
        assertTrue(filterBarSource.contains("focusedIndicatorColor = Color.Transparent"))
        assertTrue(filterBarSource.contains("unfocusedIndicatorColor = Color.Transparent"))
        assertTrue(filterBarSource.contains("Icons.Rounded.Search"))
    }

    @Test
    fun categoryChips_useNeutralAndFoodSectionColors() {
        assertTrue(filterBarSource.contains("MaterialTheme.colorScheme.outlineVariant"))
        assertTrue(filterBarSource.contains("FhTheme.modules.food.container"))
        assertTrue(filterBarSource.contains("FhTheme.modules.food.onContainer"))
    }

    @Test
    fun pinnedBar_remainsOpaque() {
        assertTrue(filterBarSource.contains("MaterialTheme.colorScheme.surface"))
        assertTrue(filterBarSource.contains("RecipeBookLayoutPolicy.FILTER_BAR_Z_INDEX"))
    }
}
