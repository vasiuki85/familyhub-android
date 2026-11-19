package com.familyhub.ui.screens.contacts

import com.familyhub.domain.model.EmergencyContact
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyContactClassifierTest {

    @Test
    fun emergencyServiceKeywords_areDetected() {
        val contact = EmergencyContact(
            id = "1",
            name = "Скорая помощь",
            phoneNumber = "103",
            description = null,
            createdAt = 1L,
        )

        assertTrue(isEmergencyServiceContact(contact))
    }

    @Test
    fun personalContacts_areNotClassifiedAsEmergencyServices() {
        val contact = EmergencyContact(
            id = "2",
            name = "Бабушка Нина",
            phoneNumber = "+7 900 123-45-67",
            description = "Бабушка",
            createdAt = 1L,
        )

        assertFalse(isEmergencyServiceContact(contact))
    }
}
