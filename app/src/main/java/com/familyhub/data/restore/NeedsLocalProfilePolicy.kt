package com.familyhub.data.restore

/**
 * Persistent recovery: after restore without the authenticated UID, suppression stays set
 * until the user explicitly adds a local profile.
 */
object NeedsLocalProfilePolicy {
    fun isRecoveryNeeded(
        suppressAuthMemberBootstrap: Boolean,
        authenticatedUid: String?,
        membersContainUid: Boolean
    ): Boolean {
        val uid = authenticatedUid?.takeIf { it.isNotBlank() } ?: return false
        if (!suppressAuthMemberBootstrap) return false
        return !membersContainUid
    }
}
