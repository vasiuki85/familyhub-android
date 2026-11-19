package com.familyhub.domain.utils

/**
 * Stable, sync-safe references to avatars bundled with every FamilyHub installation.
 *
 * The token is stored in the existing member photo field, so no Room migration is needed.
 */
object BundledFamilyAvatar {
    private const val PREFIX = "familyhub-avatar://"
    private val allowedTokens = (1..8).mapTo(linkedSetOf()) { index ->
        PREFIX + index.toString().padStart(2, '0')
    }

    val tokens: List<String> = allowedTokens.toList()

    fun isToken(value: String?): Boolean = value in allowedTokens
}
