package com.familyhub.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

fun parseHexColor(hex: String?): Color? {
    val raw = hex?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val sanitized = if (raw.startsWith("#")) raw else "#$raw"
    return try {
        Color(AndroidColor.parseColor(sanitized))
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun avatarColorOrFallback(hex: String?, fallback: Color = Color(0xFF2196F3)): Color {
    return parseHexColor(hex) ?: fallback
}
