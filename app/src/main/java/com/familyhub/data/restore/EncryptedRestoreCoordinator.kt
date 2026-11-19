package com.familyhub.data.restore

import android.content.Context
import com.familyhub.data.backup.EncryptedBackupCodec
import com.familyhub.data.importer.DataImportService
import com.familyhub.data.sync.CloudSyncScheduler
import com.familyhub.data.sync.SyncExclusiveLock
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Crash-safe two-phase encrypted restore (inspect → restore). No Compose UI.
 * Local-only: never enables sync or uploads after restore.
 *
 * Fingerprint and AES-GCM decrypt always operate on the same bounded byte snapshot.
 * Heavy work runs on [ioDispatcher]; password wipe stays in the outer finally
 * (including cancellation before the shared mutex is acquired).
 */
@Singleton
class EncryptedRestoreCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val codec: EncryptedBackupCodec,
    private val importService: DataImportService,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val cloudSyncScheduler: CloudSyncScheduler,
    private val syncExclusiveLock: SyncExclusiveLock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun inspect(
        file: File,
        password: CharArray,
        authenticatedUserId: String? = null
    ): RestorePreview {
        try {
            return withContext(ioDispatcher) {
                val resolved = RestoreFileAccess.resolveCanonicalRestoreFile(
                    file = file,
                    filesDir = context.filesDir,
                    cacheDir = context.cacheDir
                )
                val fileBytes = RestoreFileAccess.readExactBounded(resolved.canonicalFile)
                val fingerprint = RestoreFileAccess.sha256Hex(fileBytes)
                val payload = codec.decryptFromBytes(fileBytes, password)
                val snapshot = RestorePayloadValidator.validate(payload)
                val containsAuthenticatedUser = RestoreUidPresencePolicy.containsAuthenticatedUser(
                    memberIds = snapshot.data.members.map { it.id },
                    authenticatedUid = authenticatedUserId
                )
                RestorePreview(
                    fingerprintSha256 = fingerprint,
                    createdAt = snapshot.createdAt,
                    sourceFamilyId = snapshot.sourceFamilyId,
                    payloadVersion = snapshot.payloadVersion,
                    counts = snapshot.counts,
                    containsAuthenticatedUser = containsAuthenticatedUser
                )
            }
        } finally {
            Arrays.fill(password, '\u0000')
        }
    }

    suspend fun restore(
        file: File,
        password: CharArray,
        expectedFingerprint: String,
        authenticatedUserId: String? = null
    ): RestoreResult {
        try {
            return withContext(ioDispatcher) {
                // Path-only resolve outside the lock; do not retain ciphertext across waiters.
                RestoreFileAccess.resolveCanonicalRestoreFile(
                    file = file,
                    filesDir = context.filesDir,
                    cacheDir = context.cacheDir
                )

                syncExclusiveLock.withLock {
                    val resolved = RestoreFileAccess.resolveCanonicalRestoreFile(
                        file = file,
                        filesDir = context.filesDir,
                        cacheDir = context.cacheDir
                    )
                    val fileBytes = RestoreFileAccess.readExactBounded(resolved.canonicalFile)
                    val fingerprint = RestoreFileAccess.sha256Hex(fileBytes)
                    if (fingerprint != expectedFingerprint) {
                        throw RestoreFingerprintMismatchException()
                    }

                    // Decrypt only after fingerprint match (Argon2id runs inside decryptFromBytes).
                    val payload = codec.decryptFromBytes(fileBytes, password)
                    val snapshot = RestorePayloadValidator.validate(payload)
                    val containsAuthenticatedUser = RestoreUidPresencePolicy.containsAuthenticatedUser(
                        memberIds = snapshot.data.members.map { it.id },
                        authenticatedUid = authenticatedUserId
                    )
                    val needsLocalProfile = RestoreUidPresencePolicy.requiresExtraUidConfirmation(
                        containsAuthenticatedUser = containsAuthenticatedUser,
                        authenticatedUid = authenticatedUserId
                    )

                    // Barrier fail-safe enables suppression before Room replace.
                    syncSettingsDataStore.beginRestoreBarrier(
                        fingerprintSha256 = fingerprint,
                        sourceFamilyId = snapshot.sourceFamilyId
                    )
                    cloudSyncScheduler.cancelPeriodicSync()

                    try {
                        importService.applyValidatedRestoreSnapshot(snapshot)
                    } catch (error: Exception) {
                        Timber.e(error, "Encrypted restore Room transaction failed; barrier kept")
                        throw error
                    }

                    try {
                        syncSettingsDataStore.finalizeRestoreBinding(
                            expectedFingerprint = fingerprint,
                            sourceFamilyId = snapshot.sourceFamilyId,
                            suppressAuthMemberBootstrap = needsLocalProfile
                        )
                    } catch (error: Exception) {
                        Timber.e(
                            error,
                            "Encrypted restore finalization failed; Room replaced, barrier remains"
                        )
                        throw RestoreFinalizationException(
                            "Restore Room commit succeeded but binding finalization failed",
                            error
                        )
                    }

                    RestoreResult.Success(
                        fingerprintSha256 = fingerprint,
                        sourceFamilyId = snapshot.sourceFamilyId,
                        counts = snapshot.counts,
                        needsLocalProfile = needsLocalProfile
                    )
                }
            }
        } finally {
            Arrays.fill(password, '\u0000')
        }
    }
}
