package com.familyhub.ui.screens.contacts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactCardSourceContractTest {

    @Test
    fun contactCard_usesSingleRootSurfaceWithoutNestedCard() {
        val source = source("ContactCard.kt")

        assertTrue(source.contains("shadowElevation = 0.dp"))
        assertTrue(source.contains(CONTACT_CARD_SURFACE_CD))
        assertFalse(source.contains("ThemedCard("))
        assertFalse(source.contains("material3.Card"))
        assertFalse(source.contains("CardDefaults"))
    }

    private fun source(fileName: String): String =
        File("src/main/java/com/familyhub/ui/screens/contacts/$fileName")
            .readText(Charsets.UTF_8)
}
