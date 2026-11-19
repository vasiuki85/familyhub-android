package com.familyhub.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.familyhub.domain.model.ThemeMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

// ── Light palette ramp ──────────────────────────────────────────────────────

private val LightPrimary = Color(0xFF9C4020)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFFFDBCF)
private val LightOnPrimaryContainer = Color(0xFF3A0B00)

private val LightSecondary = Color(0xFF77574B)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFFFDBCF)
private val LightOnSecondaryContainer = Color(0xFF2C150C)

private val LightTertiary = Color(0xFF6A5D2F)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFF3E1A7)
private val LightOnTertiaryContainer = Color(0xFF221B00)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

private val LightBackground = Color(0xFFFFF8F6)
private val LightOnBackground = Color(0xFF231917)
private val LightSurface = Color(0xFFFFF8F6)
private val LightOnSurface = Color(0xFF231917)
private val LightSurfaceVariant = Color(0xFFF5DED6)
private val LightOnSurfaceVariant = Color(0xFF53433E)

private val LightOutline = Color(0xFF85736D)
private val LightOutlineVariant = Color(0xFFD8C2BB)
private val LightScrim = Color(0xFF000000)
private val LightSurfaceTint = Color(0xFF9C4020)

private val LightInverseSurface = Color(0xFF392E2B)
private val LightInverseOnSurface = Color(0xFFFFEDE8)
private val LightInversePrimary = Color(0xFFFFB59B)

private val LightSurfaceDim = Color(0xFFE8D6D0)
private val LightSurfaceBright = Color(0xFFFFF8F6)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFFFF1ED)
private val LightSurfaceContainer = Color(0xFFFCEAE4)
private val LightSurfaceContainerHigh = Color(0xFFF7E4DF)
private val LightSurfaceContainerHighest = Color(0xFFF1DED9)

// ── Dark palette ramp ─────────────────────────────────────────────────────────

private val DarkPrimary = Color(0xFFFFB59B)
private val DarkOnPrimary = Color(0xFF5B1B04)
private val DarkPrimaryContainer = Color(0xFF7C2C10)
private val DarkOnPrimaryContainer = Color(0xFFFFDBCF)

private val DarkSecondary = Color(0xFFE7BEAF)
private val DarkOnSecondary = Color(0xFF442A20)
private val DarkSecondaryContainer = Color(0xFF5D4035)
private val DarkOnSecondaryContainer = Color(0xFFFFDBCF)

private val DarkTertiary = Color(0xFFD7C68D)
private val DarkOnTertiary = Color(0xFF3A3005)
private val DarkTertiaryContainer = Color(0xFF52461A)
private val DarkOnTertiaryContainer = Color(0xFFF3E1A7)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val DarkBackground = Color(0xFF1A110F)
private val DarkOnBackground = Color(0xFFF1DED9)
private val DarkSurface = Color(0xFF1A110F)
private val DarkOnSurface = Color(0xFFF1DED9)
private val DarkSurfaceVariant = Color(0xFF53433E)
private val DarkOnSurfaceVariant = Color(0xFFD8C2BB)

private val DarkOutline = Color(0xFFA08D86)
private val DarkOutlineVariant = Color(0xFF53433E)
private val DarkScrim = Color(0xFF000000)
private val DarkSurfaceTint = Color(0xFFFFB59B)

private val DarkInverseSurface = Color(0xFFF1DED9)
private val DarkInverseOnSurface = Color(0xFF392E2B)
private val DarkInversePrimary = Color(0xFF9C4020)

private val DarkSurfaceDim = Color(0xFF1A110F)
private val DarkSurfaceBright = Color(0xFF423734)
private val DarkSurfaceContainerLowest = Color(0xFF140C0A)
private val DarkSurfaceContainerLow = Color(0xFF231917)
private val DarkSurfaceContainer = Color(0xFF271D1B)
private val DarkSurfaceContainerHigh = Color(0xFF322825)
private val DarkSurfaceContainerHighest = Color(0xFF3D322F)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    surfaceTint = LightSurfaceTint,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    surfaceTint = DarkSurfaceTint,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)

private val FamilyHubShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(FhRadius.field),
    medium = RoundedCornerShape(FhRadius.button),
    large = RoundedCornerShape(FhRadius.card),
    extraLarge = RoundedCornerShape(FhRadius.sheet),
)

@Composable
fun FamilyHubTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    @Suppress("UNUSED_PARAMETER")
    customJson: String? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val colorScheme = baseColorScheme
    val moduleColors = if (darkTheme) DarkFhModuleColors else LightFhModuleColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colorScheme.surface.prefersDarkForeground()
        }
    }

    CompositionLocalProvider(LocalFhModuleColors provides moduleColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FamilyHubTypography,
            shapes = FamilyHubShapes,
            content = content
        )
    }
}

private fun Color.prefersDarkForeground(): Boolean = luminance() > 0.179f

internal fun Color.contrastingForeground(): Color {
    val backgroundLuminance = luminance()
    val blackContrast = (backgroundLuminance + 0.05f) / 0.05f
    val whiteContrast = 1.05f / (backgroundLuminance + 0.05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}
