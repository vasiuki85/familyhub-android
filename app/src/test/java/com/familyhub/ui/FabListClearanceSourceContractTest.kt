package com.familyhub.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FabListClearanceSourceContractTest {

    @Test
    fun screensWithFloatingActionButton_reserveSharedListBottomPadding() {
        fabScreenPaths.forEach { (path, patterns) ->
            val source = source(path)
            assertTrue(
                "$path should reserve bottom clearance via FhLayout.listBottomPadding",
                patterns.any { source.contains(it) },
            )
        }
    }

    @Test
    fun determinateProgressBars_useFhProgressBar() {
        determinateProgressPaths.forEach { path ->
            val source = source(path)
            assertTrue(
                "$path should render determinate progress via FhProgressBar",
                source.contains("FhProgressBar("),
            )
            assertFalse(
                "$path should not call LinearProgressIndicator for determinate progress",
                source.contains("LinearProgressIndicator("),
            )
        }
    }

    private val fabScreenPaths = listOf(
        "screens/tasks/TasksScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/shopping/ShoppingScreen.kt" to listOf(
            "FhLayout.listBottomPadding",
            "ShoppingScreenLayoutPolicy.listBottomPadding",
        ),
        "screens/rewards/RewardsScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/goals/FamilyGoalsScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/goals/GoalDetailScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/penalties/PenaltiesScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/appreciations/AppreciationsScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/awesomeness/AwesomenessScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/rules/RulesScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/calendar/CalendarScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/contacts/ContactsScreen.kt" to listOf("FhLayout.listBottomPadding"),
        "screens/recipes/RecipeBookScreen.kt" to listOf("RecipeBookLayoutPolicy.listBottomPadding"),
        "screens/menu/WeeklyMenuScreen.kt" to listOf("WeeklyMenuLayoutPolicy.listBottomPadding"),
        "screens/stats/MemberStatisticsScreen.kt" to listOf("MemberStatisticsLayoutTokens.listBottomPadding"),
    )

    private val determinateProgressPaths = listOf(
        "components/ScoreRow.kt",
        "screens/goals/GoalListCard.kt",
        "screens/goals/GoalDetailScreen.kt",
        "screens/shopping/ShoppingScreen.kt",
        "screens/rewards/RewardCatalogCard.kt",
    )

    private fun source(relativePath: String): String =
        File("src/main/java/com/familyhub/ui/$relativePath")
            .readText(Charsets.UTF_8)
}
