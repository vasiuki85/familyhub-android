package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.RewardDao
import com.familyhub.data.local.db.dao.RewardRequestDao
import com.familyhub.data.local.entity.RewardEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.Reward
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

class RewardRepositoryTombstoneTest {

    private val rewardDao = mockk<RewardDao>(relaxed = true)
    private val rewardRequestDao = mockk<RewardRequestDao>(relaxed = true)
    private val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
    private lateinit var repository: RewardRepository

    @Before
    fun setUp() {
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(
            SyncSettings(
                isEnabled = true,
                accountId = "family-1",
                deviceId = "device-1",
                lastSyncAt = null,
                hasPendingChanges = false,
                lastError = null,
                googleCalendarId = null
            )
        )
        coEvery { syncSettingsDataStore.addDeletedDocuments(any()) } just Runs
        coEvery { syncSettingsDataStore.removeDeletedDocuments(any()) } just Runs
        coEvery { rewardDao.delete(any<RewardEntity>()) } just Runs
        coEvery { rewardDao.upsert(any<RewardEntity>()) } just Runs
        repository = RewardRepository(rewardDao, rewardRequestDao, CloudDeletionLedger(syncSettingsDataStore))
    }

    @Test
    fun deleteReward_recordsTombstoneBeforeDaoDelete() = runTest {
        val reward = Reward("reward-1", "Ice cream", null, 10, "user-1", true, 1L)

        repository.deleteReward(reward)

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(any())
            rewardDao.delete(any<RewardEntity>())
        }
    }

    @Test
    fun upsertReward_clearsTombstoneAfterDaoUpsert() = runTest {
        val reward = Reward("reward-1", "Ice cream", null, 10, "user-1", true, 1L)

        repository.upsertReward(reward)

        coVerifyOrder {
            rewardDao.upsert(any<RewardEntity>())
            syncSettingsDataStore.removeDeletedDocuments(any())
        }
    }
}
