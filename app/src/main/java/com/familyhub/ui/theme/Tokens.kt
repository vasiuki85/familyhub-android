package com.familyhub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FhLayout {
    /**
     * Reserve space above a screen FAB so the last list item stays tappable.
     * Covers the 56 dp FAB plus its 16 dp scaffold margin, with a little extra air.
     */
    val fabListBottomPadding = 88.dp

    /**
     * Bottom [androidx.compose.foundation.layout.PaddingValues] for scrollable lists inside
     * the app [androidx.compose.material3.Scaffold] content slot. [MainActivity][com.familyhub.ui.MainActivity]
     * already applies [androidx.compose.foundation.layout.padding] with the scaffold
     * [innerPadding][androidx.compose.material3.Scaffold], so content ends above the bottom
     * navigation bar. This token only adds FAB clearance when needed and modest breathing room.
     */
    fun listBottomPadding(hasFloatingActionButton: Boolean): Dp =
        if (hasFloatingActionButton) fabListBottomPadding else FhSpacing.lg
}

object FhSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object FhRadius {
    val card = 18.dp
    val button = 16.dp
    val field = 12.dp
    val chip = 12.dp
    val sheet = 24.dp
    val tile = 14.dp
}

@Immutable
data class FhModulePalette(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

@Immutable
data class FhModuleColors(
    val task: FhModulePalette,
    val shopping: FhModulePalette,
    val food: FhModulePalette,
    val reward: FhModulePalette,
    val goals: FhModulePalette,
)

internal val LightFhModuleColors = FhModuleColors(
    task = FhModulePalette(
        accent = Color(0xFF2F6B3C),
        onAccent = Color.White,
        container = Color(0xFFD4EAD6),
        onContainer = Color(0xFF17361E),
    ),
    shopping = FhModulePalette(
        accent = Color(0xFF315F93),
        onAccent = Color.White,
        container = Color(0xFFD7E7FF),
        onContainer = Color(0xFF102E50),
    ),
    food = FhModulePalette(
        accent = Color(0xFFA34716),
        onAccent = Color.White,
        container = Color(0xFFFFDBCA),
        onContainer = Color(0xFF3A1707),
    ),
    reward = FhModulePalette(
        accent = Color(0xFF76518E),
        onAccent = Color.White,
        container = Color(0xFFEEDCFA),
        onContainer = Color(0xFF321A43),
    ),
    goals = FhModulePalette(
        accent = Color(0xFF6B5B9A),
        onAccent = Color.White,
        container = Color(0xFFE8E2F4),
        onContainer = Color(0xFF2A2140),
    ),
)

internal val DarkFhModuleColors = FhModuleColors(
    task = FhModulePalette(
        accent = Color(0xFF9CD5A7),
        onAccent = Color(0xFF12351C),
        container = Color(0xFF244B2D),
        onContainer = Color(0xFFC1F2C8),
    ),
    shopping = FhModulePalette(
        accent = Color(0xFFA9C9F7),
        onAccent = Color(0xFF102E50),
        container = Color(0xFF25466B),
        onContainer = Color(0xFFD5E5FF),
    ),
    food = FhModulePalette(
        accent = Color(0xFFFFB68D),
        onAccent = Color(0xFF3A1707),
        container = Color(0xFF663113),
        onContainer = Color(0xFFFFDBCA),
    ),
    reward = FhModulePalette(
        accent = Color(0xFFDAB9EE),
        onAccent = Color(0xFF321A43),
        container = Color(0xFF503762),
        onContainer = Color(0xFFF0D9FF),
    ),
    goals = FhModulePalette(
        accent = Color(0xFFC4B5E8),
        onAccent = Color(0xFF1E1630),
        container = Color(0xFF3D3560),
        onContainer = Color(0xFFE8E2F4),
    ),
)

internal val LocalFhModuleColors = staticCompositionLocalOf { LightFhModuleColors }

object FhTheme {
    val spacing: FhSpacing get() = FhSpacing
    val radius: FhRadius get() = FhRadius

    val modules: FhModuleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFhModuleColors.current
}
