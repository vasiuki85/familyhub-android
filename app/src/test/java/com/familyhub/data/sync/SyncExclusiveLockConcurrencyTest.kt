package com.familyhub.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncExclusiveLockConcurrencyTest {

    @Test
    fun exclusiveLock_serializesOverlappingOperations() = runTest {
        val lock = SyncExclusiveLock()
        val firstStarted = CompletableDeferred<Unit>()
        val firstCanFinish = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()

        val first = async {
            lock.withLock {
                firstStarted.complete(Unit)
                firstCanFinish.await()
            }
        }
        firstStarted.await()

        val second = async {
            lock.withLock {
                secondStarted.complete(Unit)
            }
        }
        yield()
        assertFalse(secondStarted.isCompleted)

        firstCanFinish.complete(Unit)
        first.await()
        second.await()
        assertTrue(secondStarted.isCompleted)
    }

    @Test
    fun signOutBarrierBlocksUntilSyncOperationCompletes() = runTest {
        val lock = SyncExclusiveLock()
        val syncEntered = CompletableDeferred<Unit>()
        val syncCanExit = CompletableDeferred<Unit>()
        val wipeEntered = CompletableDeferred<Unit>()

        val sync = async {
            lock.withLock {
                syncEntered.complete(Unit)
                syncCanExit.await()
            }
        }
        syncEntered.await()

        val wipe = async {
            lock.withLock {
                wipeEntered.complete(Unit)
            }
        }
        yield()
        assertFalse(wipeEntered.isCompleted)

        syncCanExit.complete(Unit)
        sync.await()
        wipe.await()
        assertTrue(wipeEntered.isCompleted)
    }
}
