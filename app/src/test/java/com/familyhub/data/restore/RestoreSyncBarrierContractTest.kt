package com.familyhub.data.restore

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class RestoreSyncBarrierContractTest {

    @Test
    fun syncSettings_definesRestoreBarrierAndFinalizeApis() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt",
            "src/main/java/com/familyhub/data/datastore/SyncSettingsDataStore.kt"
        )
        assertTrue(source.contains("beginRestoreBarrier"))
        assertTrue(source.contains("finalizeRestoreBinding"))
        assertTrue(source.contains("expectedFingerprint"))
        assertTrue(source.contains("RestoreBarrierConflictException"))
        assertTrue(source.contains("sync_restore_in_progress"))
        assertTrue(source.contains("sync_restore_fingerprint"))
        assertTrue(source.contains("KEY_GOOGLE_CALENDAR_ID"))
        val finalize = extractFunction(source, "suspend fun finalizeRestoreBinding")
        assertTrue(finalize.contains("prefs.remove(KEY_ACCOUNT_ID)"))
        assertTrue(finalize.contains("prefs.remove(KEY_DEVICE_ID)"))
        assertTrue(finalize.contains("prefs.remove(KEY_DELETED_MEMBERS)"))
        assertTrue(finalize.contains("prefs.remove(KEY_RESTORE_IN_PROGRESS)"))
        assertTrue(finalize.contains("KEY_LOCAL_DATA_FAMILY_ID"))
        assertTrue(finalize.contains("suppressAuthMemberBootstrap"))
        assertTrue(finalize.contains("KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP"))
        assertFalseContains(finalize, "KEY_GOOGLE_CALENDAR_ID")
        val begin = extractFunction(source, "suspend fun beginRestoreBarrier")
        assertTrue(begin.contains("KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP"))
        val reset = extractFunction(source, "suspend fun resetForSignOut")
        assertTrue(reset.contains("KEY_SUPPRESS_AUTH_MEMBER_BOOTSTRAP"))
    }

    @Test
    fun syncRepository_gatesLifecycleOnRestoreBarrier() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/sync/SyncRepository.kt",
            "src/main/java/com/familyhub/data/sync/SyncRepository.kt"
        )
        assertTrue(source.contains("refuseIfRestoreBarrierLocked"))
        assertTrue(source.contains("restoreInProgress"))
        assertTrue(source.contains("syncExclusiveLock: SyncExclusiveLock"))
        assertTrue(source.contains("cancelPeriodicSync()"))
        assertFalseContains(source, "private val syncExclusiveLock = SyncExclusiveLock()")
    }

    @Test
    fun importService_exposesTypedRestorePathWithSettleContract() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/importer/DataImportService.kt",
            "src/main/java/com/familyhub/data/importer/DataImportService.kt"
        )
        assertTrue(source.contains("suspend fun applyValidatedRestoreSnapshot"))
        assertTrue(source.contains("internal suspend fun replaceValidatedExport"))
        assertTrue(source.contains("DEFAULT_SETTLE_DELAY_MS"))
        assertTrue(source.contains("applyImportSnapshot"))
        assertFalseContains(source, "localDataWiper.wipeAllFamilyData")
    }

    @Test
    fun cloudSyncWorker_failsClosedOnRestoreBlocked() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/work/CloudSyncWorker.kt",
            "src/main/java/com/familyhub/data/work/CloudSyncWorker.kt"
        )
        assertTrue(source.contains("RestoreBlockedException"))
        assertTrue(source.contains("Result.failure()"))
    }

    @Test
    fun coordinator_hashesThenDecryptsSameBytesInsideLock() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/restore/EncryptedRestoreCoordinator.kt",
            "src/main/java/com/familyhub/data/restore/EncryptedRestoreCoordinator.kt"
        )
        assertTrue(source.contains("readExactBounded"))
        assertTrue(source.contains("decryptFromBytes"))
        assertTrue(source.contains("applyValidatedRestoreSnapshot"))
        assertTrue(source.contains("@IoDispatcher"))
        assertTrue(source.contains("withContext(ioDispatcher)"))
        assertFalseContains(source, "decryptFromFile")
        assertFalseContains(source, "sha256Hex(file)")
        assertFalseContains(source, "settleDelayMs = 0L")
    }

    @Test
    fun codec_decryptsCiphertextInPlaceWithoutCopyOfRange() {
        val source = readSource(
            "app/src/main/java/com/familyhub/data/backup/EncryptedBackupCodec.kt",
            "src/main/java/com/familyhub/data/backup/EncryptedBackupCodec.kt"
        )
        assertTrue(source.contains("ciphertextOffset = EncryptedBackupFormat.HEADER_SIZE"))
        assertTrue(source.contains("decodeFromStream"))
        assertFalseContains(source, "copyOfRange(EncryptedBackupFormat.HEADER_SIZE")
        assertFalseContains(source, "plaintext.toString(StandardCharsets.UTF_8)")
    }

    private fun assertFalseContains(haystack: String, needle: String) {
        assertTrue("Did not expect: $needle", !haystack.contains(needle))
    }

    private fun extractFunction(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "Missing $signature" }
        var depth = 0
        var i = source.indexOf('{', start)
        val begin = i
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(begin, i + 1)
                    }
                }
            }
            i++
        }
        error("Unclosed function $signature")
    }

    private fun readSource(vararg candidates: String): String {
        val resolved = candidates
            .map { Paths.get(it) }
            .firstOrNull { Files.exists(it) }
            ?: error("Source file not found: ${candidates.joinToString()}")
        return String(Files.readAllBytes(resolved), StandardCharsets.UTF_8)
    }
}
