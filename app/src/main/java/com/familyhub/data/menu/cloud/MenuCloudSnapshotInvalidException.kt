package com.familyhub.data.menu.cloud

/**
 * Fail-closed signal for malformed menu cloud snapshots.
 *
 * Orphan children are **not** malformed; they are classified separately.
 */
class MenuCloudSnapshotInvalidException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
