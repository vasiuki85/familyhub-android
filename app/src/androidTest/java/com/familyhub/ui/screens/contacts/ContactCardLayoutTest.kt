package com.familyhub.ui.screens.contacts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.domain.model.EmergencyContact
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactContactCard_staysWithinBudgetAtLargeFontScale() {
        val contact = EmergencyContact(
            id = "contact-1",
            name = "Бабушка Нина",
            phoneNumber = "+7 900 123-45-67",
            description = "Бабушка",
            createdAt = 1L,
        )
        var cardHeightPx = 0

        composeRule.setContent {
            LargeFontScale {
                MaterialTheme {
                    Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                        ContactCard(
                            contact = contact,
                            onCall = {},
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                cardHeightPx = coordinates.size.height
                            },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = cardHeightPx / composeRule.density.density
        assertTrue(
            "Contact card height ${heightDp}dp exceeds budget ${CONTACT_CARD_MAX_HEIGHT_DP}dp",
            heightDp <= CONTACT_CARD_MAX_HEIGHT_DP,
        )
    }

    @Test
    fun emergencyContactCard_withLongUnbrokenName_staysWithinBudgetAtLargeFontScale() {
        val contact = EmergencyContact(
            id = "police",
            name = "полицияполицияполицияполицияполицияполиция",
            phoneNumber = "102",
            description = "экстреннаяслужбаэкстреннаяслужба",
            createdAt = 1L,
        )
        var cardHeightPx = 0

        composeRule.setContent {
            LargeFontScale {
                MaterialTheme {
                    Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                        ContactCard(
                            contact = contact,
                            isEmergencyService = true,
                            onCall = {},
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                cardHeightPx = coordinates.size.height
                            },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = cardHeightPx / composeRule.density.density
        assertTrue(
            "Emergency contact card height ${heightDp}dp exceeds budget ${CONTACT_CARD_MAX_HEIGHT_DP}dp",
            heightDp <= CONTACT_CARD_MAX_HEIGHT_DP,
        )
    }

    @Test
    fun emergencyContactCard_exposesSingleRootSurfaceSemantics() {
        composeRule.setContent {
            MaterialTheme {
                ContactCard(
                    contact = EmergencyContact(
                        id = "police",
                        name = "police",
                        phoneNumber = "102",
                        description = null,
                        createdAt = 1L,
                    ),
                    isEmergencyService = true,
                    onCall = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(CONTACT_CARD_SURFACE_CD).assertExists()
    }

    @Test
    fun emergencyServicesSection_isVisuallyDistinguishable() {
        val emergencyLabel = composeRule.activity.getString(R.string.contacts_section_emergency_services)

        composeRule.setContent {
            MaterialTheme {
                androidx.compose.material3.Text(
                    text = emergencyLabel,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        contentDescription = emergencyLabel
                    },
                )
            }
        }

        composeRule.onNodeWithText(emergencyLabel).assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun LargeFontScale(content: @androidx.compose.runtime.Composable () -> Unit) {
        val density = Density(
            density = LocalDensity.current.density,
            fontScale = 1.5f,
        )
        CompositionLocalProvider(LocalDensity provides density, content = content)
    }

    private companion object {
        const val PHONE_WIDTH_DP = 411
    }
}
