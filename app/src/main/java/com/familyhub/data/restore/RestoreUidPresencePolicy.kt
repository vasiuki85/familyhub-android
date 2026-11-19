package com.familyhub.data.restore

/**
 * Pure policy: whether the authenticated Firebase UID appears in a restore snapshot.
 * Never exposes member ID lists to UI layers.
 */
object RestoreUidPresencePolicy {
    fun containsAuthenticatedUser(
        memberIds: Collection<String>,
        authenticatedUid: String?
    ): Boolean {
        val uid = authenticatedUid?.takeIf { it.isNotBlank() } ?: return false
        return memberIds.any { it == uid }
    }

    fun requiresExtraUidConfirmation(
        containsAuthenticatedUser: Boolean,
        authenticatedUid: String?
    ): Boolean {
        val uid = authenticatedUid?.takeIf { it.isNotBlank() } ?: return false
        return !containsAuthenticatedUser && uid.isNotBlank()
    }
}
