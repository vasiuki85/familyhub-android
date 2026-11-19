package com.familyhub.data.sync

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositorySourceContractTest {

    private val repoRoot: File = File(".").canonicalFile.let { dir ->
        generateSequence(dir) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
    }

    @Test
    fun repositoryPassesCapturedEntityIdsToMerger() {
        val source = File(
            repoRoot,
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        ).readText(Charsets.UTF_8)

        assertTrue(
            "SyncRepository must pass the exact captured entity IDs to SyncMerger",
            source.contains(
                "pendingEntityIdsByCollection = syncCheckpoint.pendingEntityIdsByCollection"
            )
        )
    }

    @Test
    fun repositoryPassesCapturedDeletedEntityIdsToMerger() {
        val source = File(
            repoRoot,
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        ).readText(Charsets.UTF_8)

        assertTrue(
            "SyncRepository must pass the captured deleted entity IDs to SyncMerger",
            source.contains(
                "pendingDeletedEntityIdsByCollection = syncCheckpoint.pendingDeletedEntityIdsByCollection"
            )
        )
    }

    @Test
    fun repositoryPassesCapturedDeletedEntityIdsToMenuPipeline() {
        val source = File(
            repoRoot,
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        ).readText(Charsets.UTF_8)

        val menuDeleteWiring = Regex(
            """val menuTombstones = menuTombstonesFrom\(\s*deletedDocumentsForMerge,\s*syncCheckpoint\.pendingDeletedEntityIdsByCollection,\s*\)"""
        )
        assertTrue(
            "SyncRepository must pass captured outbox DELETE intents to the menu pipeline",
            menuDeleteWiring.containsMatchIn(source)
        )
    }

    @Test
    fun repositoryGuardsImportWithCapturedOutboxBoundaryBeforeAcknowledging() {
        val source = File(
            repoRoot,
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        ).readText(Charsets.UTF_8)

        val guardedImport = source.indexOf(
            "expectedOutboxMaxSequence = syncCheckpoint.outboxMaxSequence"
        )
        val acknowledge = source.indexOf(
            "syncChangeTracker.markSynchronized(syncCheckpoint)",
            startIndex = guardedImport.coerceAtLeast(0)
        )
        val lastSync = source.indexOf(
            "syncSettingsDataStore.updateLastSync",
            startIndex = guardedImport.coerceAtLeast(0)
        )

        assertTrue(
            "SyncRepository must guard import with the captured outbox boundary",
            guardedImport >= 0
        )
        assertTrue(
            "Checkpoint acknowledgement must happen only after guarded import succeeds",
            acknowledge > guardedImport
        )
        assertTrue(
            "lastSync must be updated only after guarded import succeeds",
            lastSync > guardedImport
        )
    }
}
