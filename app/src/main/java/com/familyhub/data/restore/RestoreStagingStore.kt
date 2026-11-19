package com.familyhub.data.restore

import android.content.Context
import android.net.Uri
import com.familyhub.data.backup.EncryptedBackupFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Copies a SAF-selected backup into a dedicated app-private staging file for restore.
 */
@Singleton
class RestoreStagingStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun stageFromUri(uri: Uri): File {
        val stagingDir = stagingDir().apply { mkdirs() }
        val target = File(stagingDir, "$STAGING_PREFIX${UUID.randomUUID()}.$FHB_EXT")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > EncryptedBackupFormat.MAX_FILE_BYTES) {
                            throw RestoreStagingException(RestoreStagingFailure.SIZE_EXCEEDED)
                        }
                        output.write(buffer, 0, read)
                    }
                    if (total <= 0L) {
                        throw RestoreStagingException(RestoreStagingFailure.INVALID_FILE)
                    }
                }
            } ?: throw RestoreStagingException(RestoreStagingFailure.READ_FAILED)
            validateStagedFile(target)
            return target
        } catch (error: CancellationException) {
            deleteStagedFile(target)
            throw error
        } catch (error: RestoreStagingException) {
            deleteStagedFile(target)
            throw error
        } catch (error: IOException) {
            deleteStagedFile(target)
            val message = error.message.orEmpty()
            if (message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("No space", ignoreCase = true)
            ) {
                throw RestoreStagingException(RestoreStagingFailure.NO_SPACE, error)
            }
            throw RestoreStagingException(RestoreStagingFailure.READ_FAILED, error)
        } catch (error: SecurityException) {
            deleteStagedFile(target)
            throw RestoreStagingException(RestoreStagingFailure.READ_FAILED, error)
        } catch (error: Exception) {
            deleteStagedFile(target)
            throw RestoreStagingException(RestoreStagingFailure.READ_FAILED, error)
        }
    }

    fun validateStagedPath(path: String?): File? {
        if (path.isNullOrBlank()) return null
        return try {
            validateStagedFile(File(path))
        } catch (_: Exception) {
            null
        }
    }

    fun deleteStagedFile(file: File?) {
        if (file == null) return
        runCatching {
            val canonical = file.canonicalFile
            if (!isSafeStagedPath(canonical)) return
            if (canonical.isFile) {
                canonical.delete()
            }
        }
    }

    fun deleteStagedPath(path: String?) {
        if (path.isNullOrBlank()) return
        deleteStagedFile(File(path))
    }

    private fun validateStagedFile(file: File): File {
        val canonical = file.canonicalFile
        if (!canonical.isFile) {
            throw RestoreStagingException(RestoreStagingFailure.INVALID_FILE)
        }
        if (!isSafeStagedPath(canonical)) {
            throw RestoreStagingException(RestoreStagingFailure.INVALID_FILE)
        }
        val length = canonical.length()
        if (length <= 0L) {
            throw RestoreStagingException(RestoreStagingFailure.INVALID_FILE)
        }
        if (length > EncryptedBackupFormat.MAX_FILE_BYTES) {
            throw RestoreStagingException(RestoreStagingFailure.SIZE_EXCEEDED)
        }
        return canonical
    }

    private fun isSafeStagedPath(canonical: File): Boolean {
        val root = stagingDir().canonicalFile
        val rootPath = root.path.trimEnd(File.separatorChar)
        val filePath = canonical.path
        if (!(filePath == rootPath || filePath.startsWith(rootPath + File.separator))) {
            return false
        }
        return canonical.name.startsWith(STAGING_PREFIX) &&
            canonical.extension.equals(FHB_EXT, ignoreCase = true)
    }

    private fun stagingDir(): File = File(context.cacheDir, STAGING_SUBDIR)

    companion object {
        const val STAGING_SUBDIR = "restore_staging"
        const val STAGING_PREFIX = "familyhub_restore_"
        private const val FHB_EXT = EncryptedBackupFormat.FHB_EXTENSION
        private const val DEFAULT_BUFFER = 64 * 1024
    }
}

enum class RestoreStagingFailure {
    SIZE_EXCEEDED,
    READ_FAILED,
    NO_SPACE,
    INVALID_FILE
}

class RestoreStagingException(
    val failure: RestoreStagingFailure,
    cause: Throwable? = null
) : Exception(failure.name, cause)
