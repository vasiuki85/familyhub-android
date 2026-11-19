package com.familyhub.domain.utils

/**
 * Stable references to recipe thumbnails bundled with every FamilyHub installation.
 *
 * Tokens are safe to export and sync because all supported app versions resolve the same token
 * to the same local drawable.
 */
object BuiltInRecipeImage {
    private const val PREFIX = "familyhub-recipe://"
    private val allowedTokens = (1..30).mapTo(linkedSetOf()) { index ->
        PREFIX + index.toString().padStart(2, '0')
    }

    val tokens: List<String> = allowedTokens.toList()

    fun isToken(value: String?): Boolean = value in allowedTokens
}
