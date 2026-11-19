package com.familyhub.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRulesVisualContractTest {

    @Test
    fun homeProfileCard_keepsFamilySummary_withoutSensitiveProfileDetails() {
        val source = source("screens/home/UserProfileCard.kt")

        assertTrue(source.contains("R.string.home_redesign_greeting"))
        assertTrue(source.contains("familyMembers.forEach"))
        assertTrue(source.contains("familyMembers.size > 1"))
        assertTrue(source.contains("R.string.home_redesign_week_score"))
        assertFalse(source.contains("roleLabel("))
        assertFalse(source.contains("profile_birthdate_label"))
        assertFalse(source.contains("profile_age_label"))
        assertFalse(source.contains("computeAge("))
    }

    @Test
    fun houseRuleCards_useNeutralSurface_withFocusedRuleAccent() {
        val source = source("screens/rules/HouseRuleCard.kt")

        assertTrue(source.contains("Surface("))
        assertTrue(source.contains("ruleCategoryIcon(item.rule.category)"))
        assertTrue(source.contains("tint = MaterialTheme.colorScheme.primary"))
        assertFalse(source.contains("FhCardTone.INFO"))
    }

    @Test
    fun houseRules_useSharedLowContrastSearchField() {
        val source = source("screens/rules/RulesScreen.kt")

        assertTrue(source.contains("FhSearchField("))
        assertFalse(source.contains("Icons.Default.Search"))
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/familyhub/ui/$relativePath")
            .readText(Charsets.UTF_8)
}
