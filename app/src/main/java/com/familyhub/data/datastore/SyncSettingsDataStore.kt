package com.familyhub.data.datastore

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.familyhub.data.restore.RestoreBarrierConflictException
import com.familyhub.domain.model.SyncSettings
import com.familyhub.domain.utils.LocalDataFamilySwitchPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_settings")

class SyncSettingsDataStore @VisibleForTesting internal constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.syncDataStore)

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("sync_enabled")
        private val KEY_ACCOUNT_ID = stringPreferencesKey("sync_account_id")
        private val KEY_DEVICE_ID = stringPreferencesKey("sync_device_id")
        private val KEY_LAST_SYNC = longPreferencesKey("sync_last_sync")
        private val KEY_PENDING_CHANGES = booleanPreferencesKey("sync_pending_changes")
        private val KEY_PENDING_COLLECTIONS = stringSetPreferencesKey("sync_pending_collections")
        private val KEY_LAST_ERROR = stringPreferencesKey("sync_last_error")
        private val KEY_GOOGLE_CALENDAR_ID = stringPreferencesKey("google_calendar_id")
        private val KEY_DELETED_MEMBERS = stringSetPreferencesKey("sync_deleted_members")
        private val KEY_DELETED_RECIPES = stringSetPreferencesKey("sync_deleted_recipes")
        private val KEY_DELETED_DOCUMENTS = stringSetPreferencesKey("sync_deleted_documents")
        private val KEY_LOCAL_DATA_FAMILY_ID = stringPreferencesKey("sync_local_data_family_id")
        private val KEY_RESTORE_IN_PROGRESS = booleanPreferencesKey("sync_restore_in_progress")
        private val KEY_RESTORE_FINGERPRINT = stringPreferencesKey("sync_restore_fingerprint")
        private val KEY_RESTORE_SOURCE_FAMILY_ID =
            stringPreferencesKey("sync_restore_source_family_id")
        private val KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP =
            booleanPreferencesKey("sync_suppress_auth_member_bootstrap")
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.clearPendingState() {
        this[KEY_PENDING_CHANGES] = false
        remove(KEY_PENDING_COLLECTIONS)
    }

    val settingsFlow: Flow<SyncSettings> = dataStore.data.map { prefs ->
        SyncSettings(
            isEnabled = prefs[KEY_ENABLED] ?: false,
            accountId = prefs[KEY_ACCOUNT_ID],
            deviceId = prefs[KEY_DEVICE_ID],
            lastSyncAt = prefs[KEY_LAST_SYNC],
            hasPendingChanges = prefs[KEY_PENDING_CHANGES] ?: false,
            lastError = prefs[KEY_LAST_ERROR],
            googleCalendarId = prefs[KEY_GOOGLE_CALENDAR_ID],
            localDataFamilyId = prefs[KEY_LOCAL_DATA_FAMILY_ID],
            restoreInProgress = prefs[KEY_RESTORE_IN_PROGRESS] ?: false,
            restoreBarrierFingerprint = prefs[KEY_RESTORE_FINGERPRINT],
            restoreBarrierSourceFamilyId = prefs[KEY_RESTORE_SOURCE_FAMILY_ID],
            suppressAuthMemberBootstrap = prefs[KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP] ?: false
        )
    }

    // Backwards-compatible alias used by older call sites
    val calendarSyncSettingsFlow: Flow<SyncSettings> = settingsFlow

    suspend fun pauseSync() {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = false
            // accountId/deviceId/lastSync остаются, чтобы можно было просто возобновить
            prefs.clearPendingState()
        }
    }

    /**
     * Полное отключение от текущей семьи без sign-out.
     * Сохраняет lastError и googleCalendarId; привязка и все tombstones очищаются атомарно.
     */
    suspend fun disableSync() {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = false
            prefs.remove(KEY_ACCOUNT_ID)
            prefs.remove(KEY_DEVICE_ID)
            prefs.remove(KEY_LAST_SYNC)
            prefs.clearPendingState()
            prefs.remove(KEY_DELETED_MEMBERS)
            prefs.remove(KEY_DELETED_RECIPES)
            prefs.remove(KEY_DELETED_DOCUMENTS)
            // localDataFamilyId preserved: Room still contains the family's data
        }
    }

    /**
     * Полный сброс sync-состояния при выходе из аккаунта.
     * Вызывать только после успешного [LocalDataWiper.wipeAllFamilyData].
     */
    suspend fun resetForSignOut() {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = false
            prefs.remove(KEY_ACCOUNT_ID)
            prefs.remove(KEY_DEVICE_ID)
            prefs.remove(KEY_LAST_SYNC)
            prefs.clearPendingState()
            prefs.remove(KEY_LAST_ERROR)
            prefs.remove(KEY_GOOGLE_CALENDAR_ID)
            prefs.remove(KEY_DELETED_MEMBERS)
            prefs.remove(KEY_DELETED_RECIPES)
            prefs.remove(KEY_DELETED_DOCUMENTS)
            prefs.remove(KEY_LOCAL_DATA_FAMILY_ID)
            prefs.remove(KEY_RESTORE_IN_PROGRESS)
            prefs.remove(KEY_RESTORE_FINGERPRINT)
            prefs.remove(KEY_RESTORE_SOURCE_FAMILY_ID)
            prefs.remove(KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP)
        }
    }

    /**
     * Persistent restore barrier: sync disabled, fingerprint recorded.
     * Fail-safe enables [KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP] before Room replace so startup
     * cannot silently insert the Firebase UID while restore is in flight / across process death.
     * Password / decrypted payload are never stored.
     * Refuses atomically if an active barrier exists with a missing or different fingerprint.
     */
    suspend fun beginRestoreBarrier(fingerprintSha256: String, sourceFamilyId: String?) {
        require(fingerprintSha256.isNotBlank()) { "fingerprint must not be blank" }
        var conflict = false
        dataStore.edit { prefs ->
            val inProgress = prefs[KEY_RESTORE_IN_PROGRESS] == true
            if (inProgress) {
                val existing = prefs[KEY_RESTORE_FINGERPRINT]
                if (existing.isNullOrBlank() || existing != fingerprintSha256) {
                    conflict = true
                    return@edit
                }
            }
            prefs[KEY_RESTORE_IN_PROGRESS] = true
            prefs[KEY_RESTORE_FINGERPRINT] = fingerprintSha256
            if (sourceFamilyId == null) {
                prefs.remove(KEY_RESTORE_SOURCE_FAMILY_ID)
            } else {
                prefs[KEY_RESTORE_SOURCE_FAMILY_ID] = sourceFamilyId
            }
            prefs[KEY_ENABLED] = false
            prefs.clearPendingState()
            // Fail-safe: block auth-member bootstrap until finalize writes the final value.
            prefs[KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP] = true
        }
        if (conflict) {
            throw RestoreBarrierConflictException()
        }
    }

    /**
     * Completes family-binding policy after Room replace and clears the restore barrier.
     * Atomically verifies the active barrier matches [expectedFingerprint], writes the final
     * auth-bootstrap suppression flag, then clears the barrier in the same edit.
     * Does not touch Firebase auth, Google Calendar id, or app preferences.
     */
    suspend fun finalizeRestoreBinding(
        expectedFingerprint: String,
        sourceFamilyId: String?,
        suppressAuthMemberBootstrap: Boolean
    ) {
        require(expectedFingerprint.isNotBlank()) { "fingerprint must not be blank" }
        var mismatch = false
        dataStore.edit { prefs ->
            val inProgress = prefs[KEY_RESTORE_IN_PROGRESS] == true
            val existing = prefs[KEY_RESTORE_FINGERPRINT]
            if (!inProgress || existing.isNullOrBlank() || existing != expectedFingerprint) {
                mismatch = true
                return@edit
            }
            val barrierSource = prefs[KEY_RESTORE_SOURCE_FAMILY_ID]
            if (barrierSource != sourceFamilyId) {
                mismatch = true
                return@edit
            }
            prefs[KEY_ENABLED] = false
            prefs.remove(KEY_ACCOUNT_ID)
            prefs.remove(KEY_DEVICE_ID)
            prefs.remove(KEY_LAST_SYNC)
            prefs.remove(KEY_LAST_ERROR)
            prefs.clearPendingState()
            prefs.remove(KEY_DELETED_MEMBERS)
            prefs.remove(KEY_DELETED_RECIPES)
            prefs.remove(KEY_DELETED_DOCUMENTS)
            if (sourceFamilyId == null) {
                prefs.remove(KEY_LOCAL_DATA_FAMILY_ID)
            } else {
                prefs[KEY_LOCAL_DATA_FAMILY_ID] = sourceFamilyId
            }
            if (suppressAuthMemberBootstrap) {
                prefs[KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP] = true
            } else {
                prefs.remove(KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP)
            }
            prefs.remove(KEY_RESTORE_IN_PROGRESS)
            prefs.remove(KEY_RESTORE_FINGERPRINT)
            prefs.remove(KEY_RESTORE_SOURCE_FAMILY_ID)
        }
        if (mismatch) {
            throw RestoreBarrierConflictException()
        }
    }

    suspend fun setLocalDataFamilyId(familyId: String) {
        require(familyId.isNotBlank()) { "familyId must not be blank" }
        dataStore.edit { prefs ->
            prefs[KEY_LOCAL_DATA_FAMILY_ID] = familyId
        }
    }

    /**
     * Disabled transition binding: target account is stored while sync stays off.
     * Tombstones and [KEY_LOCAL_DATA_FAMILY_ID] are preserved until binding completes.
     */
    suspend fun beginFamilyBindingTransition(targetAccountId: String, deviceId: String) {
        dataStore.edit { prefs ->
            val storedAccountId = prefs[KEY_ACCOUNT_ID]
            val previousMembers = prefs[KEY_DELETED_MEMBERS]
            val previousRecipes = prefs[KEY_DELETED_RECIPES]
            val memberEntries = previousMembers ?: emptySet()
            val recipeEntries = previousRecipes ?: emptySet()
            val normalizedMembers = normalizeFamilyScopedDeletedEntries(memberEntries, storedAccountId)
            val normalizedRecipes = normalizeFamilyScopedDeletedEntries(recipeEntries, storedAccountId)
            prefs.writeFamilyScopedDeletedEntries(KEY_DELETED_MEMBERS, previousMembers, normalizedMembers)
            prefs.writeFamilyScopedDeletedEntries(KEY_DELETED_RECIPES, previousRecipes, normalizedRecipes)
            prefs[KEY_ACCOUNT_ID] = targetAccountId
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_ENABLED] = false
            prefs.clearPendingState()
            prefs.remove(KEY_LAST_SYNC)
            prefs.remove(KEY_LAST_ERROR)
            prefs.remove(KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP)
        }
    }

    suspend fun ensureDeviceId(): String {
        val existing = dataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = java.util.UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = generated
        }
        return generated
    }

    suspend fun updateLastSync(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC] = timestamp
        }
    }

    suspend fun setPendingChanges(hasChanges: Boolean) {
        dataStore.edit { prefs ->
            if (hasChanges) {
                prefs[KEY_PENDING_CHANGES] = true
            } else {
                prefs.clearPendingState()
            }
        }
    }

    suspend fun addPendingCollections(collectionKeys: Set<String>) {
        if (collectionKeys.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[KEY_PENDING_CHANGES] = true
            prefs[KEY_PENDING_COLLECTIONS] =
                prefs[KEY_PENDING_COLLECTIONS].orEmpty() + collectionKeys
        }
    }

    suspend fun replacePendingCollections(collectionKeys: Set<String>) {
        dataStore.edit { prefs ->
            if (collectionKeys.isEmpty()) {
                prefs.clearPendingState()
            } else {
                prefs[KEY_PENDING_CHANGES] = true
                prefs[KEY_PENDING_COLLECTIONS] = collectionKeys
            }
        }
    }

    suspend fun getPendingCollections(): Set<String> =
        dataStore.data.first()[KEY_PENDING_COLLECTIONS].orEmpty()

    suspend fun setSuppressAuthMemberBootstrap(suppress: Boolean) {
        dataStore.edit { prefs ->
            if (suppress) {
                prefs[KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP] = true
            } else {
                prefs.remove(KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP)
            }
        }
    }

    suspend fun setLastError(message: String?) {
        dataStore.edit { prefs ->
            if (message.isNullOrBlank()) {
                prefs.remove(KEY_LAST_ERROR)
            } else {
                prefs[KEY_LAST_ERROR] = message
            }
        }
    }

    suspend fun setGoogleCalendarId(calendarId: String?) {
        dataStore.edit { prefs ->
            if (calendarId.isNullOrBlank()) {
                prefs.remove(KEY_GOOGLE_CALENDAR_ID)
            } else {
                prefs[KEY_GOOGLE_CALENDAR_ID] = calendarId
            }
        }
    }
    
    private fun androidx.datastore.preferences.core.MutablePreferences.writeFamilyScopedDeletedEntries(
        key: androidx.datastore.preferences.core.Preferences.Key<Set<String>>,
        previous: Set<String>?,
        next: Set<String>
    ) {
        when {
            next.isEmpty() -> if (previous != null) remove(key)
            next == previous -> Unit
            else -> this[key] = next
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.mutateFamilyScopedDeletedEntries(
        key: androidx.datastore.preferences.core.Preferences.Key<Set<String>>,
        transform: (Set<String>, String?) -> Set<String>
    ) {
        val storedAccountId = this[KEY_ACCOUNT_ID]
        val previous = this[key]
        val next = transform(previous ?: emptySet(), storedAccountId)
        writeFamilyScopedDeletedEntries(key, previous, next)
    }

    /**
     * Atomically bind sync to [accountId] when local owner policy allows it.
     * @return true when sync was enabled; false when atomic guard rejected the bind.
     */
    internal suspend fun enableSyncForAccount(accountId: String, deviceId: String): Boolean {
        var enabled = false
        dataStore.edit { prefs ->
            val localOwner = prefs[KEY_LOCAL_DATA_FAMILY_ID]
            val storedAccountId = prefs[KEY_ACCOUNT_ID]
            if (!LocalDataFamilySwitchPolicy.canEnableSync(localOwner, accountId, storedAccountId)) {
                return@edit
            }
            val previousMembers = prefs[KEY_DELETED_MEMBERS]
            val previousRecipes = prefs[KEY_DELETED_RECIPES]
            val memberEntries = previousMembers ?: emptySet()
            val recipeEntries = previousRecipes ?: emptySet()
            val normalizedMembers = normalizeFamilyScopedDeletedEntries(memberEntries, storedAccountId)
            val normalizedRecipes = normalizeFamilyScopedDeletedEntries(recipeEntries, storedAccountId)
            prefs.writeFamilyScopedDeletedEntries(KEY_DELETED_MEMBERS, previousMembers, normalizedMembers)
            prefs.writeFamilyScopedDeletedEntries(KEY_DELETED_RECIPES, previousRecipes, normalizedRecipes)
            prefs[KEY_ACCOUNT_ID] = accountId
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_ENABLED] = true
            prefs[KEY_PENDING_CHANGES] = true
            // A fresh binding has an unknown/full local snapshot. Do not reuse a
            // per-collection subset from an earlier binding.
            prefs.remove(KEY_PENDING_COLLECTIONS)
            prefs.remove(KEY_LAST_ERROR)
            enabled = true
        }
        return enabled
    }

    suspend fun addDeletedMember(familyId: String, memberId: String) {
        dataStore.edit { prefs ->
            prefs.mutateFamilyScopedDeletedEntries(KEY_DELETED_MEMBERS) { entries, storedAccountId ->
                addDeletedDocumentIdForFamily(entries, storedAccountId, familyId, memberId)
            }
        }
    }

    suspend fun removeDeletedMember(familyId: String, memberId: String) {
        dataStore.edit { prefs ->
            prefs.mutateFamilyScopedDeletedEntries(KEY_DELETED_MEMBERS) { entries, storedAccountId ->
                removeDeletedDocumentIdForFamily(entries, storedAccountId, familyId, memberId)
            }
        }
    }

    suspend fun getDeletedMembers(familyId: String): Set<String> {
        var documentIds = emptySet<String>()
        dataStore.edit { prefs ->
            val storedAccountId = prefs[KEY_ACCOUNT_ID]
            val previous = prefs[KEY_DELETED_MEMBERS]
            val entries = previous ?: emptySet()
            val (normalized, ids) = readNormalizedDeletedDocumentIdsForFamily(entries, storedAccountId, familyId)
            prefs.writeFamilyScopedDeletedEntries(KEY_DELETED_MEMBERS, previous, normalized)
            documentIds = ids
        }
        return documentIds
    }

    suspend fun clearDeletedMembers(familyId: String) {
        dataStore.edit { prefs ->
            prefs.mutateFamilyScopedDeletedEntries(KEY_DELETED_MEMBERS) { entries, storedAccountId ->
                clearDeletedDocumentIdsForFamily(entries, storedAccountId, familyId)
            }
        }
    }

    suspend fun addDeletedRecipe(familyId: String, recipeId: String) {
        dataStore.edit { prefs ->
            prefs.mutateFamilyScopedDeletedEntries(KEY_DELETED_RECIPES) { entries, storedAccountId ->
                addDeletedDocumentIdForFamily(entries, storedAccountId, familyId, recipeId)
            }
        }
    }

    suspend fun removeDeletedRecipe(familyId: String, recipeId: String) {
        dataStore.edit { prefs ->
            prefs.mutateFamilyScopedDeletedEntries(KEY_DELETED_RECIPES) { entries, storedAccountId ->
                removeDeletedDocumentIdForFamily(entries, storedAccountId, familyId, recipeId)
            }
        }
    }

    suspend fun getDeletedRecipes(familyId: String): Set<String> {
        var documentIds = emptySet<String>()
        dataStore.edit { prefs ->
            val storedAccountId = prefs[KEY_ACCOUNT_ID]
            val previous = prefs[KEY_DELETED_RECIPES]
            val entries = previous ?: emptySet()
            val (normalized, ids) = readNormalizedDeletedDocumentIdsForFamily(entries, storedAccountId, familyId)
            prefs.writeFamilyScopedDeletedEntries(KEY_DELETED_RECIPES, previous, normalized)
            documentIds = ids
        }
        return documentIds
    }

    suspend fun clearDeletedRecipes(familyId: String) {
        dataStore.edit { prefs ->
            prefs.mutateFamilyScopedDeletedEntries(KEY_DELETED_RECIPES) { entries, storedAccountId ->
                clearDeletedDocumentIdsForFamily(entries, storedAccountId, familyId)
            }
        }
    }

    suspend fun addDeletedRecipeForCurrentFamily(recipeId: String) {
        dataStore.edit { prefs ->
            val accountId = prefs[KEY_ACCOUNT_ID]?.takeIf { it.isNotBlank() } ?: return@edit
            prefs.mutateFamilyScopedDeletedEntries(KEY_DELETED_RECIPES) { entries, storedAccountId ->
                addDeletedDocumentIdForFamily(entries, storedAccountId, accountId, recipeId)
            }
        }
    }

    suspend fun removeDeletedRecipeForCurrentFamily(recipeId: String) {
        dataStore.edit { prefs ->
            val accountId = prefs[KEY_ACCOUNT_ID]?.takeIf { it.isNotBlank() } ?: return@edit
            prefs.mutateFamilyScopedDeletedEntries(KEY_DELETED_RECIPES) { entries, storedAccountId ->
                removeDeletedDocumentIdForFamily(entries, storedAccountId, accountId, recipeId)
            }
        }
    }

    suspend fun addDeletedDocuments(documents: Collection<DeletedCloudDocument>) {
        if (documents.isEmpty()) return
        val encoded = documents.map { document ->
            encodeDeletedCloudDocument(document)
        }.toSet()
        dataStore.edit { prefs ->
            val current = prefs[KEY_DELETED_DOCUMENTS]?.toMutableSet() ?: mutableSetOf()
            current.addAll(encoded)
            prefs[KEY_DELETED_DOCUMENTS] = current
        }
    }

    suspend fun removeDeletedDocuments(documents: Collection<DeletedCloudDocument>) {
        if (documents.isEmpty()) return
        val encoded = documents.map(::encodeDeletedCloudDocument).toSet()
        dataStore.edit { prefs ->
            val current = prefs[KEY_DELETED_DOCUMENTS]?.toMutableSet() ?: mutableSetOf()
            current.removeAll(encoded)
            if (current.isEmpty()) {
                prefs.remove(KEY_DELETED_DOCUMENTS)
            } else {
                prefs[KEY_DELETED_DOCUMENTS] = current
            }
        }
    }

    suspend fun addDeletedDocument(familyId: String, collection: String, documentId: String) {
        addDeletedDocuments(
            listOf(
                DeletedCloudDocument(
                    familyId = familyId,
                    collection = collection,
                    documentId = documentId
                )
            )
        )
    }

    suspend fun removeDeletedDocument(familyId: String, collection: String, documentId: String) {
        removeDeletedDocuments(
            listOf(
                DeletedCloudDocument(
                    familyId = familyId,
                    collection = collection,
                    documentId = documentId
                )
            )
        )
    }

    suspend fun getDeletedDocuments(familyId: String): Set<DeletedCloudDocument> {
        val entries = dataStore.data.first()[KEY_DELETED_DOCUMENTS] ?: emptySet()
        return decodeDeletedCloudDocuments(entries).filter { it.familyId == familyId }.toSet()
    }

    suspend fun clearDeletedDocuments(familyId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_DELETED_DOCUMENTS] ?: return@edit
            val remaining = retainDeletedDocumentEntriesAfterClearingFamily(current, familyId)
            if (remaining.isEmpty()) {
                prefs.remove(KEY_DELETED_DOCUMENTS)
            } else {
                prefs[KEY_DELETED_DOCUMENTS] = remaining
            }
        }
    }

    suspend fun clearAllDeletedDocuments() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_DELETED_DOCUMENTS)
        }
    }
}
