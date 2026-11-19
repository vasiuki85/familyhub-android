package com.familyhub.util

import com.familyhub.data.sync.SyncExportSanitizer
import java.net.URI

object ExternalImageUrlUtils {
    fun normalizeInput(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed
    }

    fun isLocalContentUri(url: String): Boolean {
        return url.startsWith("content://", ignoreCase = true)
    }

    fun isSyncableUrl(url: String): Boolean {
        return SyncExportSanitizer.isRemoteUrl(url) || isYandexDiskPublicUrl(url)
    }

    fun isYandexDiskPublicUrl(url: String): Boolean {
        if (!SyncExportSanitizer.isRemoteUrl(url)) return false
        val host = runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        return host == "disk.yandex.ru" || host.endsWith(".disk.yandex.ru") || host == "yadi.sk"
    }

    /**
     * Returns a storable photo URL: https/http share links (including Yandex) or null for invalid input.
     * Local content URIs are returned unchanged.
     */
    fun normalizeForStorage(raw: String?): String? {
        val normalized = normalizeInput(raw) ?: return null
        if (isLocalContentUri(normalized)) return normalized
        return if (isSyncableUrl(normalized)) normalized else null
    }
}
