package com.familyhub.ui.screens.settings

enum class SettingsInitialSyncAction {
    CREATE,
    JOIN,
    ;

    companion object {
        fun fromRouteValue(value: String?): SettingsInitialSyncAction? =
            value?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}

object SettingsInitialSyncActionPolicy {
    fun resolve(
        initialAction: SettingsInitialSyncAction?,
        alreadyConsumed: Boolean,
        accountId: String?,
        isHydrated: Boolean,
    ): SettingsInitialSyncAction? {
        if (!isHydrated || alreadyConsumed || initialAction == null || accountId != null) {
            return null
        }
        return initialAction
    }
}
