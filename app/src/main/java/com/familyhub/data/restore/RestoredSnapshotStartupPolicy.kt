package com.familyhub.data.restore

/**
 * Pure policy: after a detached encrypted restore, startup must not silently insert the
 * authenticated Firebase user into the restored Room snapshot.
 */
object RestoredSnapshotStartupPolicy {
    fun shouldAutoCreateAuthenticatedMember(
        authenticatedUid: String?,
        membersContainUid: Boolean,
        suppressAuthMemberBootstrap: Boolean,
        restoreInProgress: Boolean = false
    ): Boolean {
        val uid = authenticatedUid?.takeIf { it.isNotBlank() } ?: return false
        if (membersContainUid) return false
        if (restoreInProgress) return false
        if (suppressAuthMemberBootstrap) return false
        return uid.isNotBlank()
    }

    /**
     * When the restored snapshot already contains the auth UID, a leftover suppression flag
     * from a prior restore must be cleared safely (self-heal).
     */
    fun shouldClearStaleSuppression(
        membersContainUid: Boolean,
        suppressAuthMemberBootstrap: Boolean,
        restoreInProgress: Boolean
    ): Boolean {
        if (restoreInProgress) return false
        return membersContainUid && suppressAuthMemberBootstrap
    }
}
