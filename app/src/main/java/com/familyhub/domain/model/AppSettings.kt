package com.familyhub.domain.model

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class AppSettings(
    val currentUserId: String,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeCustomJson: String? = null,
    val biometricEnabled: Boolean = false,
    val activeShoppingListId: String = "",
    val bottomNavRoutes: List<String> = emptyList(), // Сохраняемые маршруты нижней навигации
    val familyOnboardingDismissed: Boolean = false,
)
