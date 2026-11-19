package com.familyhub.data.importer

/**
 * Thrown by [DataImportService] when a writer committed new data after the
 * sync checkpoint was captured, making a full-clear import unsafe.
 *
 * The caller (typically [com.familyhub.data.sync.SyncRepository]) should
 * treat this as a recoverable error — the current sync cycle is aborted,
 * [com.familyhub.data.work.CloudSyncWorker] will retry, and the
 * post-checkpoint write is preserved because the import transaction
 * rolled back before any data was cleared.
 */
class ImportRaceGuardException(
    val expectedOutboxMaxSequence: Long,
    val actualOutboxMaxSequence: Long,
) : IllegalStateException(
    "Import blocked: outbox max sequence $actualOutboxMaxSequence exceeds " +
        "expected $expectedOutboxMaxSequence. " +
        "A write was committed after the sync snapshot was captured."
)
