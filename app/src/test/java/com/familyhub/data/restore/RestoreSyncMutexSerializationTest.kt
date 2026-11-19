package com.familyhub.data.restore

import com.familyhub.data.sync.SyncExclusiveLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreSyncMutexSerializationTest {

    @Test
    fun sharedMutex_serializesRestoreAgainstInFlightSync() = runTest {
        val lock = SyncExclusiveLock()
        val syncEntered = CompletableDeferred<Unit>()
        val syncCanExit = CompletableDeferred<Unit>()
        val restoreEntered = CompletableDeferred<Unit>()

        val sync = async {
            lock.withLock {
                syncEntered.complete(Unit)
                syncCanExit.await()
            }
        }
        syncEntered.await()

        val restore = async {
            lock.withLock {
                restoreEntered.complete(Unit)
            }
        }
        yield()
        assertFalse(restoreEntered.isCompleted)

        syncCanExit.complete(Unit)
        sync.await()
        restore.await()
        assertTrue(restoreEntered.isCompleted)
    }
}
