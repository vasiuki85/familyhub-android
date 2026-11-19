package com.familyhub.ui.screens.contacts

import com.familyhub.domain.model.EmergencyContact

private val EMERGENCY_SERVICE_KEYWORDS = listOf(
    "112",
    "911",
    "101",
    "102",
    "103",
    "104",
    "ambulance",
    "police",
    "fire",
    "emergency",
    "скорая",
    "полици",
    "пожарн",
    "мчс",
    "служб",
    "rescue",
    "hotline",
)

fun isEmergencyServiceContact(contact: EmergencyContact): Boolean {
    val combined = buildString {
        append(contact.name)
        contact.description?.let {
            append(' ')
            append(it)
        }
        append(' ')
        append(contact.phoneNumber)
    }.lowercase()
    return EMERGENCY_SERVICE_KEYWORDS.any { keyword -> combined.contains(keyword) }
}
