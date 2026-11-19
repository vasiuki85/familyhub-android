package com.familyhub.ui.screens.home

import androidx.annotation.StringRes
import com.familyhub.R
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.SyncState

enum class SyncHealthSeverity {
    INFO,
    WARNING,
    ERROR,
}

enum class SyncHealthAction {
    NONE,
    SETTINGS,
    RETRY,
    CHECK,
    SYNC_NOW,
}

data class SyncHealthUiModel(
    val visible: Boolean = false,
    @StringRes val messageResId: Int? = null,
    @StringRes val actionLabelResId: Int? = null,
    val action: SyncHealthAction = SyncHealthAction.NONE,
    val showProgress: Boolean = false,
    val actionEnabled: Boolean = true,
    @StringRes val contentDescriptionResId: Int? = null,
    val severity: SyncHealthSeverity = SyncHealthSeverity.INFO,
) {
    companion object {
        fun hidden(): SyncHealthUiModel = SyncHealthUiModel(visible = false)
    }
}

fun HomeUiState.displaySyncHealth(): SyncHealthUiModel {
    if (!syncRetryInFlight) {
        return syncHealth
    }
    return syncHealth.copy(actionEnabled = false)
}

object SyncHealthUiMapper {
    fun map(
        syncState: SyncState,
        memberRole: MemberRole,
    ): SyncHealthUiModel {
        if (syncState.accountId == null) {
            return SyncHealthUiModel.hidden()
        }

        if (!syncState.isEnabled) {
            return visible(
                messageResId = R.string.home_sync_health_paused,
                actionLabelResId = R.string.home_sync_health_action_settings,
                action = SyncHealthAction.SETTINGS,
                contentDescriptionResId = R.string.home_sync_health_paused_cd,
                severity = SyncHealthSeverity.WARNING,
            )
        }

        if (syncState.isSyncing) {
            return visible(
                messageResId = R.string.home_sync_health_syncing,
                showProgress = true,
                actionEnabled = false,
                contentDescriptionResId = R.string.home_sync_health_syncing_cd,
                severity = SyncHealthSeverity.INFO,
            )
        }

        if (syncState.lastError != null) {
            return visible(
                messageResId = R.string.home_sync_health_error,
                actionLabelResId = R.string.home_sync_health_action_retry,
                action = SyncHealthAction.RETRY,
                actionEnabled = !syncState.isSyncing,
                contentDescriptionResId = R.string.home_sync_health_error_cd,
                severity = SyncHealthSeverity.ERROR,
            )
        }

        if (memberRole == MemberRole.PENDING) {
            return visible(
                messageResId = R.string.home_sync_health_pending_approval,
                actionLabelResId = R.string.home_sync_health_action_check,
                action = SyncHealthAction.CHECK,
                contentDescriptionResId = R.string.home_sync_health_pending_approval_cd,
                severity = SyncHealthSeverity.WARNING,
            )
        }

        if (syncState.hasPendingChanges) {
            return visible(
                messageResId = R.string.home_sync_health_pending_changes,
                actionLabelResId = R.string.home_sync_health_action_sync,
                action = SyncHealthAction.SYNC_NOW,
                contentDescriptionResId = R.string.home_sync_health_pending_changes_cd,
                severity = SyncHealthSeverity.INFO,
            )
        }

        if (syncState.lastSyncAt == null) {
            return visible(
                messageResId = R.string.home_sync_health_first_sync,
                contentDescriptionResId = R.string.home_sync_health_first_sync_cd,
                severity = SyncHealthSeverity.INFO,
            )
        }

        return SyncHealthUiModel.hidden()
    }

    private fun visible(
        @StringRes messageResId: Int,
        @StringRes actionLabelResId: Int? = null,
        action: SyncHealthAction = SyncHealthAction.NONE,
        showProgress: Boolean = false,
        actionEnabled: Boolean = true,
        @StringRes contentDescriptionResId: Int,
        severity: SyncHealthSeverity,
    ): SyncHealthUiModel = SyncHealthUiModel(
        visible = true,
        messageResId = messageResId,
        actionLabelResId = actionLabelResId,
        action = action,
        showProgress = showProgress,
        actionEnabled = actionEnabled,
        contentDescriptionResId = contentDescriptionResId,
        severity = severity,
    )
}
