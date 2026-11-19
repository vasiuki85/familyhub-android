package com.familyhub.data.restore

import android.content.Context
import android.net.Uri
import com.familyhub.data.backup.EncryptedBackupFormat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.io.path.createTempDirectory
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestoreStagingStoreTest {

    private lateinit var root: File
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var store: RestoreStagingStore

    @Before
    fun setUp() {
        root = createTempDirectory(prefix = "fh_stage_").toFile()
        cacheDir = File(root, "cache").apply { mkdirs() }
        context = mockk()
        every { context.cacheDir } returns cacheDir
        store = RestoreStagingStore(context)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun stageFromUri_exactMaxAccepted() = runBlocking {
        val uri = mockUri(SizedZeroInputStream(EncryptedBackupFormat.MAX_FILE_BYTES))
        val staged = store.stageFromUri(uri)
        assertEquals(EncryptedBackupFormat.MAX_FILE_BYTES, staged.length())
        assertTrue(staged.name.startsWith(RestoreStagingStore.STAGING_PREFIX))
        assertTrue(staged.extension.equals("fhb", ignoreCase = true))
        store.deleteStagedFile(staged)
        assertTrue(!staged.exists())
    }

    @Test
    fun stageFromUri_overMaxRejectedAndPartialDeleted() = runBlocking {
        val uri = mockUri(SizedZeroInputStream(EncryptedBackupFormat.MAX_FILE_BYTES + 1))
        assertThrows(RestoreStagingException::class.java) {
            runBlocking { store.stageFromUri(uri) }
        }
        assertNoStagingLeftovers()
    }

    @Test
    fun stageFromUri_readFailureDeletesPartial() = runBlocking {
        val uri = mockk<Uri>()
        every { context.contentResolver.openInputStream(uri) } throws FileNotFoundException("gone")
        assertThrows(RestoreStagingException::class.java) {
            runBlocking { store.stageFromUri(uri) }
        }
        assertNoStagingLeftovers()
    }

    @Test
    fun stageFromUri_coroutineCancellation_deletesPartial() = runBlocking {
        val uri = mockUri(
            SlowChunkedInputStream(
                totalBytes = 512 * 1024,
                chunkSize = 8 * 1024,
                pauseMs = 5
            )
        )
        val job = launch(Dispatchers.Default) { store.stageFromUri(uri) }
        repeat(20) {
            if (hasStagingPartial()) return@repeat
            delay(5)
        }
        job.cancelAndJoin()
        assertNoStagingLeftovers()
    }

    @Test
    fun validateStagedPath_rejectsMissingOrUnsafe() {
        assertNull(store.validateStagedPath(null))
        assertNull(store.validateStagedPath(File(cacheDir, "evil.fhb").absolutePath))
        val safe = File(
            File(cacheDir, RestoreStagingStore.STAGING_SUBDIR).apply { mkdirs() },
            "${RestoreStagingStore.STAGING_PREFIX}ok.fhb"
        ).apply { writeBytes(ByteArray(80) { 1 }) }
        assertEquals(safe.canonicalFile, store.validateStagedPath(safe.absolutePath))
    }

    private fun assertNoStagingLeftovers() {
        val stagingDir = File(cacheDir, RestoreStagingStore.STAGING_SUBDIR)
        val leftovers = stagingDir.listFiles()?.filter {
            it.name.startsWith(RestoreStagingStore.STAGING_PREFIX)
        }.orEmpty()
        assertTrue(leftovers.isEmpty())
    }

    private fun hasStagingPartial(): Boolean {
        val stagingDir = File(cacheDir, RestoreStagingStore.STAGING_SUBDIR)
        return stagingDir.listFiles()?.any {
            it.name.startsWith(RestoreStagingStore.STAGING_PREFIX)
        } == true
    }

    private fun mockUri(stream: InputStream): Uri {
        val uri = mockk<Uri>()
        every { context.contentResolver } returns mockk {
            every { openInputStream(uri) } returns stream
        }
        return uri
    }

    /** Streams [total] zero bytes without allocating the full payload. */
    private class SizedZeroInputStream(private val total: Long) : InputStream() {
        private var remaining = total

        override fun read(): Int {
            if (remaining <= 0L) return -1
            remaining--
            return 0
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val n = min(len.toLong(), remaining).toInt()
            remaining -= n
            return n
        }
    }

    private class SlowChunkedInputStream(
        private val totalBytes: Long,
        private val chunkSize: Int,
        private val pauseMs: Long
    ) : InputStream() {
        private var remaining = totalBytes

        override fun read(): Int {
            if (remaining <= 0L) return -1
            Thread.sleep(pauseMs)
            remaining--
            return 0
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            Thread.sleep(pauseMs)
            val n = min(min(len, chunkSize).toLong(), remaining).toInt()
            remaining -= n
            return n
        }
    }
}
