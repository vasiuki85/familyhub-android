package com.familyhub.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FhSearchFieldSourceContractTest {
    @Test
    fun sharedSearchField_isFilledRoundedAndHasNoDarkIndicator() {
        val source = File("src/main/java/com/familyhub/ui/components/FhSearchField.kt")
            .readText(Charsets.UTF_8)

        assertTrue(source.contains("Icons.Rounded.Search"))
        assertTrue(source.contains("surfaceContainerLow"))
        assertTrue(source.contains("focusedIndicatorColor = Color.Transparent"))
        assertTrue(source.contains("unfocusedIndicatorColor = Color.Transparent"))
        assertFalse(source.contains("OutlinedTextField("))
    }

    @Test
    fun tasksAndShopping_useSharedSearchField() {
        val tasksFilters = File("src/main/java/com/familyhub/ui/screens/tasks/TaskFilterAndSortBar.kt")
            .readText(Charsets.UTF_8)
        val shopping = File("src/main/java/com/familyhub/ui/screens/shopping/ShoppingScreen.kt")
            .readText(Charsets.UTF_8)

        assertTrue(tasksFilters.contains("FhSearchField("))
        assertTrue(shopping.contains("FhSearchField("))
    }
}
