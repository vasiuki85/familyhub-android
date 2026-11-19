package com.familyhub.data.datastore

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class AppSettingsDataStoreSessionResetTest {

    @Test
    fun resetUserSession_clearsFamilyOnboardingDismissed() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/datastore/AppSettingsDataStore.kt",
            "src/main/java/com/familyhub/data/datastore/AppSettingsDataStore.kt",
        )

        assertTrue(source.contains("prefs.remove(KEY_FAMILY_ONBOARDING_DISMISSED)"))
    }

    private fun readSource(vararg candidates: String): String {
        val resolved = candidates
            .map { Paths.get(it) }
            .firstOrNull { Files.exists(it) }
            ?: error("Source file not found: ${candidates.joinToString()}")
        return String(Files.readAllBytes(resolved), StandardCharsets.UTF_8)
    }
}
