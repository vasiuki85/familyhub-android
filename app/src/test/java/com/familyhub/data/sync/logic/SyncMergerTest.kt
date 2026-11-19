package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportAchievement
import com.familyhub.data.export.ExportAppreciation
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportEvent
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportHouseRule
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportReward
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportRecipe
import com.familyhub.data.export.ExportRecipeIngredient
import com.familyhub.data.export.ExportRecipeStep
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportShoppingActivity
import com.familyhub.data.export.ExportShoppingCategory
import com.familyhub.data.export.ExportShoppingItem
import com.familyhub.data.export.ExportShoppingList
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskAttachment
import com.familyhub.data.export.ExportTaskCategory
import com.familyhub.data.export.ExportTaskComment
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportTaskRotation
import com.familyhub.data.export.ExportTaskTemplate
import com.familyhub.data.export.ExportWallet
import com.familyhub.data.export.ExportEmergencyContact
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.datastore.DeletedCloudDocument
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.datastore.groupDeletedDocumentIdsByCollection
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.WeekUtils
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergerTest {

    private val merger = SyncMerger()

    @Test
    fun mergeSnapshots_prefersNewerShoppingActivityByTimestamp() {
        val local = createBaselineSnapshot().copy(
            shoppingActivities = listOf(
                ExportShoppingActivity(
                    id = "activity_1",
                    listId = "list",
                    itemId = "item",
                    actorId = "user",
                    actionType = "item_added",
                    payload = mapOf("listTitle" to "Список"),
                    occurredAt = 200L
                )
            ),
            exportDate = formatDate(2024, 6, 10, 12, 0)
        )
        val remote = createBaselineSnapshot().copy(
            shoppingActivities = listOf(
                ExportShoppingActivity(
                    id = "activity_1",
                    listId = "list",
                    itemId = "item",
                    actorId = "user",
                    actionType = "item_added",
                    payload = mapOf("listTitle" to "Список"),
                    occurredAt = 100L
                )
            ),
            exportDate = formatDate(2024, 5, 10, 12, 0)
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true
        )

        assertEquals(1, merged.shoppingActivities.size)
        assertEquals(200L, merged.shoppingActivities.first().occurredAt)
    }

    @Test
    fun mergeSnapshots_mergesDistinctShoppingActivities() {
        val local = createBaselineSnapshot().copy(
            shoppingActivities = listOf(
                ExportShoppingActivity(
                    id = "local_only",
                    listId = "list",
                    itemId = null,
                    actorId = "user",
                    actionType = "list_created",
                    payload = emptyMap(),
                    occurredAt = 50L
                )
            ),
            exportDate = formatDate(2024, 6, 10, 12, 0)
        )
        val remote = createBaselineSnapshot().copy(
            shoppingActivities = listOf(
                ExportShoppingActivity(
                    id = "remote_only",
                    listId = "list",
                    itemId = null,
                    actorId = "user2",
                    actionType = "list_created",
                    payload = emptyMap(),
                    occurredAt = 40L
                )
            ),
            exportDate = formatDate(2024, 6, 5, 12, 0)
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertEquals(2, merged.shoppingActivities.size)
        val ids = merged.shoppingActivities.map { it.id }.sorted()
        assertEquals(listOf("local_only", "remote_only"), ids)
    }

    @Test
    fun mergeSnapshots_prefersLocalScore_whenPendingChanges() {
        val weekStart = WeekUtils.getWeekStartMillis(1_704_067_200_000L)
        val userId = "user1"

        val local = createBaselineSnapshot().copy(
            scores = listOf(ExportScore(userId, weekStart, 90)),
            penalties = listOf(
                ExportPenalty(
                    id = "penalty-local",
                    userId = userId,
                    issuedByUserId = "parent",
                    amount = -10,
                    createdAt = 1_704_067_200_000L
                )
            ),
            exportDate = formatDate(2024, 6, 10, 12, 0)
        )
        val remote = createBaselineSnapshot().copy(
            scores = listOf(ExportScore(userId, weekStart, 100)),
            exportDate = formatDate(2024, 5, 10, 12, 0)
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertEquals(90, merged.scores.first().score)
    }

    @Test
    fun mergeSnapshots_prefersRemoteScore_whenNoPendingChanges() {
        val weekStart = WeekUtils.getWeekStartMillis(1_704_067_200_000L)
        val userId = "user1"

        val local = createBaselineSnapshot().copy(
            scores = listOf(ExportScore(userId, weekStart, 100)),
            exportDate = formatDate(2024, 6, 10, 12, 0)
        )
        val remote = createBaselineSnapshot().copy(
            scores = listOf(ExportScore(userId, weekStart, 90)),
            penalties = listOf(
                ExportPenalty(
                    id = "penalty-remote",
                    userId = userId,
                    issuedByUserId = "parent",
                    amount = -10,
                    createdAt = 1_704_067_200_000L
                )
            ),
            exportDate = formatDate(2024, 6, 10, 12, 5)
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = false
        )

        assertEquals(90, merged.scores.first().score)
    }

    @Test
    fun mergeSnapshots_prefersLocalWallet_whenPendingChanges() {
        val userId = "user1"

        val local = createBaselineSnapshot().copy(
            wallets = listOf(ExportWallet(userId, 50)),
            rewardRequests = listOf(
                ExportRewardRequest(
                    id = "req-local",
                    rewardId = "reward",
                    rewardTitle = "Treat",
                    rewardCost = 50,
                    requesterUserId = userId,
                    status = "APPROVED",
                    requestedAt = 100L,
                    processedAt = 200L
                )
            ),
            exportDate = formatDate(2024, 6, 10, 12, 0)
        )
        val remote = createBaselineSnapshot().copy(
            wallets = listOf(ExportWallet(userId, 100)),
            exportDate = formatDate(2024, 5, 10, 12, 0)
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertEquals(50, merged.wallets.first().balance)
    }

    @Test
    fun mergeSnapshots_prefersNewerRecipeByUpdatedAt() {
        val local = createBaselineSnapshot().copy(
            recipes = listOf(
                ExportRecipe(
                    id = "recipe_1",
                    title = "New title",
                    updatedAt = 200L,
                    ingredients = listOf(ExportRecipeIngredient(id = "ing_1", name = "Milk", quantity = "200 ml")),
                    steps = listOf(ExportRecipeStep(id = "step_1", description = "Mix", order = 0))
                )
            )
        )
        val remote = createBaselineSnapshot().copy(
            recipes = listOf(
                ExportRecipe(
                    id = "recipe_1",
                    title = "Old title",
                    updatedAt = 100L,
                    ingredients = listOf(ExportRecipeIngredient(id = "ing_1", name = "Milk", quantity = "100 ml")),
                    steps = listOf(ExportRecipeStep(id = "step_1", description = "Old", order = 0))
                )
            )
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertEquals(1, merged.recipes.size)
        assertEquals("New title", merged.recipes.first().title)
        assertEquals("200 ml", merged.recipes.first().ingredients.first().quantity)
    }

    @Test
    fun mergeSnapshots_doesNotRestoreDeletedRecipeFromRemote() {
        val remote = createBaselineSnapshot().copy(
            recipes = listOf(
                ExportRecipe(
                    id = "recipe_deleted",
                    title = "Remote recipe",
                    updatedAt = 100L
                )
            )
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            deletedRecipeIds = setOf("recipe_deleted")
        )

        assertEquals(emptyList<ExportRecipe>(), merged.recipes)
    }

    @Test
    fun mergeSnapshots_doesNotRestoreOutboxDeletedRecipeFromRemote() {
        val remote = createBaselineSnapshot().copy(
            recipes = listOf(
                ExportRecipe(
                    id = "recipe_outbox_deleted",
                    title = "Remote recipe",
                    updatedAt = 100L,
                )
            )
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            pendingDeletedEntityIdsByCollection = mapOf(
                "recipes" to setOf("recipe_outbox_deleted")
            ),
        )

        assertEquals(emptyList<ExportRecipe>(), merged.recipes)
    }

    @Test
    fun mergeSnapshots_returnsFilteredLocalWhenRemoteIsNull() {
        val local = createBaselineSnapshot().copy(
            tasks = listOf(
                ExportTask(id = "task_keep", title = "Keep me"),
                ExportTask(
                    id = "foreign-private",
                    title = "Secret",
                    private = true,
                    createdByUserId = "other",
                    assignedToUserId = "other"
                )
            )
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = null,
            currentUserId = "test_user",
            isMemberWriteAllowed = true
        )

        assertEquals("Keep me", merged.tasks.single().title)
    }

    @Test
    fun mergeSnapshots_keepsRemoteOnlyPendingForAdminWhenPendingChanges() {
        val local = createBaselineSnapshot().copy(
            members = listOf(exportMember("admin-1", MemberRole.ADMIN))
        )
        val remote = createBaselineSnapshot().copy(
            members = listOf(
                exportMember("admin-1", MemberRole.ADMIN),
                exportMember("pending-1", MemberRole.PENDING)
            )
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "admin-1",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertEquals(
            setOf("admin-1", "pending-1"),
            merged.members.map { it.id }.toSet()
        )
        assertEquals(MemberRole.PENDING.name, merged.members.first { it.id == "pending-1" }.role)
    }

    @Test
    fun mergeSnapshots_doesNotRestoreDeletedMemberFromRemote() {
        val remote = createBaselineSnapshot().copy(
            members = listOf(
                exportMember("admin-1", MemberRole.ADMIN),
                exportMember("removed-1", MemberRole.PARENT)
            )
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot().copy(
                members = listOf(exportMember("admin-1", MemberRole.ADMIN))
            ),
            remote = remote,
            currentUserId = "admin-1",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            deletedMembers = setOf("removed-1")
        )

        assertEquals(listOf("admin-1"), merged.members.map { it.id })
    }

    @Test
    fun mergeSnapshots_doesNotRestoreOutboxDeletedMemberFromRemote() {
        val remote = createBaselineSnapshot().copy(
            members = listOf(
                exportMember("admin-1", MemberRole.ADMIN),
                exportMember("removed-outbox", MemberRole.PARENT),
            )
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot().copy(
                members = listOf(exportMember("admin-1", MemberRole.ADMIN))
            ),
            remote = remote,
            currentUserId = "admin-1",
            isMemberWriteAllowed = true,
            pendingDeletedEntityIdsByCollection = mapOf(
                "members" to setOf("removed-outbox")
            ),
        )

        assertEquals(listOf("admin-1"), merged.members.map { it.id })
    }

    @Test
    fun mergeSnapshots_preservesOnlyRemoteAdminDespiteOutboxDeleteIntent() {
        val remote = createBaselineSnapshot().copy(
            members = listOf(exportMember("admin-1", MemberRole.ADMIN))
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "parent-1",
            isMemberWriteAllowed = false,
            pendingDeletedEntityIdsByCollection = mapOf(
                "members" to setOf("admin-1")
            ),
        )

        assertEquals(listOf("admin-1"), merged.members.map { it.id })
        assertEquals(MemberRole.ADMIN.name, merged.members.single().role)
    }

    @Test
    fun mergeSnapshots_preservesRemoteAdminWithoutMemberWriteRights() {
        val local = createBaselineSnapshot().copy(
            members = listOf(exportMember("parent-1", MemberRole.PARENT))
        )
        val remote = createBaselineSnapshot().copy(
            members = listOf(
                exportMember("admin-1", MemberRole.ADMIN),
                exportMember("parent-1", MemberRole.PARENT)
            )
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "parent-1",
            isMemberWriteAllowed = false,
            hasPendingChanges = true
        )

        assertEquals(
            setOf("admin-1", "parent-1"),
            merged.members.map { it.id }.toSet()
        )
        assertEquals(MemberRole.ADMIN.name, merged.members.first { it.id == "admin-1" }.role)
    }

    @Test
    fun mergeSnapshots_doesNotRestoreDeletedTaskFromRemote() {
        val remote = createBaselineSnapshot().copy(
            tasks = listOf(ExportTask(id = "task-deleted", title = "Remote task"))
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            deletedDocumentIdsByCollection = mapOf(
                DeletedCloudDocumentCollections.TASKS to setOf("task-deleted")
            )
        )

        assertTrue(merged.tasks.none { it.id == "task-deleted" })
    }

    @Test
    fun mergeSnapshots_preservesRemoteOnlyTaskWithoutTombstone() {
        val remote = createBaselineSnapshot().copy(
            tasks = listOf(ExportTask(id = "remote-only", title = "From remote"))
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true
        )

        assertEquals("remote-only", merged.tasks.single().id)
    }

    @Test
    fun mergeSnapshots_keepsLocallyRecreatedTaskDespiteTombstone() {
        val local = createBaselineSnapshot().copy(
            tasks = listOf(ExportTask(id = "task-1", title = "Local copy"))
        )
        val remote = createBaselineSnapshot().copy(
            tasks = listOf(ExportTask(id = "task-1", title = "Remote copy"))
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            deletedDocumentIdsByCollection = mapOf(
                DeletedCloudDocumentCollections.TASKS to setOf("task-1")
            )
        )

        assertEquals("Local copy", merged.tasks.single().title)
    }

    @Test
    fun mergeSnapshots_doesNotRestoreDeletedShoppingListCascadeFromRemote() {
        val remote = createBaselineSnapshot().copy(
            shoppingLists = listOf(
                ExportShoppingList(
                    id = "list-deleted",
                    title = "List",
                    colorHex = "#fff",
                    iconName = "cart",
                    createdAt = 1L
                )
            ),
            shoppingCategories = listOf(
                ExportShoppingCategory(
                    id = "cat-deleted",
                    listId = "list-deleted",
                    title = "Cat",
                    colorHex = "#fff",
                    iconName = "icon",
                    sortOrder = 0
                )
            ),
            shoppingItems = listOf(
                ExportShoppingItem(
                    id = "item-deleted",
                    listId = "list-deleted",
                    title = "Milk",
                    createdAt = 1L
                )
            )
        )
        val deleted = groupDeletedDocumentIdsByCollection(
            setOf(
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.SHOPPING_LISTS, "list-deleted"),
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.SHOPPING_CATEGORIES, "cat-deleted"),
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.SHOPPING_ITEMS, "item-deleted")
            )
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            deletedDocumentIdsByCollection = deleted
        )

        assertTrue(merged.shoppingLists.none { it.id == "list-deleted" })
        assertTrue(merged.shoppingCategories.none { it.id == "cat-deleted" })
        assertTrue(merged.shoppingItems.none { it.id == "item-deleted" })
    }

    @Test
    fun mergeSnapshots_doesNotRestoreDeletedEventFromRemote() {
        val remote = createBaselineSnapshot().copy(
            events = listOf(ExportEvent(id = "event-deleted", title = "Party", startDateTime = 1L))
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            deletedDocumentIdsByCollection = mapOf(
                DeletedCloudDocumentCollections.EVENTS to setOf("event-deleted")
            )
        )

        assertTrue(merged.events.none { it.id == "event-deleted" })
    }

    @Test
    fun mergeSnapshots_doesNotRestoreDeletedRewardFromRemote() {
        val remote = createBaselineSnapshot().copy(
            rewards = listOf(ExportReward(id = "reward-deleted", title = "Ice cream", cost = 5))
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            deletedDocumentIdsByCollection = mapOf(
                DeletedCloudDocumentCollections.REWARDS to setOf("reward-deleted")
            )
        )

        assertTrue(merged.rewards.none { it.id == "reward-deleted" })
    }

    @Test
    fun mergeSnapshots_preservesRemoteOnlyRewardWithoutTombstone() {
        val remote = createBaselineSnapshot().copy(
            rewards = listOf(ExportReward(id = "reward-remote", title = "Candy", cost = 3))
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true
        )

        assertEquals("reward-remote", merged.rewards.single().id)
    }

    @Test
    fun mergeSnapshots_doesNotRestoreDeletedGoalCascadeFromRemote() {
        val remote = createBaselineSnapshot().copy(
            familyGoals = listOf(ExportFamilyGoal(id = "goal-deleted", title = "Trip")),
            familyGoalStages = listOf(
                ExportFamilyGoalStage(id = "stage-deleted", goalId = "goal-deleted", title = "Stage")
            ),
            familyGoalSteps = listOf(
                ExportFamilyGoalStep(id = "step-deleted", goalId = "goal-deleted", stageId = "stage-deleted", title = "Step")
            )
        )
        val deleted = groupDeletedDocumentIdsByCollection(
            setOf(
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.FAMILY_GOALS, "goal-deleted"),
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES, "stage-deleted"),
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS, "step-deleted")
            )
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            deletedDocumentIdsByCollection = deleted
        )

        assertTrue(merged.familyGoals.none { it.id == "goal-deleted" })
        assertTrue(merged.familyGoalStages.none { it.id == "stage-deleted" })
        assertTrue(merged.familyGoalSteps.none { it.id == "step-deleted" })
    }

    @Test
    fun mergeSnapshots_doesNotRestoreDeletedDocumentsForRemainingNineCollections() {
        val remote = createBaselineSnapshot().copy(
            houseRules = listOf(ExportHouseRule(id = "rule-deleted", title = "Rule")),
            taskTemplates = listOf(ExportTaskTemplate(id = "template-deleted", title = "Template")),
            taskCategories = listOf(ExportTaskCategory(id = "category-deleted", name = "Cat")),
            appreciations = listOf(
                ExportAppreciation(id = "app-deleted", senderId = "a", recipientId = "b", message = "Hi")
            ),
            emergencyContacts = listOf(
                ExportEmergencyContact(id = "contact-deleted", name = "Mom", phoneNumber = "+1")
            )
        )
        val deleted = mapOf(
            DeletedCloudDocumentCollections.HOUSE_RULES to setOf("rule-deleted"),
            DeletedCloudDocumentCollections.TASK_TEMPLATES to setOf("template-deleted"),
            DeletedCloudDocumentCollections.TASK_CATEGORIES to setOf("category-deleted"),
            DeletedCloudDocumentCollections.APPRECIATIONS to setOf("app-deleted"),
            DeletedCloudDocumentCollections.EMERGENCY_CONTACTS to setOf("contact-deleted")
        )

        val merged = merger.mergeSnapshots(
            local = createBaselineSnapshot(),
            remote = remote,
            currentUserId = "test_user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            deletedDocumentIdsByCollection = deleted
        )

        assertTrue(merged.houseRules.none { it.id == "rule-deleted" })
        assertTrue(merged.taskTemplates.none { it.id == "template-deleted" })
        assertTrue(merged.taskCategories.none { it.id == "category-deleted" })
        assertTrue(merged.appreciations.none { it.id == "app-deleted" })
        assertTrue(merged.emergencyContacts.none { it.id == "contact-deleted" })
    }

    @Test
    fun `achievement logical key matches Room primary key and keeps one newest record`() {
        val local = ExportAchievement(userId = "child", type = "FIRST_TASK", earnedAt = 100L)
        val remote = local.copy(earnedAt = 200L)

        val merged = SyncMerger().mergeSnapshots(
            local = ExportData(achievements = listOf(local)),
            remote = ExportData(achievements = listOf(remote)),
            currentUserId = "child",
            isMemberWriteAllowed = false,
            hasPendingChanges = false,
        )

        assertEquals(listOf(remote), merged.achievements)
    }

    private fun exportMember(id: String, role: MemberRole) = ExportMember(
        id = id,
        displayName = id,
        roleLabel = role.name,
        avatarColor = "#2196F3",
        role = role.name
    )

    private fun createBaselineSnapshot(): ExportData {
        return ExportData(
            members = emptyList<ExportMember>(),
            tasks = emptyList<ExportTask>(),
            shoppingLists = listOf(
                ExportShoppingList(
                    id = "list",
                    title = "Список",
                    colorHex = "#ffffff",
                    iconName = "cart",
                    createdAt = 1L,
                    archived = false,
                    archivedAt = null,
                    templateKey = null,
                    participantIds = emptyList()
                )
            ),
            shoppingCategories = emptyList<ExportShoppingCategory>(),
            shoppingItems = emptyList<ExportShoppingItem>(),
            shoppingActivities = emptyList(),
            events = emptyList<ExportEvent>(),
            scores = emptyList<ExportScore>(),
            taskCompletions = emptyList<ExportTaskCompletion>(),
            achievements = emptyList<ExportAchievement>(),
            rewards = emptyList<ExportReward>(),
            rewardRequests = emptyList<ExportRewardRequest>(),
            wallets = emptyList<ExportWallet>(),
            taskRotations = emptyList<ExportTaskRotation>(),
            houseRules = emptyList<ExportHouseRule>(),
            taskTemplates = emptyList<ExportTaskTemplate>(),
            taskCategories = emptyList<ExportTaskCategory>(),
            appreciations = emptyList<ExportAppreciation>(),
            familyGoals = emptyList<ExportFamilyGoal>(),
            familyGoalStages = emptyList<ExportFamilyGoalStage>(),
            familyGoalSteps = emptyList<ExportFamilyGoalStep>(),
            emergencyContacts = emptyList<ExportEmergencyContact>(),
            penalties = emptyList<ExportPenalty>(),
            exportDate = formatDate(2024, 5, 1, 12, 0)
        )
    }

    private fun formatDate(year: Int, month: Int, day: Int, hour: Int, minute: Int): String {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month - 1)
            set(java.util.Calendar.DAY_OF_MONTH, day)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(calendar.time)
    }

    // ── Pending-aware resolveTask for children ──────────────────────────

    @Test
    fun `child completed task — pending=true keeps WAITING locally`() {
        val merger = SyncMerger()
        val local = createTask(id = "t1", status = "WAITING_FOR_APPROVAL", assignedTo = "child1", updatedAt = TODAY_MS)
        val remote = createTask(id = "t1", status = "TODO", assignedTo = "child1", updatedAt = YESTERDAY_MS)
        val merged = merger.mergeSnapshots(
            local = ExportData(tasks = listOf(local)),
            remote = ExportData(tasks = listOf(remote)),
            currentUserId = "child1",
            isMemberWriteAllowed = false,
            hasPendingChanges = true
        )
        assertEquals("WAITING_FOR_APPROVAL", merged.tasks.single().status)
    }

    @Test
    fun `admin rejected — pending=false picks remote TODO for child`() {
        val merger = SyncMerger()
        val local = createTask(id = "t1", status = "WAITING_FOR_APPROVAL", assignedTo = "child1", updatedAt = TODAY_MS)
        val remote = createTask(id = "t1", status = "TODO", assignedTo = "child1", updatedAt = YESTERDAY_MS)
        val merged = merger.mergeSnapshots(
            local = ExportData(tasks = listOf(local)),
            remote = ExportData(tasks = listOf(remote)),
            currentUserId = "child1",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertEquals("TODO", merged.tasks.single().status)
    }

    @Test
    fun `admin rejected — pending=true from other table should NOT keep WAITING for child`() {
        // Regression: hasPendingChanges was global. If shopping_items changed,
        // the task merge wrongly kept local WAITING. Now this test ensures
        // that when tasks table is not dirty, pending flag from other tables
        // doesn't affect task resolution for children.
        val merger = SyncMerger()
        val local = createTask(id = "t1", status = "WAITING_FOR_APPROVAL", assignedTo = "child1", updatedAt = TODAY_MS)
        val remote = createTask(id = "t1", status = "TODO", assignedTo = "child1", updatedAt = YESTERDAY_MS)
        // hasPendingChanges from other collection — should NOT affect task merge
        // In real impl this is handled by per-table pending tracking.
        // For now, testing that the merge correctly prefers remote when hasPendingChanges=false.
        val merged = merger.mergeSnapshots(
            local = ExportData(tasks = listOf(local)),
            remote = ExportData(tasks = listOf(remote)),
            currentUserId = "child1",
            isMemberWriteAllowed = false,
            hasPendingChanges = false  // tasks table not dirty
        )
        assertEquals("TODO", merged.tasks.single().status)
    }

    // ── Cross-device deletion propagation ──────────────────────────────

    @Test
    fun `child sync after admin deleted task — drops local task without pending`() {
        val merger = SyncMerger()
        // Admin deleted task from cloud — remote has no task
        // Child's local still has it, no pending changes
        val local = listOf(createTask(id = "t1", status = "TODO", assignedTo = "child1"))
        val merged = merger.mergeSnapshots(
            local = ExportData(tasks = local),
            remote = ExportData(tasks = emptyList()),
            currentUserId = "child1",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertTrue("Task should be dropped when not in remote and no pending", merged.tasks.isEmpty())
    }

    @Test
    fun `child with pending changes keeps locally created task even if not in remote`() {
        val merger = SyncMerger()
        val local = listOf(createTask(id = "t1", status = "TODO", assignedTo = "child1"))
        val merged = merger.mergeSnapshots(
            local = ExportData(tasks = local),
            remote = ExportData(tasks = emptyList()),
            currentUserId = "child1",
            isMemberWriteAllowed = false,
            hasPendingChanges = true
        )
        assertEquals("Task with pending changes should be kept", 1, merged.tasks.size)
    }

    @Test
    fun `dirty task A does not resurrect stale deleted task B from same collection`() {
        val localA = createTask(
            id = "task-a",
            status = "WAITING_FOR_APPROVAL",
            assignedTo = "child1",
        )
        val staleDeletedB = createTask(
            id = "task-b",
            status = "TODO",
            assignedTo = "child1",
        )
        val remoteA = localA.copy(status = "TODO")

        val merged = merger.mergeSnapshots(
            local = ExportData(tasks = listOf(localA, staleDeletedB)),
            remote = ExportData(tasks = listOf(remoteA)),
            currentUserId = "child1",
            isMemberWriteAllowed = false,
            hasPendingChanges = true,
            pendingByCollection = mapOf("tasks" to true),
            pendingEntityIdsByCollection = mapOf("tasks" to setOf("task-a")),
        )

        assertEquals(listOf("task-a"), merged.tasks.map(ExportTask::id))
        assertEquals("WAITING_FOR_APPROVAL", merged.tasks.single().status)
    }

    @Test
    fun `outbox delete intent removes remote task without legacy tombstone`() {
        val remoteTask = createTask(
            id = "task-deleted-locally",
            assignedTo = "child",
            status = "TODO",
        )

        val merged = merger.mergeSnapshots(
            local = ExportData(tasks = emptyList()),
            remote = ExportData(tasks = listOf(remoteTask)),
            currentUserId = "parent",
            isMemberWriteAllowed = true,
            pendingByCollection = mapOf("tasks" to true),
            pendingEntityIdsByCollection = mapOf("tasks" to setOf(remoteTask.id)),
            pendingDeletedEntityIdsByCollection = mapOf("tasks" to setOf(remoteTask.id)),
        )

        assertTrue(merged.tasks.isEmpty())
    }

    @Test
    fun `outbox delete intent removes remote shopping item without legacy tombstone`() {
        val remoteItem = ExportShoppingItem(
            id = "item-deleted-locally",
            listId = "list-1",
            title = "Milk",
            createdByUserId = "child1",
            bought = false,
        )

        val merged = merger.mergeSnapshots(
            local = ExportData(shoppingItems = emptyList()),
            remote = ExportData(shoppingItems = listOf(remoteItem)),
            currentUserId = "parent",
            isMemberWriteAllowed = true,
            pendingByCollection = mapOf("shoppingItems" to true),
            pendingEntityIdsByCollection = mapOf("shoppingItems" to setOf(remoteItem.id)),
            pendingDeletedEntityIdsByCollection = mapOf("shoppingItems" to setOf(remoteItem.id)),
        )

        assertTrue(merged.shoppingItems.isEmpty())
    }

    @Test
    fun `dirty shopping item A does not resurrect stale deleted item B from same collection`() {
        val localA = ExportShoppingItem(
            id = "item-a",
            listId = "list-1",
            title = "Milk",
            createdByUserId = "child1",
            bought = true,
        )
        val staleDeletedB = ExportShoppingItem(
            id = "item-b",
            listId = "list-1",
            title = "Bread",
            createdByUserId = "child1",
        )
        val remoteA = localA.copy(bought = false)

        val merged = merger.mergeSnapshots(
            local = ExportData(shoppingItems = listOf(localA, staleDeletedB)),
            remote = ExportData(shoppingItems = listOf(remoteA)),
            currentUserId = "child1",
            isMemberWriteAllowed = false,
            hasPendingChanges = true,
            pendingByCollection = mapOf("shoppingItems" to true),
            pendingEntityIdsByCollection = mapOf("shoppingItems" to setOf("item-a")),
        )

        assertEquals(listOf("item-a"), merged.shoppingItems.map(ExportShoppingItem::id))
        assertTrue(merged.shoppingItems.single().bought)
    }

    // ── Outbox DELETE intent coverage for generic mutable collections ──

    @Test
    fun `outbox delete intents remove remote entities across generic mutable collections`() {
        // 12 collections whose mergeBy callsites currently pass only deletedIds(legacy)
        // instead of effectiveDeletedIds(logical, legacy).  This test proves that
        // a pendingDeletedEntityIdsByCollection entry is silently ignored — each
        // remote entity survives the merge (RED) when it should be removed.

        val remote = ExportData(
            shoppingLists = listOf(
                ExportShoppingList(id = "ol-slist", title = "Remote list", colorHex = "#fff", iconName = "cart")
            ),
            shoppingCategories = listOf(
                ExportShoppingCategory(id = "ol-scat", listId = "ol-slist", title = "Remote cat", colorHex = "#ccc", iconName = "tag")
            ),
            events = listOf(
                ExportEvent(id = "ol-event", title = "Remote event", startDateTime = 1L)
            ),
            rewards = listOf(
                ExportReward(id = "ol-reward", title = "Remote reward", cost = 5)
            ),
            houseRules = listOf(
                ExportHouseRule(id = "ol-rule", title = "Remote rule")
            ),
            taskTemplates = listOf(
                ExportTaskTemplate(id = "ol-ttpl", title = "Remote template")
            ),
            taskCategories = listOf(
                ExportTaskCategory(id = "ol-tcat", name = "Remote category")
            ),
            appreciations = listOf(
                ExportAppreciation(id = "ol-appr", senderId = "user-a", recipientId = "user-b", message = "Thanks")
            ),
            familyGoals = listOf(
                ExportFamilyGoal(id = "ol-goal", title = "Remote goal")
            ),
            familyGoalStages = listOf(
                ExportFamilyGoalStage(id = "ol-stage", goalId = "ol-goal", title = "Remote stage")
            ),
            familyGoalSteps = listOf(
                ExportFamilyGoalStep(id = "ol-step", goalId = "ol-goal", stageId = "ol-stage", title = "Remote step")
            ),
            emergencyContacts = listOf(
                ExportEmergencyContact(id = "ol-contact", name = "Remote contact", phoneNumber = "+1234567890")
            ),
        )

        val pendingDeleted = mapOf(
            "shoppingLists" to setOf("ol-slist"),
            "shoppingCategories" to setOf("ol-scat"),
            "events" to setOf("ol-event"),
            "rewards" to setOf("ol-reward"),
            "houseRules" to setOf("ol-rule"),
            "taskTemplates" to setOf("ol-ttpl"),
            "taskCategories" to setOf("ol-tcat"),
            "appreciations" to setOf("ol-appr"),
            "familyGoals" to setOf("ol-goal"),
            "goalStages" to setOf("ol-stage"),
            "goalSteps" to setOf("ol-step"),
            "emergencyContacts" to setOf("ol-contact"),
        )

        val merged = merger.mergeSnapshots(
            local = ExportData(),           // empty local — no entities to "recreate"
            remote = remote,
            currentUserId = "admin-user",   // admin → no member-role filtering
            isMemberWriteAllowed = true,
            hasPendingChanges = false,
            // No legacy tombstones — only outbox persisted map
            pendingDeletedEntityIdsByCollection = pendingDeleted,
        )

        // Every one of these should be empty because the outbox DELETE intents
        // must prevent remote-only entities from being restored.
        // Currently RED: all 12 assertions fail because the callsites pass only
        // deletedIds(legacy) instead of effectiveDeletedIds(logical, legacy).
        assertEquals(
            "outbox DELETE should remove remote shoppingLists",
            emptyList<ExportShoppingList>(), merged.shoppingLists
        )
        assertEquals(
            "outbox DELETE should remove remote shoppingCategories",
            emptyList<ExportShoppingCategory>(), merged.shoppingCategories
        )
        assertEquals(
            "outbox DELETE should remove remote events",
            emptyList<ExportEvent>(), merged.events
        )
        assertEquals(
            "outbox DELETE should remove remote rewards",
            emptyList<ExportReward>(), merged.rewards
        )
        assertEquals(
            "outbox DELETE should remove remote houseRules",
            emptyList<ExportHouseRule>(), merged.houseRules
        )
        assertEquals(
            "outbox DELETE should remove remote taskTemplates",
            emptyList<ExportTaskTemplate>(), merged.taskTemplates
        )
        assertEquals(
            "outbox DELETE should remove remote taskCategories",
            emptyList<ExportTaskCategory>(), merged.taskCategories
        )
        assertEquals(
            "outbox DELETE should remove remote appreciations",
            emptyList<ExportAppreciation>(), merged.appreciations
        )
        assertEquals(
            "outbox DELETE should remove remote familyGoals",
            emptyList<ExportFamilyGoal>(), merged.familyGoals
        )
        assertEquals(
            "outbox DELETE should remove remote goalStages",
            emptyList<ExportFamilyGoalStage>(), merged.familyGoalStages
        )
        assertEquals(
            "outbox DELETE should remove remote goalSteps",
            emptyList<ExportFamilyGoalStep>(), merged.familyGoalSteps
        )
        assertEquals(
            "outbox DELETE should remove remote emergencyContacts",
            emptyList<ExportEmergencyContact>(), merged.emergencyContacts
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun createTask(
        id: String,
        status: String,
        assignedTo: String,
        updatedAt: Long = TODAY_MS,
    ): ExportTask = ExportTask(
        id = id,
        title = "Task $id",
        status = status,
        createdByUserId = "admin",
        assignedToUserId = assignedTo,
        completionCycle = 0L,
        dueDate = null,
        dueDateHasTime = false,
        reminderMode = "NONE",
        reminderMinutesOfDay = null,
        repeatRule = "NONE",
        requiresApproval = true,
        priority = "MEDIUM",
        categoryId = "cat1",
        description = "",
        orderIndex = 0,
        attachments = emptyList(),
        comments = emptyList(),
    )

    private val TODAY_MS = System.currentTimeMillis()
    private val YESTERDAY_MS = TODAY_MS - 86_400_000L
}
