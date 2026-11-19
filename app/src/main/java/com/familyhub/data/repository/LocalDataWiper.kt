package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.DataWipeDao
import com.familyhub.data.sync.SyncChangeTracker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collection keys that represent goal/intent data which must survive partial family wipes.
 * Maps directly to the `collectionKey` column in `sync_outbox`.
 */
private val GOAL_COLLECTION_KEYS: Set<String> = setOf("familyGoals", "goalStages", "goalSteps")

/**
 * All non-goal collection keys whose outbox rows should be removed during partial wipe.
 * Derived from every [com.familyhub.data.sync.DIRECT_SYNC_TRIGGER_SPECS] collection key
 * that is NOT in [GOAL_COLLECTION_KEYS].
 */
private val NON_GOAL_OUTBOX_COLLECTION_KEYS: Set<String> = setOf(
    "members",
    "tasks",
    "shoppingLists",
    "shoppingCategories",
    "shoppingItems",
    "shoppingActivity",
    "events",
    "taskCompletions",
    "achievements",
    "rewards",
    "rewardRequests",
    "wallets",
    "taskRotations",
    "scores",
    "houseRules",
    "taskTemplates",
    "taskCategories",
    "appreciations",
    "emergencyContacts",
    "penalties",
    "recipes",
    "weeklyMenus",
    "menuEntries",
    "menuTemplates",
    "menuTemplateEntries",
    "dailyAssignments",
)

@Singleton
class LocalDataWiper @Inject constructor(
    private val database: FamilyHubDatabase,
    private val syncChangeTracker: SyncChangeTracker
    // AwesomenessDataStore intentionally not touched
) {
    suspend fun wipeAllExceptGoalsAndAwesomeness() {
        syncChangeTracker.runWithoutTracking {
            database.withTransaction {
                database.syncOutboxDao().beginTrackingSuppression()
                try {
                    wipeSharedFamilyTables(database.dataWipeDao(), includeGoals = false)
                    // Remove old outbox rows for wiped (non-goal) collections,
                    // preserving goal collection operations.
                    database.syncOutboxDao().deleteByCollectionKeys(NON_GOAL_OUTBOX_COLLECTION_KEYS)
                } finally {
                    database.syncOutboxDao().endTrackingSuppression()
                }
            }
        }
    }

    /** Full family wipe for sign-out — no personal goals or awesomeness retention. */
    suspend fun wipeAllFamilyData() {
        syncChangeTracker.runWithoutTracking {
            database.withTransaction {
                database.syncOutboxDao().beginTrackingSuppression()
                try {
                    wipeSharedFamilyTables(database.dataWipeDao(), includeGoals = true)
                    // Atomically clear every pre-existing outbox row.
                    database.syncOutboxDao().deleteAll()
                } finally {
                    database.syncOutboxDao().endTrackingSuppression()
                }
            }
        }
    }

    private suspend fun wipeSharedFamilyTables(dao: DataWipeDao, includeGoals: Boolean) {
        dao.clearMenuTemplateEntries()
        dao.clearMenuTemplates()
        dao.clearMenuEntries()
        dao.clearWeeklyMenus()

        dao.clearRecipeIngredients()
        dao.clearRecipeSteps()
        dao.clearRecipes()

        dao.clearShoppingActivity()
        dao.clearShoppingItems()
        dao.clearShoppingCategories()
        dao.clearShoppingLists()

        dao.clearTaskHistory()
        dao.clearTaskCompletions()
        dao.clearTaskRotations()
        dao.clearTaskTemplates()
        dao.clearTaskCategories()
        dao.clearTasks()

        dao.clearRewards()
        dao.clearRewardRequests()
        dao.clearUserScores()
        dao.clearUserAchievements()
        dao.clearUserWallets()

        dao.clearHouseRuleBlocks()
        dao.clearHouseRules()
        dao.clearAppreciations()
        dao.clearDailyAssignments()
        dao.clearEmergencyContacts()
        dao.clearPenalties()

        if (includeGoals) {
            dao.clearGoalSteps()
            dao.clearGoalStages()
            dao.clearFamilyGoals()
        }

        dao.clearFamilyEvents()
        dao.clearFamilyMembers()
    }
}
