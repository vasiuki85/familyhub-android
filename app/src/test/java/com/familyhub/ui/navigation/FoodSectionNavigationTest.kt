package com.familyhub.ui.navigation

import androidx.navigation.NavOptionsBuilder
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class FoodSectionNavigationTest {

    @Test
    fun navigateFoodSectionTab_replacesCurrentFoodChildRoute() {
        val navController = mockk<androidx.navigation.NavController>(relaxed = true)

        navController.navigateFoodSectionTab(
            target = FoodSectionTab.RECIPES,
            current = FoodSectionTab.WEEKLY_MENU,
        )

        verify {
            navController.navigate(
                FoodSectionTab.RECIPES.route,
                any<NavOptionsBuilder.() -> Unit>(),
            )
        }
    }

    @Test
    fun navigateFoodSectionTab_noOpWhenAlreadySelected() {
        val navController = mockk<androidx.navigation.NavController>(relaxed = true)

        navController.navigateFoodSectionTab(
            target = FoodSectionTab.WEEKLY_MENU,
            current = FoodSectionTab.WEEKLY_MENU,
        )

        verify(exactly = 0) {
            navController.navigate(
                any<String>(),
                any<NavOptionsBuilder.() -> Unit>(),
            )
        }
    }
}
