package com.familyhub.ui.screens.food

import androidx.compose.ui.graphics.vector.ImageVector
import com.familyhub.domain.model.MealType
import com.familyhub.ui.components.FhIcons

internal object MealTypeIconMapper {
    fun iconFor(mealType: MealType): ImageVector = when (mealType) {
        MealType.BREAKFAST -> FhIcons.MealBreakfast
        MealType.LUNCH -> FhIcons.MealLunch
        MealType.DINNER -> FhIcons.MealDinner
        MealType.SNACK -> FhIcons.MealSnack
    }
}
