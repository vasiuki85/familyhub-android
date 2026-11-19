package com.familyhub.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedDeepLinkGateTest {

    @Test
    fun unauthenticatedJoin_isHeldUntilFreshAuthAndAuthenticatedDestination() {
        val gate = AuthenticatedDeepLinkGate<String>()

        assertEquals(emptyList<String>(), gate.offer("join-a"))
        assertEquals(emptyList<String>(), gate.updateNavigatorAttached(true))
        assertEquals(emptyList<String>(), gate.updateDestination(false))
        assertEquals(emptyList<String>(), gate.updateAuthenticated(false))
        assertEquals(emptyList<String>(), gate.updateAuthenticated(true))

        assertEquals(listOf("join-a"), gate.updateDestination(true))
        assertEquals(0, gate.pendingCount())
    }

    @Test
    fun initialAuthenticatedJoin_waitsForFreshListenerCallback() {
        val gate = AuthenticatedDeepLinkGate<String>()

        assertEquals(emptyList<String>(), gate.updateNavigatorAttached(true))
        assertEquals(emptyList<String>(), gate.updateDestination(true))
        assertEquals(emptyList<String>(), gate.offer("join-a"))

        assertEquals(listOf("join-a"), gate.updateAuthenticated(true))
    }

    @Test
    fun multipleIntents_areConsumedOnceInArrivalOrder() {
        val gate = AuthenticatedDeepLinkGate<String>()

        gate.updateNavigatorAttached(true)
        gate.updateDestination(false)
        gate.updateAuthenticated(false)
        gate.offer("join-a")
        gate.offer("join-b")

        assertEquals(emptyList<String>(), gate.updateAuthenticated(true))
        assertEquals(listOf("join-a", "join-b"), gate.updateDestination(true))
        assertEquals(emptyList<String>(), gate.updateDestination(true))
        assertEquals(emptyList<String>(), gate.updateAuthenticated(true))
    }

    @Test
    fun logout_holdsSubsequentIntentUntilNextAuthenticatedDestination() {
        val gate = AuthenticatedDeepLinkGate<String>()

        gate.updateNavigatorAttached(true)
        gate.updateDestination(true)
        gate.updateAuthenticated(true)
        assertEquals(listOf("first"), gate.offer("first"))

        gate.updateAuthenticated(false)
        gate.updateDestination(false)
        assertEquals(emptyList<String>(), gate.offer("second"))
        assertEquals(emptyList<String>(), gate.updateAuthenticated(true))
        assertEquals(listOf("second"), gate.updateDestination(true))
    }

    @Test
    fun detachedNavigator_neverConsumesPendingIntent() {
        val gate = AuthenticatedDeepLinkGate<String>()

        gate.updateAuthenticated(true)
        gate.updateDestination(true)
        assertEquals(emptyList<String>(), gate.offer("join-a"))
        assertEquals(listOf("join-a"), gate.updateNavigatorAttached(true))

        gate.updateNavigatorAttached(false)
        assertEquals(emptyList<String>(), gate.offer("join-b"))
        assertEquals(listOf("join-b"), gate.updateNavigatorAttached(true))
    }

    @Test
    fun joinPolicy_acceptsOnlyActionViewFamilyHubJoinWithFamilyId() {
        assertTrue(
            JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                action = "android.intent.action.VIEW",
                uriText = "familyhub://join?familyId=family-1",
            )
        )
        assertTrue(
            JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                action = "android.intent.action.VIEW",
                uriText = "FAMILYHUB://JOIN?other=x&familyId=family%202",
            )
        )

        assertFalse(
            JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                action = null,
                uriText = "familyhub://join?familyId=family-1",
            )
        )
        assertFalse(
            JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                action = "android.intent.action.VIEW",
                uriText = "https://join?familyId=family-1",
            )
        )
        assertFalse(
            JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                action = "android.intent.action.VIEW",
                uriText = "familyhub://other?familyId=family-1",
            )
        )
        assertFalse(
            JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                action = "android.intent.action.VIEW",
                uriText = "familyhub://join?familyId=",
            )
        )
        assertFalse(
            JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                action = "android.intent.action.VIEW",
                uriText = "familyhub://join?familyId=%",
            )
        )
    }
}
