package com.familyhub.ui.screens.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsVisualContractTest {

    @Test
    fun syncTechnicalIdentifiers_areCollapsedByDefault() {
        val source = File(
            "src/main/java/com/familyhub/ui/screens/settings/SettingsScreen.kt"
        ).readText(Charsets.UTF_8)

        assertTrue(source.contains("showSyncTechnicalDetails by"))
        assertTrue(source.contains("AnimatedVisibility(visible = showSyncTechnicalDetails)"))
        assertTrue(source.contains("R.string.settings_sync_details_show"))
        assertTrue(source.contains("R.string.settings_sync_details_hide"))
    }

    @Test
    fun syncPrimaryActions_stackAtPhoneWidth() {
        val source = File(
            "src/main/java/com/familyhub/ui/screens/settings/SettingsScreen.kt"
        ).readText(Charsets.UTF_8)
        val syncActions = source.substring(
            source.indexOf("if (state.syncState.hasPendingChanges)"),
            source.indexOf("TextButton(\n                                onClick = { viewModel.disableSync() }"),
        )

        assertTrue(syncActions.contains("Column("))
        assertTrue(syncActions.contains("modifier = Modifier.fillMaxWidth()"))
    }
}
