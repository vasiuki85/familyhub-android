package com.familyhub.util

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Heuristic fix for mojibake (UTF‑8 text misinterpreted as Windows‑1251 or ISO‑8859‑1).
 * If the transformed string appears to contain more Cyrillic characters than the
 * original, the transformed value is returned.
 */
fun String.fixMojibakeIfNeeded(): String {
    if (isBlank()) return this

    fun cyrillicScore(s: String): Int = s.count { it.code in 0x0400..0x04FF }
    val original = this

    val candidates = buildList {
        // Try CP1251 bytes -> UTF-8
        runCatching {
            add(String(original.toByteArray(Charset.forName("windows-1251")), StandardCharsets.UTF_8))
        }
        // Try ISO-8859-1 bytes -> UTF-8
        runCatching {
            add(String(original.toByteArray(Charset.forName("ISO-8859-1")), StandardCharsets.UTF_8))
        }
    }

    val best = (listOf(original) + candidates)
        .maxByOrNull { cyrillicScore(it) } ?: original

    // Avoid obviously broken results (still containing typical mojibake markers)
    return if (best.contains('Ð') || best.contains('Ã') || best.contains('Â')) original else best
}

