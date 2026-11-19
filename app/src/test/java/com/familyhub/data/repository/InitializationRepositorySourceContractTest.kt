package com.familyhub.data.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitializationRepositorySourceContractTest {
    private val initializationSource = File(
        "src/main/java/com/familyhub/data/repository/InitializationRepository.kt"
    ).readText(Charsets.UTF_8)
    private val syncRepositorySource = File(
        "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
    ).readText(Charsets.UTF_8)
    private val settingsViewModelSource = File(
        "src/main/java/com/familyhub/ui/screens/settings/SettingsViewModel.kt"
    ).readText(Charsets.UTF_8)

    @Test
    fun initialization_hasNoFutureDemoSeeder() {
        assertFalse(
            File("src/main/java/com/familyhub/domain/usecase/SeedDemoDataUseCase.kt").exists()
        )
        assertFalse(
            File("src/main/java/com/familyhub/domain/utils/DefaultFamilyMembers.kt").exists()
        )
        assertFalse(initializationSource.contains("DefaultFamilyMembers"))
        assertFalse(initializationSource.contains("FLAG_DEBUGGABLE"))
        assertFalse(initializationSource.contains("Убрать комнату"))
        assertFalse(initializationSource.contains("Банановый смузи"))
    }

    @Test
    fun cleanupAndBootstrap_shareOneExclusiveLock() {
        val lockStart = initializationSource.indexOf("syncExclusiveLock.withLock")
        val wipe = initializationSource.indexOf("localDataWiper.wipeAllFamilyData()")
        val lockedBootstrap = initializationSource.indexOf(
            ".ensureAuthenticatedMemberExistsLocked()"
        )
        val completion = initializationSource.indexOf(
            "preferences[KEY_LEGACY_DEMO_CLEANUP_COMPLETED] = true"
        )

        assertTrue(lockStart >= 0)
        assertTrue(wipe > lockStart)
        assertTrue(lockedBootstrap > wipe)
        assertTrue(completion > lockedBootstrap)
    }

    @Test
    fun builtInCatalog_seedsBeforeOneTimeMarkerInsideExclusiveLock() {
        val lockStart = initializationSource.indexOf("syncExclusiveLock.withLock")
        val seed = initializationSource.indexOf("builtInRecipeSeeder.seedMissing()")
        val completion = initializationSource.indexOf(
            "preferences[KEY_BUILTIN_RECIPES_V1_SEEDED] = true"
        )

        assertTrue(lockStart >= 0)
        assertTrue(seed > lockStart)
        assertTrue(completion > seed)
    }

    @Test
    fun syncAndSettingsDoNotRenderRawMultipleAdminException() {
        assertFalse(syncRepositorySource.contains("Exception(\"Multiple admins"))
        assertFalse(settingsViewModelSource.contains("syncActionError = context.getString(\n" +
            "                        R.string.error_sync"))
        assertTrue(settingsViewModelSource.contains("UserFacingErrorMapper.message"))
    }
}
