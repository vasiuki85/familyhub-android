package com.familyhub.data.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.URL
import java.security.MessageDigest

object RemoteCache {
    private fun hashName(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun guessExt(url: String): String {
        val q = url.substringAfterLast('/').substringBefore('?')
        val dot = q.lastIndexOf('.')
        return if (dot != -1 && dot < q.length - 1) q.substring(dot) else ""
    }

    private fun safeFileName(url: String, preferredName: String?): String {
        // Always fall back to hashed name to avoid slashes/query in filenames
        val ext = guessExt(url)
        val base = preferredName
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[\\\\/?:*\"<>|]"), "_")
            ?.takeIf { !it.contains('/') && !it.contains('\\') }
        return (base ?: hashName(url)) + ext
    }

    fun cachedFilePath(context: Context, url: String, subdir: String, preferredName: String? = null): String? {
        val dir = File(context.cacheDir, subdir)
        val name = safeFileName(url, preferredName)
        val file = File(dir, name)
        return if (file.exists()) file.absolutePath else null
    }

    suspend fun cacheFileIfHttp(
        context: Context,
        url: String?,
        subdir: String,
        preferredName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        val lower = url.lowercase()
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) return@withContext null

        val dir = File(context.cacheDir, subdir)
        if (!dir.exists()) dir.mkdirs()
        val name = safeFileName(url, preferredName)
        val file = File(dir, name)
        if (file.exists()) return@withContext file.absolutePath

        runCatching {
            URL(url).openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Timber.d("RemoteCache: cached %s -> %s", url, file.absolutePath)
            file.absolutePath
        }.onFailure { Timber.w(it, "RemoteCache: failed to cache %s", url) }.getOrNull()
    }

    fun resolveLocalOrRemote(context: Context, url: String?, subdir: String, preferredName: String? = null): String? {
        if (url.isNullOrBlank()) return null
        val cached = cachedFilePath(context, url, subdir, preferredName)
        return cached ?: url
    }
}


