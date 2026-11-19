package com.familyhub.ui.screens.food

import com.familyhub.domain.model.MealType
import com.familyhub.ui.components.FhIcons
import org.junit.Assert.assertEquals
import org.junit.Test

class MealTypeIconMapperTest {

    @Test
    fun iconFor_mapsEveryMealTypeToFhIconsFacade() {
        val expected = mapOf(
            MealType.BREAKFAST to FhIcons.MealBreakfast,
            MealType.LUNCH to FhIcons.MealLunch,
            MealType.DINNER to FhIcons.MealDinner,
            MealType.SNACK to FhIcons.MealSnack,
        )

        assertEquals(MealType.entries.toSet(), expected.keys)
        expected.forEach { (mealType, icon) ->
            assertEquals(icon, MealTypeIconMapper.iconFor(mealType))
        }
    }
}
