package com.familyhub.ui.screens.rules

import androidx.compose.ui.graphics.vector.ImageVector
import com.familyhub.ui.components.FhIcons

internal fun ruleCategoryIcon(category: String?): ImageVector {
    return when (category?.trim()?.lowercase()) {
        "home", "дом" -> FhIcons.Home
        "kitchen", "кухня" -> FhIcons.RestaurantMenu
        "clear", "clean", "cleaning", "уборка" -> FhIcons.Lightbulb
        "safety", "безопасность" -> FhIcons.Shield
        else -> FhIcons.Shield
    }
}
