package com.familyhub.ui.screens.home

import com.familyhub.R
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncHealthUiMapperTest {

    @Test
    fun map_localModeWithoutAccount_hidesBannerEvenWithStaleError() {
        val model = SyncHealthUiMapper.map(
            syncState = SyncState(
                accountId = null,
                isEnabled = true,
                lastError = "Permission denied: raw backend text",
            ),
            memberRole = MemberRole.PENDING,
        )

        assertFalse(model.visible)
    }

    @Test
    fun map_cloudConfiguredButPaused_showsPausedWithSettingsAction() {
        val model = map(
            syncState = SyncState(accountId = ACCOUNT, isEnabled = false),
            memberRole = MemberRole.CHILD,
        )

        assertTrue(model.visible)
        assertEquals(R.string.home_sync_health_paused, model.messageResId)
        assertEquals(SyncHealthAction.SETTINGS, model.action)
        assertEquals(R.string.home_sync_health_action_settings, model.actionLabelResId)
        assertEquals(SyncHealthSeverity.WARNING, model.severity)
    }

    @Test
    fun map_pausedOutranksPendingRole() {
        val model = map(
            syncState = SyncState(accountId = ACCOUNT, isEnabled = false),
            memberRole = MemberRole.PENDING,
        )

        assertEquals(R.string.home_sync_health_paused, model.messageResId)
        assertNotEquals(R.string.home_sync_health_pending_approval, model.messageResId)
    }

    @Test
    fun map_syncing_showsProgressWithoutAction() {
        val model = map(
            syncState = SyncState(
                accountId = ACCOUNT,
                isEnabled = true,
                isSyncing = true,
            ),
            memberRole = MemberRole.PARENT,
        )

        assertTrue(model.visible)
        assertEquals(R.string.home_sync_health_syncing, model.messageResId)
        assertTrue(model.showProgress)
        assertEquals(SyncHealthAction.NONE, model.action)
        assertNull(model.actionLabelResId)
        assertFalse(model.actionEnabled)
    }

    @Test
    fun map_syncingOutranksPendingChanges() {
        val model = map(
            syncState = SyncState(
                accountId = ACCOUNT,
                isEnabled = true,
                isSyncing = true,
                hasPendingChanges = true,
            ),
            memberRole = MemberRole.PARENT,
        )

        assertEquals(R.string.home_sync_health_syncing, model.messageResId)
    }

    @Test
    fun map_lastError_showsGenericMessageWithoutRawExceptionText() {
        val rawError = "Firestore PERMISSION_DENIED at families/x: raw exception payload"
        val model = map(
            syncState = SyncState(
                accountId = ACCOUNT,
                isEnabled = true,
                lastError = rawError,
            ),
            memberRole = MemberRole.PARENT,
        )

        assertTrue(model.visible)
        assertEquals(R.string.home_sync_health_error, model.messageResId)
        assertEquals(SyncHealthAction.RETRY, model.action)
        assertEquals(R.string.home_sync_health_action_retry, model.actionLabelResId)
        assertEquals(SyncHealthSeverity.ERROR, model.severity)
    }

    @Test
    fun map_pendingRole_showsAwaitingApprovalWithCheckAction() {
        val model = map(
            syncState = SyncState(
                accountId = ACCOUNT,
                isEnabled = true,
                lastSyncAt = NOW,
            ),
            memberRole = MemberRole.PENDING,
        )

        assertTrue(model.visible)
        assertEquals(R.string.home_sync_health_pending_approval, model.messageResId)
        assertEquals(SyncHealthAction.CHECK, model.action)
        assertEquals(R.string.home_sync_health_action_check, model.actionLabelResId)
    }

    @Test
    fun map_pendingChanges_showsSyncAction() {
        val model = map(
            syncState = SyncState(
                accountId = ACCOUNT,
                isEnabled = true,
                hasPendingChanges = true,
                lastSyncAt = NOW,
            ),
            memberRole = MemberRole.CHILD,
        )

        assertTrue(model.visible)
        assertEquals(R.string.home_sync_health_pending_changes, model.messageResId)
        assertEquals(SyncHealthAction.SYNC_NOW, model.action)
        assertEquals(R.string.home_sync_health_action_sync, model.actionLabelResId)
    }

    @Test
    fun map_enabledWithoutLastSync_showsFirstSyncMessage() {
        val model = map(
            syncState = SyncState(
                accountId = ACCOUNT,
                isEnabled = true,
                lastSyncAt = null,
            ),
            memberRole = MemberRole.PARENT,
        )

        assertTrue(model.visible)
        assertEquals(R.string.home_sync_health_first_sync, model.messageResId)
        assertEquals(SyncHealthAction.NONE, model.action)
        assertNull(model.actionLabelResId)
    }

    @Test
    fun map_healthyState_hidesBanner() {
        val model = map(
            syncState = SyncState(
                accountId = ACCOUNT,
                isEnabled = true,
                lastSyncAt = NOW,
                hasPendingChanges = false,
                lastError = null,
                isSyncing = false,
            ),
            memberRole = MemberRole.PARENT,
        )

        assertFalse(model.visible)
    }

    @Test
    fun map_errorRetryDisabledWhileSyncing() {
        val model = SyncHealthUiMapper.map(
            syncState = SyncState(
                accountId = ACCOUNT,
                isEnabled = true,
                isSyncing = true,
                lastError = "ignored because syncing wins",
            ),
            memberRole = MemberRole.PARENT,
        )

        assertEquals(R.string.home_sync_health_syncing, model.messageResId)
        assertFalse(model.actionEnabled)
    }

    private fun map(
        syncState: SyncState,
        memberRole: MemberRole,
    ) = SyncHealthUiMapper.map(
        syncState = syncState,
        memberRole = memberRole,
    )

    private companion object {
        const val ACCOUNT = "family-a"
        const val NOW = 1_700_000_000_000L
    }
}
