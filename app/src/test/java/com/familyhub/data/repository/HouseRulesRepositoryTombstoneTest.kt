package com.familyhub.data.repository

import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.HouseRuleDao
import com.familyhub.data.local.entity.HouseRuleEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.HouseRule
import com.familyhub.domain.model.SyncSettings
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class HouseRulesRepositoryTombstoneTest {

    private val dao = mockk<HouseRuleDao>(relaxed = true)
    private val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
    private lateinit var repository: HouseRulesRepository

    @Before
    fun setUp() {
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(pausedFamilySettings())
        coEvery { syncSettingsDataStore.addDeletedDocuments(any()) } just Runs
        coEvery { syncSettingsDataStore.removeDeletedDocuments(any()) } just Runs
        coEvery { dao.deleteById(any()) } just Runs
        coEvery { dao.upsert(any<HouseRuleEntity>()) } just Runs
        repository = HouseRulesRepository(dao, CloudDeletionLedger(syncSettingsDataStore))
    }

    @Test
    fun delete_recordsTombstoneBeforeDaoDelete() = runTest {
        val rule = HouseRule("rule-1", "Quiet hours", null, "user-1", 1L, 1L)

        repository.delete(rule)

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(any())
            dao.deleteById("rule-1")
        }
    }

    @Test
    fun upsert_clearsTombstoneAfterDaoUpsert() = runTest {
        val rule = HouseRule("rule-1", "Quiet hours", null, "user-1", 1L, 1L)

        repository.upsert(rule)

        coVerifyOrder {
            dao.upsert(any<HouseRuleEntity>())
            syncSettingsDataStore.removeDeletedDocuments(any())
        }
    }

    private fun pausedFamilySettings() = SyncSettings(
        isEnabled = false,
        accountId = "family-1",
        deviceId = "device-1",
        lastSyncAt = 1L,
        hasPendingChanges = false,
        lastError = null,
        googleCalendarId = null
    )
}
