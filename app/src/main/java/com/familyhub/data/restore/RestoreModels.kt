package com.familyhub.data.restore

import com.familyhub.data.export.ExportData

/** Metadata-only preview from authenticated decrypt; never includes [ExportData]. */
data class RestorePreview(
    val fingerprintSha256: String,
    val createdAt: Long,
    val sourceFamilyId: String?,
    val payloadVersion: Int,
    val counts: RestoreCollectionCounts,
    /** Safe boolean only — never expose member ids to UI. */
    val containsAuthenticatedUser: Boolean = false
)

data class RestoreCollectionCounts(
    val members: Int,
    val tasks: Int,
    val shoppingLists: Int,
    val shoppingItems: Int,
    val events: Int,
    val recipes: Int,
    val familyGoals: Int,
    val rewards: Int,
    val penalties: Int,
    val weeklyMenus: Int,
    val menuTemplates: Int
)

sealed class RestoreResult {
    data class Success(
        val fingerprintSha256: String,
        val sourceFamilyId: String?,
        val counts: RestoreCollectionCounts,
        /** True when the authenticated UID was absent from the restored snapshot. */
        val needsLocalProfile: Boolean = false
    ) : RestoreResult()
}

/**
 * Immutable, fully validated snapshot ready for a single Room replace transaction.
 */
data class ValidatedRestoreSnapshot(
    val payloadVersion: Int,
    val createdAt: Long,
    val sourceFamilyId: String?,
    val data: ExportData,
    val counts: RestoreCollectionCounts
)

class RestoreValidationException(message: String) : IllegalArgumentException(message)

class RestoreFingerprintMismatchException :
    IllegalArgumentException("Encrypted restore fingerprint mismatch")

class RestoreBarrierConflictException :
    IllegalStateException("A restore is already in progress for a different backup")

class RestoreBlockedException(message: String) : IllegalStateException(message)

class RestoreFinalizationException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
