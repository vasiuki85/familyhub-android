package com.familyhub.data.sync

import androidx.annotation.VisibleForTesting
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.FamilyHubDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@VisibleForTesting
internal val SYNC_TRACKED_TABLES: Set<String> = setOf(
    "family_members",
    "tasks",
    "shopping_lists",
    "shopping_categories",
    "shopping_items",
    "shopping_activity",
    "family_events",
    "task_completions",
    "user_achievements",
    "rewards",
    "reward_requests",
    "user_wallets",
    "task_rotations",
    "user_scores",
    "house_rules",
    "task_templates",
    "task_categories",
    "appreciations",
    "family_goals",
    "goal_stages",
    "goal_steps",
    "emergency_contacts",
    "penalties",
    "recipes",
    "recipe_ingredients",
    "recipe_steps",
    "weekly_menus",
    "menu_entries",
    "menu_templates",
    "menu_template_entries",
    "daily_assignments",
)

/**
 * Room table name → merge/localNewer key used in SyncMerger.
 * Maps DB table names to the corresponding `localNewer` collection keys
 * so that `hasPendingChangesFor(collection)` can answer whether the local
 * side should be considered "newer" for a particular collection.
 */
@VisibleForTesting
internal val TABLE_TO_COLLECTION_KEY: Map<String, String> = mapOf(
    "tasks" to "tasks",
    "task_completions" to "taskCompletions",
    "shopping_items" to "shoppingItems",
    "shopping_lists" to "shoppingLists",
    "shopping_categories" to "shoppingCategories",
    "shopping_activity" to "shoppingActivity",
    "family_events" to "events",
    "family_members" to "members",
    "user_scores" to "scores",
    "user_achievements" to "achievements",
    "rewards" to "rewards",
    "reward_requests" to "rewardRequests",
    "user_wallets" to "wallets",
    "appreciations" to "appreciations",
    "family_goals" to "familyGoals",
    "goal_stages" to "goalStages",
    "goal_steps" to "goalSteps",
    "house_rules" to "houseRules",
    "task_templates" to "taskTemplates",
    "task_categories" to "taskCategories",
    "task_rotations" to "taskRotations",
    "emergency_contacts" to "emergencyContacts",
    "penalties" to "penalties",
    "recipes" to "recipes",
    "recipe_ingredients" to "recipes",
    "recipe_steps" to "recipes",
    "weekly_menus" to "weeklyMenus",
    "menu_entries" to "menuEntries",
    "menu_templates" to "menuTemplates",
    "menu_template_entries" to "menuTemplateEntries",
    "daily_assignments" to "dailyAssignments",
)

internal data class SyncCheckpoint(
    val generation: Long,
    val pendingByCollection: Map<String, Boolean>,
    val hasPendingChanges: Boolean,
    val pendingEntityIdsByCollection: Map<String, Set<String>> = emptyMap(),
    val pendingDeletedEntityIdsByCollection: Map<String, Set<String>> = emptyMap(),
    val outboxOperations: List<PendingSyncEntityOperation> = emptyList(),
    val outboxMaxSequence: Long = 0L,
)

internal data class PendingSyncEntityOperation(
    val collectionKey: String,
    val entityId: String,
    val operation: SyncOutboxOperation,
)

internal data class CapturedSyncSnapshot<T>(
    val value: T,
    val checkpoint: SyncCheckpoint,
)

private data class PendingGenerationSnapshot(
    val generation: Long,
    val dirtyTableGenerations: Map<String, Long>,
)

private data class PendingOutboxSnapshot(
    val maxSequence: Long,
    val operations: List<PendingSyncEntityOperation>,
) {
    val entityIdsByCollection: Map<String, Set<String>> = operations
        .groupBy(PendingSyncEntityOperation::collectionKey)
        .mapValues { (_, entries) ->
            entries.mapTo(mutableSetOf(), PendingSyncEntityOperation::entityId)
        }

    val deletedEntityIdsByCollection: Map<String, Set<String>> = operations
        .filter { it.operation == SyncOutboxOperation.DELETE }
        .groupBy(PendingSyncEntityOperation::collectionKey)
        .mapValues { (_, entries) ->
            entries.mapTo(mutableSetOf(), PendingSyncEntityOperation::entityId)
        }
}

internal class LocalDataChangedDuringSyncCaptureException : IllegalStateException(
    "Local database kept changing while a stable sync snapshot was being captured"
)

@Singleton
class SyncChangeTracker private constructor(
    private val database: FamilyHubDatabase,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val schedulePendingChangesSync: (() -> Unit)?,
) {
    @Inject
    constructor(
        database: FamilyHubDatabase,
        syncSettingsDataStore: SyncSettingsDataStore,
        cloudSyncScheduler: CloudSyncScheduler,
    ) : this(database, syncSettingsDataStore, cloudSyncScheduler::schedulePendingChangesSync)

    /** Test-only construction keeps Room instrumentation independent from WorkManager. */
    constructor(
        database: FamilyHubDatabase,
        syncSettingsDataStore: SyncSettingsDataStore,
    ) : this(database, syncSettingsDataStore, null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ignoreChangesDepth = AtomicInteger(0)
    private val persistenceMutex = Mutex()
    private val stateLock = Any()
    private var currentGeneration = 0L
    private val dirtyTableGenerations = mutableMapOf<String, Long>()

    /** Global volatile flag for backward compatibility with existing merge logic. */
    @Volatile
    var hasPendingChangesVolatile: Boolean = false
        private set

    /** Per-collection pending check. Collection key matches SyncMerger naming. */
    fun hasPendingChangesFor(collectionKey: String): Boolean {
        if (!hasPendingChangesVolatile) return false
        return pendingGenerationSnapshot().dirtyTableGenerations.keys.any {
            TABLE_TO_COLLECTION_KEY[it] == collectionKey
        }
    }

    private fun pendingGenerationSnapshot(): PendingGenerationSnapshot = synchronized(stateLock) {
        PendingGenerationSnapshot(
            generation = currentGeneration,
            dirtyTableGenerations = dirtyTableGenerations.toMap(),
        )
    }

    private val observer = object : InvalidationTracker.Observer(
        SYNC_TRACKED_TABLES.toTypedArray()
    ) {
        override fun onInvalidated(tables: Set<String>) {
            if (ignoreChangesDepth.get() > 0) return

            val trackedTables = tables.filterTo(mutableSetOf()) { it in TABLE_TO_COLLECTION_KEY }
            val collectionKeys = trackedTables.mapNotNullTo(mutableSetOf()) {
                TABLE_TO_COLLECTION_KEY[it]
            }
            if (collectionKeys.isEmpty()) return

            synchronized(stateLock) {
                currentGeneration += 1L
                trackedTables.forEach { table ->
                    dirtyTableGenerations[table] = currentGeneration
                }
                hasPendingChangesVolatile = true
            }
            scope.launch {
                persistenceMutex.withLock {
                    val stillDirtyKeys = collectionKeys.filterTo(mutableSetOf()) { key ->
                        pendingGenerationSnapshot().dirtyTableGenerations.keys.any { table ->
                            TABLE_TO_COLLECTION_KEY[table] == key
                        }
                    }
                    syncSettingsDataStore.addPendingCollections(stillDirtyKeys)
                    val syncSettings = syncSettingsDataStore.settingsFlow.first()
                    if (
                        stillDirtyKeys.isNotEmpty() &&
                        syncSettings.isEnabled &&
                        !syncSettings.restoreInProgress
                    ) {
                        schedulePendingChangesSync?.invoke()
                    }
                }
            }
        }
    }

    init {
        database.invalidationTracker.addObserver(observer)
    }

    @Suppress("DEPRECATION")
    private fun refreshInvalidationsSynchronously() {
        database.invalidationTracker.refreshVersionsSync()
    }

    private fun pendingCollectionKeys(snapshot: PendingGenerationSnapshot): Set<String> =
        snapshot.dirtyTableGenerations.keys.mapNotNullTo(mutableSetOf()) { table ->
            TABLE_TO_COLLECTION_KEY[table]
        }

    private suspend fun readOutboxSnapshot(): PendingOutboxSnapshot {
        val outbox = database.syncOutboxDao()
        val maxSequence = outbox.maxSequence() ?: 0L
        if (maxSequence == 0L) {
            return PendingOutboxSnapshot(maxSequence = 0L, operations = emptyList())
        }

        val latestByDocument = linkedMapOf<Pair<String, String>, PendingSyncEntityOperation>()
        outbox.entriesThrough(maxSequence).forEach { entry ->
            val operation = runCatching { SyncOutboxOperation.valueOf(entry.operation) }.getOrNull()
                ?: return@forEach
            latestByDocument[entry.collectionKey to entry.entityId] = PendingSyncEntityOperation(
                collectionKey = entry.collectionKey,
                entityId = entry.entityId,
                operation = operation,
            )
        }
        return PendingOutboxSnapshot(
            maxSequence = maxSequence,
            operations = latestByDocument.values.toList(),
        )
    }

    private suspend fun checkpointFrom(
        snapshot: PendingGenerationSnapshot,
        capturedOutboxSnapshot: PendingOutboxSnapshot? = null,
    ): SyncCheckpoint {
        val outboxSnapshot = capturedOutboxSnapshot ?: readOutboxSnapshot()
        val persistedKeys = syncSettingsDataStore.getPendingCollections()
        val persistedAggregate = syncSettingsDataStore.settingsFlow.first().hasPendingChanges
        val pendingKeys = pendingCollectionKeys(snapshot) +
            persistedKeys +
            outboxSnapshot.entityIdsByCollection.keys
        return SyncCheckpoint(
            generation = snapshot.generation,
            pendingByCollection = pendingKeys.associateWith { true },
            hasPendingChanges = persistedAggregate ||
                pendingKeys.isNotEmpty() ||
                outboxSnapshot.operations.isNotEmpty(),
            pendingEntityIdsByCollection = outboxSnapshot.entityIdsByCollection,
            pendingDeletedEntityIdsByCollection = outboxSnapshot.deletedEntityIdsByCollection,
            outboxOperations = outboxSnapshot.operations,
            outboxMaxSequence = outboxSnapshot.maxSequence,
        )
    }

    /**
     * Capture one transactionally consistent local export together with the exact pending
     * generation represented by it. Writes committed after this token remain pending.
     */
    internal suspend fun <T> captureSyncSnapshot(export: suspend () -> T): CapturedSyncSnapshot<T> =
        persistenceMutex.withLock {
            repeat(MAX_STABLE_CAPTURE_ATTEMPTS) {
                refreshInvalidationsSynchronously()
                val before = pendingGenerationSnapshot()
                val (value, outboxSnapshot) = database.withTransaction {
                    val exported = export()
                    exported to readOutboxSnapshot()
                }
                refreshInvalidationsSynchronously()
                val after = pendingGenerationSnapshot()
                if (after.generation == before.generation) {
                    return@withLock CapturedSyncSnapshot(
                        value = value,
                        checkpoint = checkpointFrom(after, outboxSnapshot),
                    )
                }
            }
            throw LocalDataChangedDuringSyncCaptureException()
        }

    /** Build a map of collection key → hasPending for use in SyncMerger.mergeSnapshots. */
    suspend fun buildPendingByCollection(): Map<String, Boolean> = persistenceMutex.withLock {
        refreshInvalidationsSynchronously()
        checkpointFrom(pendingGenerationSnapshot()).pendingByCollection
    }

    suspend fun hasPendingChangesIncludingPersisted(): Boolean = persistenceMutex.withLock {
        refreshInvalidationsSynchronously()
        checkpointFrom(pendingGenerationSnapshot()).hasPendingChanges
    }

    internal suspend fun markSynchronized(checkpoint: SyncCheckpoint) {
        persistenceMutex.withLock {
            if (checkpoint.outboxMaxSequence > 0L) {
                database.syncOutboxDao().deleteThrough(checkpoint.outboxMaxSequence)
            }
            val remainingOutbox = readOutboxSnapshot()
            val remainingKeys = synchronized(stateLock) {
                val iterator = dirtyTableGenerations.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().value <= checkpoint.generation) {
                        iterator.remove()
                    }
                }
                hasPendingChangesVolatile = dirtyTableGenerations.isNotEmpty() ||
                    remainingOutbox.operations.isNotEmpty()
                dirtyTableGenerations.keys.mapNotNullTo(mutableSetOf()) { table ->
                    TABLE_TO_COLLECTION_KEY[table]
                }
            } + remainingOutbox.entityIdsByCollection.keys
            syncSettingsDataStore.replacePendingCollections(remainingKeys)
        }
    }

    suspend fun markSynchronized() {
        val snapshot = pendingGenerationSnapshot()
        val outboxSnapshot = readOutboxSnapshot()
        markSynchronized(
            SyncCheckpoint(
                generation = snapshot.generation,
                pendingByCollection = emptyMap(),
                hasPendingChanges = snapshot.dirtyTableGenerations.isNotEmpty() ||
                    outboxSnapshot.operations.isNotEmpty(),
                pendingEntityIdsByCollection = outboxSnapshot.entityIdsByCollection,
                pendingDeletedEntityIdsByCollection = outboxSnapshot.deletedEntityIdsByCollection,
                outboxOperations = outboxSnapshot.operations,
                outboxMaxSequence = outboxSnapshot.maxSequence,
            )
        )
    }

    /**
     * Executes [block] inside a Room transaction while SQL outbox triggers are suppressed.
     * SQL suppression is balanced before Room commits, while the surrounding
     * [runWithoutTracking] keeps observer suppression active through the commit.
     * Use this for local-only bootstrap writes that must not participate in cloud sync.
     *
     * Does not delay or acknowledge pending changes; callers manage their own sync lifecycle.
     */
    suspend fun runInTransactionWithoutTracking(block: suspend () -> Unit) {
        runWithoutTracking {
            database.withTransaction {
                database.syncOutboxDao().beginTrackingSuppression()
                try {
                    block()
                } finally {
                    database.syncOutboxDao().endTrackingSuppression()
                }
            }
        }
    }

    fun hasPendingChanges(): Boolean = hasPendingChangesVolatile

    suspend fun runWithoutTracking(block: suspend () -> Unit) {
        if (ignoreChangesDepth.get() == 0) {
            refreshInvalidationsSynchronously()
        }
        ignoreChangesDepth.incrementAndGet()
        try {
            block()
        } finally {
            // Consume Room invalidations produced by the import while suppression is still active.
            refreshInvalidationsSynchronously()
            check(ignoreChangesDepth.decrementAndGet() >= 0) {
                "SyncChangeTracker ignore depth became negative"
            }
        }
    }

    @VisibleForTesting
    fun close() {
        database.invalidationTracker.removeObserver(observer)
        scope.cancel()
    }

    private companion object {
        const val MAX_STABLE_CAPTURE_ATTEMPTS = 5
    }
}
