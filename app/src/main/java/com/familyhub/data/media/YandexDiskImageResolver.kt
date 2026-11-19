package com.familyhub.data.media

import androidx.annotation.VisibleForTesting
import com.familyhub.util.ExternalImageUrlUtils
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@VisibleForTesting
internal fun parseDownloadHref(responseBody: String): String {
    return Json.parseToJsonElement(responseBody).jsonObject.getValue("href").jsonPrimitive.content
}

/**
 * Resolves Yandex Disk public share links to a temporary direct image URL via the public API.
 * The original share link should be stored in the database; this resolver is for display only.
 */
object YandexDiskImageResolver {
    private const val DOWNLOAD_API =
        "https://cloud-api.yandex.net/v1/disk/public/resources/download?public_key="

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun resolveForDisplay(url: String): String = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        if (!ExternalImageUrlUtils.isYandexDiskPublicUrl(normalized)) {
            return@withContext normalized
        }

        cache[normalized]?.let { return@withContext it }

        runCatching {
            val encodedKey = URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())
            val connection = (URI("$DOWNLOAD_API$encodedKey").toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
            }
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val href = parseDownloadHref(reader.readText())
                cache[normalized] = href
                href
            }
        }.getOrElse { error ->
            Timber.w(error, "YandexDiskImageResolver: failed to resolve %s", normalized)
            normalized
        }
    }
}
