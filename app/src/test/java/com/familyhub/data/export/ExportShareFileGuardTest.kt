package com.familyhub.data.export

import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExportShareFileGuardTest {

    private lateinit var rootDir: File
    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var backupsDir: File
    private var now: Long = 1_700_000_000_000L
    private lateinit var guard: ExportShareFileGuard

    @Before
    fun setUp() {
        rootDir = createTempDirectory(prefix = "fh_export_guard_").toFile()
        cacheDir = File(rootDir, "cache").apply { mkdirs() }
        filesDir = File(rootDir, "files").apply { mkdirs() }
        backupsDir = File(filesDir, "backups").apply { mkdirs() }
        now = 1_700_000_000_000L
        guard = ExportShareFileGuard(
            cacheDir = cacheDir,
            backupsDir = backupsDir,
            clock = { now }
        )
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun validateShareable_allowsCacheJsonAndCsv() {
        val json = File(cacheDir, "familyhub_export_2026-01-01.json")
        json.writeText("{}", StandardCharsets.UTF_8)
        val csv = File(cacheDir, "familyhub_export_2026-01-01.csv")
        csv.writeText("a,b", StandardCharsets.UTF_8)

        assertEquals("application/json", guard.validateShareable(json).mimeType)
        assertEquals("text/csv", guard.validateShareable(csv).mimeType)
    }

    @Test
    fun validateShareable_allowsBackupsJson() {
        val backup = File(backupsDir, "familyhub_backup_2026-01-01.json")
        backup.writeText("{}", StandardCharsets.UTF_8)

        val validated = guard.validateShareable(backup)
        assertEquals("application/json", validated.mimeType)
    }

    @Test
    fun validateShareable_rejectsArbitraryFilesDir() {
        val rogue = File(filesDir, "rogue.json")
        rogue.writeText("{}", StandardCharsets.UTF_8)

        assertThrows(SecurityException::class.java) {
            guard.validateShareable(rogue)
        }
    }

    @Test
    fun validateShareable_rejectsDatabasePath() {
        val databasesDir = File(filesDir, "databases").apply { mkdirs() }
        val dbFile = File(databasesDir, "familyhub.db")
        dbFile.writeText("sqlite", StandardCharsets.UTF_8)

        assertThrows(IllegalArgumentException::class.java) {
            guard.validateShareable(dbFile)
        }
    }

    @Test
    fun validateShareable_rejectsSharedPrefsPath() {
        val prefsDir = File(filesDir, "shared_prefs").apply { mkdirs() }
        val prefsFile = File(prefsDir, "settings.xml")
        prefsFile.writeText("<map/>", StandardCharsets.UTF_8)

        assertThrows(IllegalArgumentException::class.java) {
            guard.validateShareable(prefsFile)
        }
    }

    @Test
    fun validateShareable_rejectsUnknownExtension() {
        val txt = File(cacheDir, "familyhub_export_data.txt")
        txt.writeText("x", StandardCharsets.UTF_8)

        assertThrows(IllegalArgumentException::class.java) {
            guard.validateShareable(txt)
        }
    }

    @Test
    fun validateShareable_rejectsUnexpectedCacheFileName() {
        val other = File(cacheDir, "other_export.json")
        other.writeText("{}", StandardCharsets.UTF_8)

        assertThrows(SecurityException::class.java) {
            guard.validateShareable(other)
        }
    }

    @Test
    fun validateShareable_rejectsNonexistentPath() {
        val missing = File(cacheDir, "familyhub_export_missing.json")

        assertThrows(IllegalArgumentException::class.java) {
            guard.validateShareable(missing)
        }
    }

    @Test
    fun validateShareable_rejectsDirectoryPath() {
        val directory = File(cacheDir, "familyhub_export_dir.json")
        assertTrue(directory.mkdir())

        assertThrows(IllegalArgumentException::class.java) {
            guard.validateShareable(directory)
        }
    }

    @Test
    fun cleanupStaleExportCache_removesOnlyOldOwnExports() {
        val staleCsv = File(cacheDir, "familyhub_export_old.csv")
        staleCsv.writeText("a", StandardCharsets.UTF_8)
        staleCsv.setLastModified(now - ExportShareFileGuard.STALE_EXPORT_MAX_AGE_MS - 1_000L)

        val freshJson = File(cacheDir, "familyhub_export_fresh.json")
        freshJson.writeText("{}", StandardCharsets.UTF_8)
        freshJson.setLastModified(now - 1_000L)

        val stranger = File(cacheDir, "stranger.json")
        stranger.writeText("{}", StandardCharsets.UTF_8)
        val otherPrefix = File(cacheDir, "other_export.json")
        otherPrefix.writeText("{}", StandardCharsets.UTF_8)
        val backup = File(backupsDir, "familyhub_backup_keep.json")
        backup.writeText("{}", StandardCharsets.UTF_8)

        guard.cleanupStaleExportCache(now)

        assertFalse(staleCsv.exists())
        assertTrue(freshJson.exists())
        assertTrue(stranger.exists())
        assertTrue(otherPrefix.exists())
        assertTrue(backup.exists())
    }

    @Test
    fun cleanupStaleExportCache_doesNotDeleteFreshExportOpenedForShare() {
        val firstExport = File(cacheDir, "familyhub_export_first.json")
        firstExport.writeText("""{"version":1}""", StandardCharsets.UTF_8)
        firstExport.setLastModified(now - 1_000L)

        guard.cleanupStaleExportCache(now)

        assertTrue(firstExport.exists())
        assertEquals(
            """{"version":1}""",
            firstExport.readText(StandardCharsets.UTF_8)
        )
    }

    @Test
    fun createExportCacheFile_sameDateProducesDistinctFilesWithoutMutatingFirst() {
        val exportDate = "2026-01-01 12:00:00"
        val first = ExportTempFileFactory.createExportCacheFile(cacheDir, ExportFormat.JSON, exportDate)
        first.writeText("""{"version":1}""", StandardCharsets.UTF_8)
        val second = ExportTempFileFactory.createExportCacheFile(cacheDir, ExportFormat.JSON, exportDate)
        second.writeText("""{"version":2}""", StandardCharsets.UTF_8)

        assertNotEquals(first.path, second.path)
        assertTrue(first.name.startsWith(ExportShareFileGuard.EXPORT_CACHE_PREFIX))
        assertTrue(second.name.startsWith(ExportShareFileGuard.EXPORT_CACHE_PREFIX))
        assertEquals("""{"version":1}""", first.readText(StandardCharsets.UTF_8))
        assertEquals("""{"version":2}""", second.readText(StandardCharsets.UTF_8))
    }

    @Test
    fun createBackupFile_sameDateProducesDistinctFilesWithoutMutatingFirst() {
        val exportDate = "2026-01-01 12:00:00"
        val first = ExportTempFileFactory.createBackupFile(backupsDir, exportDate)
        first.writeText("""{"version":1}""", StandardCharsets.UTF_8)
        val second = ExportTempFileFactory.createBackupFile(backupsDir, exportDate)
        second.writeText("""{"version":2}""", StandardCharsets.UTF_8)

        assertNotEquals(first.path, second.path)
        assertTrue(first.name.startsWith(ExportShareFileGuard.BACKUP_FILE_PREFIX))
        assertTrue(second.name.startsWith(ExportShareFileGuard.BACKUP_FILE_PREFIX))
        assertEquals("""{"version":1}""", first.readText(StandardCharsets.UTF_8))
        assertEquals("""{"version":2}""", second.readText(StandardCharsets.UTF_8))
    }

    @Test
    fun utf8CyrillicRoundTripThroughBackupPath() {
        val cyrillic = "Семья Хаб — экспорт данных"
        val backup = File(backupsDir, "familyhub_backup_utf8.json")
        backup.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write("""{"members":[{"displayName":"$cyrillic"}]}""")
        }

        val readBack = backup.readText(StandardCharsets.UTF_8)
        assertTrue(readBack.contains(cyrillic))

        val validated = guard.validateShareable(backup)
        assertEquals("application/json", validated.mimeType)
    }
}
