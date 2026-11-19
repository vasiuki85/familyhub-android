package com.familyhub.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes sync lifecycle and encrypted restore so Room wipe/import never races upload.
 * Single shared [@Singleton] instance for [SyncRepository] and restore coordinator.
 */
@Singleton
class SyncExclusiveLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
