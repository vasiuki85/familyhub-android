package com.familyhub.ui

import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityDeepLinkSourceContractTest {

    private val repoRoot: File = File(".").canonicalFile.let { directory ->
        generateSequence(directory) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
    }

    private val source: String = File(
        repoRoot,
        "app/src/main/java/com/familyhub/ui/MainActivity.kt",
    ).readText(StandardCharsets.UTF_8)

    @Test
    fun initialJoinIntent_isProtectedBeforeComposeNavigationInstallsGraph() {
        val protection = source.indexOf(
            "protectJoinDeepLinkFromAutomaticNavigation(startIntent)"
        )
        val setContent = source.indexOf("setContent {")

        assertTrue("initial join protection must exist", protection >= 0)
        assertTrue(
            "initial join protection must run before setContent/NavHost graph installation",
            protection < setContent,
        )
        assertTrue(source.contains("replaceExtras(Bundle())"))
    }

    @Test
    fun initialAndNewJoinIntents_enterAuthenticatedGate() {
        assertTrue(source.contains("offerStartupPendingDeepLinksOnce()"))
        assertTrue(source.contains("processApprovedDeepLinks(joinDeepLinkGate.offer(pending))"))

        val onNewIntentBody = source
            .substringAfter("override fun onNewIntent")
            .substringBefore("private fun protectJoinDeepLinkFromAutomaticNavigation")
        assertTrue(
            "onNewIntent join data must be protected from automatic navigation",
            onNewIntentBody.contains(
                "if (protectJoinDeepLinkFromAutomaticNavigation(capturedIntent))"
            ),
        )
        assertTrue(
            "onNewIntent join data must be offered to the auth gate",
            onNewIntentBody.contains("offerJoinDeepLink("),
        )
    }

    @Test
    fun gate_isDrivenByFreshAuthListenerAndVisibleDestination() {
        assertTrue(
            source.contains("joinDeepLinkGate.updateAuthenticated(authenticated)")
        )
        assertTrue(
            source.contains("currentRoute != null && currentRoute != NavRoutes.AUTH")
        )
    }

    @Test
    fun pendingInitialJoin_survivesActivityRecreationWithoutPersistingCredentials() {
        assertTrue(source.contains("KEY_PENDING_DEEP_LINK_ACTIONS"))
        assertTrue(source.contains("KEY_PENDING_DEEP_LINK_URIS"))
        assertTrue(source.contains("KEY_PENDING_DEEP_LINK_INITIAL_FLAGS"))
        assertTrue(source.contains("restorePendingJoinDeepLinks(savedInstanceState)"))
        assertTrue(source.contains("pendingDeepLinksForRecreation.remove(pending)"))
    }
}
