package com.familyhub.data.menu.cloud

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.sync.SYNC_TRACKED_TABLES
import com.familyhub.data.datastore.DeletedCloudDocumentCollections.REMOTE_ACTIVE_COLLECTIONS
import com.familyhub.data.datastore.DeletedCloudDocumentCollections.STORAGE_ALLOWLIST
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.MenuSyncIds
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuCloudSourceContractTest {

    private val repoRoot: File = File(".").canonicalFile.let { dir ->
        generateSequence(dir) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
    }

    private val engineScope = "app/src/main/java/com/familyhub/data/menu/cloud"
    private val firestoreScope = "app/src/main/java/com/familyhub/data/menu/firestore"
    private val forbiddenImports = listOf(
        "androidx.room",
        "com.google.firebase",
        "androidx.datastore",
    )

    @Test
    fun menuFirestoreCodec_doesNotImportRoomFirebaseOrDataStore() {
        val dir = Path.of(repoRoot.path, firestoreScope)
        val offenders = Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { path ->
                val text = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                forbiddenImports.filter { text.contains("import $it") }
                    .map { importPrefix ->
                        "${path.toString().removePrefix(repoRoot.path + File.separator)} imports $importPrefix"
                    }
            }
            .flatMap { it.stream() }
            .toList()
        assertTrue("Firestore codec must stay pure: $offenders", offenders.isEmpty())
    }

    @Test
    fun firestoreBackendApi_doesNotReferenceMenuCloudUploadPlanner() {
        assertNoReference(
            "app/src/main/java/com/familyhub/data/sync/FirestoreBackendApi.kt",
            "MenuCloudUploadPlanner",
        )
    }

    @Test
    fun firestoreBackendApi_doesNotReferenceMenuFirestoreDocumentCodec() {
        assertNoReference(
            "app/src/main/java/com/familyhub/data/sync/FirestoreBackendApi.kt",
            "MenuFirestoreDocumentCodec",
        )
    }

    @Test
    fun syncRepository_doesNotReferenceMenuCloudUploadPlanner() {
        assertNoReference(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "MenuCloudUploadPlanner",
        )
    }

    @Test
    fun syncMerger_doesNotReferenceMenuFirestoreDocumentCodec() {
        assertNoReference(
            "app/src/main/java/com/familyhub/data/sync/logic/SyncMerger.kt",
            "MenuFirestoreDocumentCodec",
        )
    }

    @Test
    fun menuFirestoreScopeFiles_useUtf8LfWithoutBom() {
        val dir = Path.of(repoRoot.path, firestoreScope)
        val paths = Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .toList()
        assertTrue(paths.isNotEmpty())
        paths.forEach { path ->
            val relative = path.toString().removePrefix(repoRoot.path + File.separator)
            val bytes = Files.readAllBytes(path)
            assertFalse("$relative must not start with UTF-8 BOM", bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            assertFalse("$relative must not contain CR", bytes.contains(0x0D))
        }
    }

    @Test
    fun menuCloudEngine_doesNotImportRoomFirebaseOrDataStore() {
        val dir = Path.of(repoRoot.path, engineScope)
        val offenders = Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { path ->
                val text = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                forbiddenImports.filter { text.contains("import $it") }
                    .map { importPrefix ->
                        "${path.toString().removePrefix(repoRoot.path + File.separator)} imports $importPrefix"
                    }
            }
            .flatMap { it.stream() }
            .toList()
        assertTrue("Engine must stay pure: $offenders", offenders.isEmpty())
    }

    @Test
    fun syncMerger_doesNotReferenceMenuCloudMergeEngine() {
        assertNoReference(
            "app/src/main/java/com/familyhub/data/sync/logic/SyncMerger.kt",
            "MenuCloudMergeEngine",
        )
    }

    @Test
    fun firestoreBackendApi_doesNotReferenceMenuCloudMergeEngine() {
        assertNoReference(
            "app/src/main/java/com/familyhub/data/sync/FirestoreBackendApi.kt",
            "MenuCloudMergeEngine",
        )
    }

    @Test
    fun menuCloudWriteCapability_doesNotReferenceMemberWriteAllowed() {
        val text = String(
            Files.readAllBytes(Path.of(repoRoot.path, "$engineScope/MenuCloudWriteCapability.kt")),
            StandardCharsets.UTF_8,
        )
        assertFalse("capability must not depend on isMemberWriteAllowed", "isMemberWriteAllowed" in text)
    }

    @Test
    fun buildUploadPlan_doesNotReferenceMenuCloudMergeEngine() {
        assertNoReference(
            "app/src/main/java/com/familyhub/data/sync/FirestoreBackendApi.kt",
            "MenuCloudMergeEngine",
        )
    }

    @Test
    fun menuCollections_areRemoteActiveAndTracked() {
        val menuCollections = DeletedCloudDocumentCollections.MENU_COLLECTIONS
        assertEquals(5, menuCollections.size)
        menuCollections.forEach { collection ->
            assertTrue(collection in REMOTE_ACTIVE_COLLECTIONS)
            assertTrue(collection in STORAGE_ALLOWLIST)
            assertTrue(collection in SYNC_TRACKED_TABLES)
            assertFalse(collection in DeletedCloudDocumentCollections.PRE_MERGE_CONFIRMABLE_COLLECTIONS)
        }
        assertEquals(20, REMOTE_ACTIVE_COLLECTIONS.size)
        assertEquals(20, STORAGE_ALLOWLIST.size)
        assertEquals(15, DeletedCloudDocumentCollections.PRE_MERGE_CONFIRMABLE_COLLECTIONS.size)
        assertEquals(31, SYNC_TRACKED_TABLES.size)
    }

    @Test
    fun menuSyncScopeFiles_useUtf8LfWithoutBom() {
        val syncScope = "app/src/main/java/com/familyhub/data/sync"
        val syncFiles = listOf(
            "MenuCloudSyncSupport.kt",
            "MenuCloudUploadPlanAssembler.kt",
            "MenuFirestoreSnapshotDownload.kt",
            "MenuTombstoneRemotePresence.kt",
        )
        val testFiles = listOf(
            "app/src/test/java/com/familyhub/data/sync/SyncChangeTrackerContractTest.kt",
            "app/src/test/java/com/familyhub/data/sync/MenuCloudSyncSupportTest.kt",
        )
        val paths = syncFiles.map { Path.of(repoRoot.path, syncScope, it) } +
            testFiles.map { Path.of(repoRoot.path, it) }
        paths.forEach { path ->
            val relative = path.toString().removePrefix(repoRoot.path + File.separator)
            val bytes = Files.readAllBytes(path)
            assertFalse("$relative must not start with UTF-8 BOM", bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            assertFalse("$relative must not contain CR", bytes.contains(0x0D))
        }
    }

    @Test
    fun menuCloudScopeFiles_useUtf8LfWithoutBom() {
        val dir = Path.of(repoRoot.path, engineScope)
        val paths = Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .toList()
        assertTrue(paths.isNotEmpty())
        paths.forEach { path ->
            val relative = path.toString().removePrefix(repoRoot.path + File.separator)
            val bytes = Files.readAllBytes(path)
            assertFalse("$relative must not start with UTF-8 BOM", bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            assertFalse("$relative must not contain CR", bytes.contains(0x0D))
        }
    }

    private fun assertNoReference(relativePath: String, symbol: String) {
        val text = String(
            Files.readAllBytes(Path.of(repoRoot.path, relativePath)),
            StandardCharsets.UTF_8,
        )
        assertFalse("$relativePath must not reference $symbol", symbol in text)
    }
}
