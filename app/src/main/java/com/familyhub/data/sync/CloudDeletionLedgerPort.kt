package com.familyhub.data.sync

interface CloudDeletionLedgerPort {
    suspend fun recordDeletions(entries: Collection<Pair<String, String>>)
    suspend fun clearDeletions(entries: Collection<Pair<String, String>>)
}
