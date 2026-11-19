package com.familyhub.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FhScreenScaffoldSourceContractTest {
    @Test
    fun nestedScaffold_doesNotApplySystemInsetsTwice() {
        val source = File(
            "src/main/java/com/familyhub/ui/components/FhScreenScaffold.kt"
        ).readText(Charsets.UTF_8)

        assertTrue(source.contains("contentWindowInsets = WindowInsets(0, 0, 0, 0)"))
        assertTrue(source.contains("windowInsets = WindowInsets(0, 0, 0, 0)"))
        assertFalse(source.contains("windowInsets = TopAppBarDefaults.windowInsets"))
    }

    @Test
    fun appScaffold_consumesInsetsBeforeRenderingNestedScreens() {
        val source = File("src/main/java/com/familyhub/ui/MainActivity.kt")
            .readText(Charsets.UTF_8)

        assertTrue(source.contains(".padding(innerPadding)"))
        assertTrue(source.contains(".consumeWindowInsets(innerPadding)"))
    }

    @Test
    fun backgroundDecoration_isSubtleByDefaultAndStrongerOnHome() {
        val scaffold = File(
            "src/main/java/com/familyhub/ui/components/FhScreenScaffold.kt"
        ).readText(Charsets.UTF_8)
        val home = File("src/main/java/com/familyhub/ui/screens/home/HomeScreen.kt")
            .readText(Charsets.UTF_8)

        assertTrue(scaffold.contains("backgroundDecorationAlpha: Float = 0.04f"))
        assertTrue(scaffold.contains("backgroundDecorationAlpha.coerceIn(0f, 1f)"))
        assertTrue(home.contains("backgroundDecorationAlpha = 0.12f"))
    }
}
