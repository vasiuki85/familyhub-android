package com.familyhub.ui.screens.menu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MenuEntry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeeklyMenuMealRowLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun denseMealRow_meetsHeightBudgetAtPhoneWidthWithLargeFontScale() {
        val entry = sampleEntry(
            customDish = "Суп с фрикадельками и домашней лапшой для всей семьи",
            servings = 4,
        )
        var rowHeightPx = 0

        composeRule.setContent {
            LargeFontScale {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    WeeklyMenuMealRow(
                        mealType = MealType.DINNER,
                        entry = entry,
                        recipe = null,
                        canManageMenu = true,
                        readOnlyEditCd = "read-only",
                        onOpen = {},
                        onOpenRecipe = {},
                        onReplace = {},
                        onAssign = {},
                        onRemove = {},
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            rowHeightPx = coordinates.size.height
                        },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = rowHeightPx / composeRule.density.density
        assertTrue(
            "Meal row height ${heightDp}dp exceeds budget ${WEEKLY_MENU_MEAL_ROW_MAX_HEIGHT_DP}dp",
            heightDp <= WEEKLY_MENU_MEAL_ROW_MAX_HEIGHT_DP,
        )
    }

    @Test
    fun fourMealRows_fitWithinCombinedHeightBudget() {
        val entry = sampleEntry(
            customDish = "Курица под сыром с помидорами и зеленью",
            servings = 4,
        )
        var totalHeightPx = 0

        composeRule.setContent {
            LargeFontScale {
                Column(
                    modifier = Modifier
                        .width(PHONE_WIDTH_DP.dp)
                        .onGloballyPositioned { coordinates ->
                            totalHeightPx = coordinates.size.height
                        },
                ) {
                    MealType.entries.forEach { mealType ->
                        WeeklyMenuMealRow(
                            mealType = mealType,
                            entry = entry,
                            recipe = null,
                            canManageMenu = true,
                            readOnlyEditCd = "read-only",
                            onOpen = {},
                            onOpenRecipe = {},
                            onReplace = {},
                            onAssign = {},
                            onRemove = {},
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = totalHeightPx / composeRule.density.density
        assertTrue(
            "Four meal rows height ${heightDp}dp exceeds budget ${WEEKLY_MENU_FOUR_MEALS_MAX_HEIGHT_DP}dp",
            heightDp <= WEEKLY_MENU_FOUR_MEALS_MAX_HEIGHT_DP,
        )
    }

    @Composable
    private fun LargeFontScale(content: @Composable () -> Unit) {
        val density = Density(
            density = LocalDensity.current.density,
            fontScale = 1.5f,
        )
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDensity provides density,
            content = content,
        )
    }

    private fun sampleEntry(
        customDish: String,
        servings: Int,
    ): MenuEntry = MenuEntry(
        id = "entry-1",
        menuId = "menu-1",
        dayOfWeek = 0,
        mealType = MealType.DINNER,
        recipeId = null,
        customDish = customDish,
        servings = servings,
        assignedMemberId = null,
        cookingTaskId = null,
        prepTaskId = null,
    )

    private companion object {
        const val PHONE_WIDTH_DP = 411
    }
}
