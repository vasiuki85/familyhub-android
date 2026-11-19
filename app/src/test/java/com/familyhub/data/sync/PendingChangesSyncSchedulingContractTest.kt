package com.familyhub.data.sync

import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingChangesSyncSchedulingContractTest {

    @Test
    fun pendingRoomChanges_enqueueDebouncedNetworkWork() {
        val schedulerSource = source(
            "src/main/java/com/familyhub/data/sync/CloudSyncScheduler.kt"
        )
        val trackerSource = source(
            "src/main/java/com/familyhub/data/sync/SyncChangeTracker.kt"
        )

        assertTrue(schedulerSource.contains("OneTimeWorkRequestBuilder<CloudSyncWorker>()"))
        assertTrue(schedulerSource.contains("PENDING_CHANGES_DEBOUNCE_SECONDS"))
        assertTrue(schedulerSource.contains("NetworkType.CONNECTED"))
        assertTrue(schedulerSource.contains("ExistingWorkPolicy.REPLACE"))
        assertTrue(schedulerSource.contains("PENDING_CHANGES_WORK_NAME"))

        val persistIndex = trackerSource.indexOf(
            "syncSettingsDataStore.addPendingCollections(stillDirtyKeys)"
        )
        val enabledGateIndex = trackerSource.indexOf("syncSettings.isEnabled")
        val enqueueIndex = trackerSource.indexOf("schedulePendingChangesSync?.invoke()")
        assertTrue("Pending changes must be persisted before work is enqueued", persistIndex >= 0)
        assertTrue("Disabled local-only sessions must not enqueue sync work", enabledGateIndex > persistIndex)
        assertTrue("Pending sync work must be enqueued after persistence", enqueueIndex > persistIndex)
    }

    private fun source(path: String): String =
        File(path).readText(StandardCharsets.UTF_8)
}
