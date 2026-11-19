package com.familyhub.data.sync

/**
 * Typed sync failures whose internal invariant details must not become user-visible text.
 */
sealed class SyncFamilyInvariantException : IllegalStateException() {
    class MultipleAdmins : SyncFamilyInvariantException()

    class MissingAdmin : SyncFamilyInvariantException()

    class InvalidAdminCount : SyncFamilyInvariantException()
}
