package com.familyhub.ui.screens.home

import com.familyhub.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyOnboardingUiMapperTest {

    @Test
    fun map_unhydratedUnbound_isHidden() {
        val model = mapUnbound(isHydrated = false)

        assertFalse(model.visible)
    }

    @Test
    fun map_hydratedUnboundNotDismissed_isVisible() {
        val model = mapUnbound(isHydrated = true)

        assertTrue(model.visible)
        assertEquals(R.string.home_family_onboarding_title, model.titleResId)
        assertEquals(R.string.home_family_onboarding_message, model.messageResId)
    }

    @Test
    fun map_hydrationTransition_unboundBecomesVisible() {
        val before = mapUnbound(isHydrated = false)
        val after = mapUnbound(isHydrated = true)

        assertFalse(before.visible)
        assertTrue(after.visible)
    }

    @Test
    fun map_hydrationTransition_boundStaysHidden() {
        val before = mapBound(isHydrated = false)
        val after = mapBound(isHydrated = true)

        assertFalse(before.visible)
        assertFalse(after.visible)
    }

    @Test
    fun map_dismissed_isCompactAndVisible() {
        val model = FamilyOnboardingUiMapper.map(
            isFirebaseAuthenticated = true,
            accountId = null,
            familyOnboardingDismissed = true,
            isHydrated = true,
        )

        assertTrue(model.visible)
        assertTrue(model.compact)
        assertEquals(R.string.home_family_onboarding_compact_title, model.titleResId)
        assertEquals(R.string.home_family_onboarding_compact_message, model.messageResId)
    }

    @Test
    fun map_boundEnabled_isHidden() {
        val model = mapBound(isHydrated = true)

        assertFalse(model.visible)
    }

    @Test
    fun map_unauthenticated_isHidden() {
        val model = FamilyOnboardingUiMapper.map(
            isFirebaseAuthenticated = false,
            accountId = null,
            familyOnboardingDismissed = false,
            isHydrated = true,
        )

        assertFalse(model.visible)
    }

    private fun mapUnbound(isHydrated: Boolean) = FamilyOnboardingUiMapper.map(
        isFirebaseAuthenticated = true,
        accountId = null,
        familyOnboardingDismissed = false,
        isHydrated = isHydrated,
    )

    private fun mapBound(isHydrated: Boolean) = FamilyOnboardingUiMapper.map(
        isFirebaseAuthenticated = true,
        accountId = ACCOUNT,
        familyOnboardingDismissed = false,
        isHydrated = isHydrated,
    )

    private companion object {
        const val ACCOUNT = "family-a"
    }
}
