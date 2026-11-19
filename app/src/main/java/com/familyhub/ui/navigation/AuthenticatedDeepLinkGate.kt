package com.familyhub.ui.navigation

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

/**
 * Holds protected deep links until both authentication and the visible destination are fresh.
 *
 * [updateAuthenticated] must be called from the Firebase auth-state listener. Reading
 * `currentUser` before the listener's first callback is intentionally not sufficient.
 */
internal class AuthenticatedDeepLinkGate<T> {
    private var freshAuthenticated: Boolean? = null
    private var navigatorAttached: Boolean = false
    private var authenticatedDestinationVisible: Boolean = false
    private val pending = ArrayDeque<T>()

    @Synchronized
    fun offer(value: T): List<T> {
        pending.addLast(value)
        return drainIfReady()
    }

    @Synchronized
    fun updateAuthenticated(authenticated: Boolean): List<T> {
        freshAuthenticated = authenticated
        return drainIfReady()
    }

    @Synchronized
    fun updateNavigatorAttached(attached: Boolean): List<T> {
        navigatorAttached = attached
        return drainIfReady()
    }

    @Synchronized
    fun updateDestination(authenticatedDestinationVisible: Boolean): List<T> {
        this.authenticatedDestinationVisible = authenticatedDestinationVisible
        return drainIfReady()
    }

    @Synchronized
    fun pendingCount(): Int = pending.size

    private fun drainIfReady(): List<T> {
        if (
            freshAuthenticated != true ||
            !navigatorAttached ||
            !authenticatedDestinationVisible
        ) {
            return emptyList()
        }

        return buildList(pending.size) {
            while (pending.isNotEmpty()) {
                add(pending.removeFirst())
            }
        }
    }
}

internal object JoinDeepLinkPolicy {
    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val FAMILY_ID_QUERY_PARAMETER = "familyId"

    fun isSupportedJoinDeepLink(action: String?, uriText: String?): Boolean {
        if (action != ACTION_VIEW || uriText.isNullOrBlank()) return false

        val uri = runCatching { URI(uriText) }.getOrNull() ?: return false
        if (!uri.scheme.equals("familyhub", ignoreCase = true)) return false
        if (!uri.host.equals("join", ignoreCase = true)) return false

        return uri.rawQuery
            ?.split('&')
            ?.mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator < 0) return@mapNotNull null
                val name = decode(part.substring(0, separator)) ?: return@mapNotNull null
                val value = decode(part.substring(separator + 1)) ?: return@mapNotNull null
                name to value
            }
            ?.any { (name, value) ->
                name == FAMILY_ID_QUERY_PARAMETER && value.isNotBlank()
            }
            ?: false
    }

    private fun decode(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()
}
