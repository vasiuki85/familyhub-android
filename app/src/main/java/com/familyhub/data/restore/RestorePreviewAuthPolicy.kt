package com.familyhub.data.restore

/**
 * Binds encrypted-restore preview to the Firebase auth session used at inspect time.
 */
object RestorePreviewAuthPolicy {
    fun authSessionMatches(inspectAuthUid: String?, currentAuthUid: String?): Boolean {
        val inspected = inspectAuthUid?.takeIf { it.isNotBlank() }
        val current = currentAuthUid?.takeIf { it.isNotBlank() }
        if (inspected == null && current == null) return true
        return inspected == current
    }
}
