package com.familyhub.data.sync

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncLifecycleContractTest {

    @Test
    fun settingsViewModel_pauseSync_usesPauseSyncUseCaseNotDisable() {
        val source = readSource(
            "app/src/main/java/com/familyhub/ui/screens/settings/SettingsViewModel.kt",
            "src/main/java/com/familyhub/ui/screens/settings/SettingsViewModel.kt"
        )
        val pauseBlock = extractFunctionBlock(source, "fun pauseSync")

        assertTrue(pauseBlock.contains("pauseSyncUseCase"))
        assertFalse(pauseBlock.contains("disableSyncUseCase"))
    }

    @Test
    fun syncRepository_pauseSync_preservesTombstonesAndAccountBinding() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val pauseBlock = extractFunctionBlock(source, "private suspend fun pauseSyncLocked")

        assertTrue(pauseBlock.contains("syncSettingsDataStore.pauseSync()"))
        assertFalse(pauseBlock.contains("clearDeletedMembers"))
        assertFalse(pauseBlock.contains("clearDeletedRecipes"))
        assertFalse(pauseBlock.contains("clearAllDeletedDocuments"))
        assertFalse(pauseBlock.contains("disableSync()"))
    }

    @Test
    fun syncRepository_disableSync_delegatesAtomicCleanupToDataStore() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val disableBlock = extractFunctionBlock(source, "private suspend fun disableSyncLocked")

        assertTrue(disableBlock.contains("syncSettingsDataStore.disableSync()"))
        assertFalse(disableBlock.contains("clearDeletedMembers"))
        assertFalse(disableBlock.contains("clearDeletedRecipes"))
        assertFalse(disableBlock.contains("clearAllDeletedDocuments"))
        assertFalse(disableBlock.contains("pauseSync()"))
    }

    @Test
    fun syncSettings_pauseSync_preservesAccountBinding() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt",
            "src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt"
        )
        val pauseBlock = extractFunctionBlock(source, "suspend fun pauseSync")

        assertFalse(pauseBlock.contains("remove(KEY_ACCOUNT_ID)"))
        assertFalse(pauseBlock.contains("remove(KEY_DELETED_MEMBERS)"))
        assertFalse(pauseBlock.contains("remove(KEY_DELETED_RECIPES)"))
        assertFalse(pauseBlock.contains("remove(KEY_DELETED_DOCUMENTS)"))
    }

    @Test
    fun syncSettings_disableSync_unbindsAccountButKeepsLastErrorAndCalendar() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt",
            "src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt"
        )
        val disableBlock = extractFunctionBlock(source, "suspend fun disableSync")

        assertTrue(disableBlock.contains("remove(KEY_ACCOUNT_ID)"))
        assertTrue(disableBlock.contains("remove(KEY_DEVICE_ID)"))
        assertTrue(disableBlock.contains("remove(KEY_LAST_SYNC)"))
        assertTrue(disableBlock.contains("remove(KEY_DELETED_MEMBERS)"))
        assertTrue(disableBlock.contains("remove(KEY_DELETED_RECIPES)"))
        assertTrue(disableBlock.contains("remove(KEY_DELETED_DOCUMENTS)"))
        assertFalse(disableBlock.contains("remove(KEY_LAST_ERROR)"))
        assertFalse(disableBlock.contains("remove(KEY_GOOGLE_CALENDAR_ID)"))
        assertFalse(disableBlock.contains("remove(KEY_LOCAL_DATA_FAMILY_ID)"))
    }

    @Test
    fun syncSettings_pauseSync_preservesLocalDataFamilyOwner() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt",
            "src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt"
        )
        val pauseBlock = extractFunctionBlock(source, "suspend fun pauseSync")

        assertFalse(pauseBlock.contains("remove(KEY_LOCAL_DATA_FAMILY_ID)"))
    }

    @Test
    fun syncSettings_resetForSignOut_clearsLocalDataFamilyOwner() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt",
            "src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt"
        )
        val resetBlock = extractFunctionBlock(source, "suspend fun resetForSignOut")

        assertTrue(resetBlock.contains("remove(KEY_LOCAL_DATA_FAMILY_ID)"))
        assertTrue(resetBlock.contains("KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP"))
    }

    @Test
    fun syncRepository_enableSync_checksLocalDataOwnerBeforeBinding() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val enableBlock = extractFunctionBlock(source, "private suspend fun enableSyncLocked")

        assertTrue(enableBlock.contains("enableSyncForAccount(accountId, deviceId)"))
        assertTrue(enableBlock.contains("LocalDataFamilyMismatchException"))
    }

    @Test
    fun syncRepository_prepareFamilyBinding_ordersCancelTransitionWipeThenOwner() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val prepareBlock = extractFunctionBlock(source, "suspend fun prepareFamilyBinding")

        assertTrue(prepareBlock.indexOf("cancelPeriodicSync") < prepareBlock.indexOf("beginFamilyBindingTransition"))
        assertFalse(prepareBlock.contains("ensureDeviceId()"))
        assertTrue(prepareBlock.indexOf("UUID.randomUUID") < prepareBlock.indexOf("beginFamilyBindingTransition"))
        assertTrue(prepareBlock.indexOf("wipeAllFamilyData") < prepareBlock.indexOf("setLocalDataFamilyId"))
    }

    @Test
    fun syncRepository_syncNow_blocksExportBeforeDownloadWhenOwnerMismatch() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val syncBlock = extractFunctionBlock(source, "suspend fun syncNow")
        val exportIndex = source.indexOf("exportService.getExportData", syncBlock.indexOf("suspend fun syncNow"))
        val guardIndex = syncBlock.indexOf("resolveSyncExportGuard")

        assertTrue(guardIndex >= 0)
        assertTrue(exportIndex >= 0)
        assertTrue(guardIndex < exportIndex - syncBlock.indexOf("suspend fun syncNow"))
        assertTrue(syncBlock.indexOf("resolveSyncExportGuard") < syncBlock.indexOf("recentlySynced"))
    }

    @Test
    fun settingsViewModel_enableSync_preparesBeforeLocalAndRemoteMutations() {
        val source = readSource(
            "app/src/main/java/com/familyhub/ui/screens/settings/SettingsViewModel.kt",
            "src/main/java/com/familyhub/ui/screens/settings/SettingsViewModel.kt"
        )
        val enableBlock = extractFunctionBlock(source, "fun enableSync")

        assertTrue(enableBlock.indexOf("prepareFamilyBinding") < enableBlock.indexOf("ensureSelfMemberExists"))
        assertTrue(enableBlock.indexOf("prepareFamilyBinding") < enableBlock.indexOf("createFamily"))
    }

    @Test
    fun syncRepository_forbidsDirectReadCopyWriteStateUpdates() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )

        assertFalse(
            "_state.value = _state.value.copy",
            source.contains("_state.value = _state.value.copy"),
        )
    }

    @Test
    fun syncRepository_hydratesStateFromFirstSettingsSnapshot() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val initBlock = source.substringAfter("init {").substringBefore("fun observeSyncState")

        assertTrue(initBlock.contains("settingsFlow.collectLatest"))
        assertTrue(initBlock.contains("SyncStateReducer.applyHydratedSettings"))
        assertTrue(initBlock.contains("_state.update"))
    }

    @Test
    fun settingsViewModel_enableSync_marksFamilyOnboardingDismissed() {
        val source = readSource(
            "app/src/main/java/com/familyhub/ui/screens/settings/SettingsViewModel.kt",
            "src/main/java/com/familyhub/ui/screens/settings/SettingsViewModel.kt"
        )
        val enableBlock = extractFunctionBlock(source, "fun enableSync")

        assertTrue(enableBlock.contains("setFamilyOnboardingDismissed(true)"))
        assertTrue(enableBlock.indexOf("setFamilyOnboardingDismissed(true)") > enableBlock.indexOf("enableSyncUseCase"))
    }

    @Test
    fun settingsViewModel_resumeSync_preparesBeforeEnable() {
        val source = readSource(
            "app/src/main/java/com/familyhub/ui/screens/settings/SettingsViewModel.kt",
            "src/main/java/com/familyhub/ui/screens/settings/SettingsViewModel.kt"
        )
        val resumeBlock = extractFunctionBlock(source, "fun resumeSyncFromStored")

        assertTrue(resumeBlock.indexOf("prepareFamilyBinding") < resumeBlock.indexOf("enableSyncUseCase"))
    }

    @Test
    fun syncRepository_enableSync_usesSingleAtomicDataStoreBind() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val enableBlock = extractFunctionBlock(source, "private suspend fun enableSyncLocked")

        assertTrue(enableBlock.contains("enableSyncForAccount(accountId, deviceId)"))
        assertFalse(enableBlock.contains("setSyncEnabled(true)"))
        assertFalse(enableBlock.contains("updateAccount(accountId"))
        assertFalse(enableBlock.contains("setPendingChanges(true)"))
        assertTrue(enableBlock.contains("cloudSyncScheduler.schedulePeriodicSync()"))
    }

    @Test
    fun syncRepository_enableSync_runsUnderExclusiveLock() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val enableBlock = extractFunctionBlock(source, "suspend fun enableSync")

        assertTrue(enableBlock.contains("syncExclusiveLock.withLock"))
    }

    @Test
    fun syncRepository_wipeAndResetForSignOut_delegatesToLockedBody() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val wipeBlock = extractFunctionBlock(source, "suspend fun wipeAndResetForSignOut")
        val lockedBlock = extractFunctionBlock(source, "suspend fun wipeAndResetForSignOutLocked")

        assertTrue(wipeBlock.contains("syncExclusiveLock.withLock"))
        assertTrue(wipeBlock.contains("wipeAndResetForSignOutLocked"))
        assertFalse(lockedBlock.contains("syncExclusiveLock.withLock"))
        assertTrue(lockedBlock.indexOf("cancelPeriodicSync") < lockedBlock.indexOf("wipeAllFamilyData"))
        assertTrue(lockedBlock.indexOf("wipeAllFamilyData") < lockedBlock.indexOf("resetForSignOut"))
    }

    @Test
    fun signOutUseCase_holdsExclusiveLockThroughFirebaseSignOut() {
        val source = readSource(
            "app/src/main/java/com/familyhub/domain/usecase/SignOutUseCase.kt",
            "src/main/java/com/familyhub/domain/usecase/SignOutUseCase.kt"
        )

        assertTrue(source.contains("syncExclusiveLock.withLock"))
        assertTrue(source.indexOf("wipeAndResetForSignOutLocked") < source.indexOf("resetUserSession"))
        assertTrue(source.indexOf("resetUserSession") < source.indexOf("firebaseAuth.signOut"))
    }

    @Test
    fun syncRepository_prepareRefuse_pausesBeforeThrow() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val prepareBlock = extractFunctionBlock(source, "suspend fun prepareFamilyBinding")

        assertTrue(prepareBlock.contains("refuseFamilyBindingLocked"))
        assertTrue(
            prepareBlock.indexOf("refuseFamilyBindingLocked") <
                prepareBlock.indexOf("FamilyBindingRefusedException")
        )
    }

    @Test
    fun syncRepository_syncNow_ownerGuardBeforeRecentlySynced() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        val syncBlock = extractFunctionBlock(source, "suspend fun syncNow")

        assertTrue(syncBlock.contains("settingsFlow.first()"))
        assertTrue(syncBlock.indexOf("resolveSyncExportGuard") < syncBlock.indexOf("recentlySynced"))
        assertTrue(syncBlock.indexOf("resolveSyncExportGuard") < syncBlock.indexOf("getExportData"))
    }

    @Test
    fun syncSettings_enableSyncForAccount_isInternalWithAtomicGuard() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt",
            "src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt"
        )

        assertTrue(source.contains("internal suspend fun enableSyncForAccount"))
        val enableBlock = extractFunctionBlock(source, "suspend fun enableSyncForAccount")
        assertTrue(enableBlock.contains("LocalDataFamilySwitchPolicy.canEnableSync"))
        assertTrue(enableBlock.contains("return enabled"))
    }

    @Test
    fun syncSettings_enableSyncForAccount_performsMigrationAndBindInOneEdit() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt",
            "src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt"
        )
        val enableBlock = extractFunctionBlock(source, "suspend fun enableSyncForAccount")

        assertTrue(enableBlock.contains("dataStore.edit"))
        assertTrue(enableBlock.contains("normalizeFamilyScopedDeletedEntries"))
        assertFalse(enableBlock.contains("previousAccountId != accountId"))
        assertTrue(enableBlock.contains("LocalDataFamilySwitchPolicy.canEnableSync"))
        assertTrue(enableBlock.contains("prefs[KEY_ACCOUNT_ID] = accountId"))
        assertTrue(enableBlock.contains("prefs[KEY_ENABLED] = true"))
        assertTrue(enableBlock.contains("prefs[KEY_PENDING_CHANGES] = true"))
        assertTrue(enableBlock.contains("prefs.remove(KEY_LAST_ERROR)"))
    }

    @Test
    fun syncSettings_removedNonAtomicAccountBindingApis() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt",
            "src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt"
        )

        assertFalse(source.contains("suspend fun setSyncEnabled"))
        assertFalse(source.contains("suspend fun updateAccount"))
    }

    private fun readSource(vararg candidates: String): String {
        val resolved = candidates
            .map { Paths.get(it) }
            .firstOrNull { Files.exists(it) }
            ?: error("Source file not found: ${candidates.joinToString()}")
        return String(Files.readAllBytes(resolved), StandardCharsets.UTF_8)
    }

    private fun extractFunctionBlock(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "Function $signature not found" }
        val openBrace = source.indexOf('{', start)
        var depth = 0
        for (index in openBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(start, index + 1)
                    }
                }
            }
        }
        error("Unterminated function block for $signature")
    }
}
