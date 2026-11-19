package com.familyhub.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleColorContrastTest {

    @Test
    fun lightAndDarkModulePalettes_meetTextContrast() {
        listOf(LightFhModuleColors, DarkFhModuleColors).forEach { colors ->
            listOf(colors.task, colors.shopping, colors.food, colors.reward, colors.goals).forEach { palette ->
                assertContrastAtLeast(
                    foreground = palette.onAccent,
                    background = palette.accent,
                    minimum = 4.5f,
                )
                assertContrastAtLeast(
                    foreground = palette.onContainer,
                    background = palette.container,
                    minimum = 4.5f,
                )
            }
        }
    }

    @Test
    fun customAccentForeground_choosesHigherWcagContrast() {
        listOf(
            Color(0xFFFF0000),
            Color(0xFF00A000),
            Color(0xFF0000FF),
            Color(0xFF777777),
            Color(0xFFFFC107),
        ).forEach { background ->
            val selected = background.contrastingForeground()
            val selectedContrast = contrast(selected, background)
            val alternativeContrast = contrast(
                if (selected == Color.Black) Color.White else Color.Black,
                background,
            )

            assertTrue(
                "Expected maximum contrast for $background",
                selectedContrast >= alternativeContrast,
            )
            assertTrue(
                "Expected readable contrast for $background, was $selectedContrast",
                selectedContrast >= 4.5f,
            )
        }
    }

    private fun assertContrastAtLeast(
        foreground: Color,
        background: Color,
        minimum: Float,
    ) {
        val ratio = contrast(foreground, background)

        assertTrue("Expected contrast >= $minimum, was $ratio", ratio >= minimum)
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
