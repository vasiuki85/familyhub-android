package com.familyhub.ui.screens.menu

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyMenuCookModelSourceContractTest {

    @Test
    fun mealRow_doesNotRepeatDayCookAssignment() {
        val mealRowSource = source("screens/menu/WeeklyMenuMealRow.kt")
        assertFalse(mealRowSource.contains("menu_assigned_to"))
        assertFalse(mealRowSource.contains("menu_assign_placeholder"))
    }

    @Test
    fun dayCookHeader_isTheOnlyCookSurfaceInDayCard() {
        val screenSource = source("screens/menu/WeeklyMenuScreen.kt")
        val cookHeaderCount = screenSource.split("WeeklyMenuDayCookHeader").size - 1
        assertTrue("Expected exactly one day cook header", cookHeaderCount == 1)
        assertFalse(screenSource.contains("menu_assign_button"))
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/familyhub/ui/$relativePath")
            .readText(Charsets.UTF_8)
}
