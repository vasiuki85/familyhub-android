package com.familyhub.data.sync

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportAchievement
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportEvent
import com.familyhub.data.export.ExportShoppingCategory
import com.familyhub.data.export.ExportShoppingItem
import com.familyhub.data.export.ExportShoppingList
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportRecipe
import com.familyhub.data.export.ExportReward
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportWallet
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportTaskHistory
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportTask
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.domain.model.MemberRole
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreBackendApiUploadValidationTest {

    @Test
    fun computeCollectionWriteDiff_deletesRemovedIdsAndSetsNewOrChangedItems() {
        val diff = computeCollectionWriteDiff(
            previousItems = listOf("keep", "remove"),
            targetItems = listOf("keep", "new"),
            idSelector = { it }
        )

        assertEquals(listOf("remove"), diff.deleteIds)
        assertEquals(listOf("new" to "new"), diff.sets)
    }

    @Test
    fun computeCollectionWriteDiff_ignoresUnchangedItems() {
        val diff = computeCollectionWriteDiff(
            previousItems = listOf("same", "old"),
            targetItems = listOf("same", "old"),
            idSelector = { it }
        )

        assertEquals(emptyList<String>(), diff.deleteIds)
        assertEquals(emptyList<Pair<String, String>>(), diff.sets)
    }

    @Test
    fun countUploadWriteOperations_includesFamilyUpdatedAt() {
        val operations = listOf(
            PlannedUploadOperation.Delete("tasks", "task-1"),
            PlannedUploadOperation.Set("tasks", "task-2", data = mapOf("id" to "task-2"))
        )

        assertEquals(3, countUploadWriteOperations(operations, includesFamilyUpdatedAt = true))
        assertEquals(2, countUploadWriteOperations(operations, includesFamilyUpdatedAt = false))
    }

    @Test
    fun isUploadWithinTransactionLimit_rejectsSnapshotsAboveFirestoreCap() {
        assertFalse(isUploadWithinTransactionLimit(MAX_TRANSACTION_WRITE_OPERATIONS + 1))
        assertTrue(isUploadWithinTransactionLimit(MAX_TRANSACTION_WRITE_OPERATIONS))
    }

    @Test
    fun isSnapshotVersionCompatible_versionZeroWhenFamilyAbsent() {
        assertEquals(
            true,
            isSnapshotVersionCompatible(
                snapshotVersion = 0L,
                snapshotVersionNanos = 0,
                familyExists = false,
                updatedAt = null
            )
        )
    }

    @Test
    fun isSnapshotVersionCompatible_versionZeroWhenLegacyFamilyWithoutUpdatedAt() {
        assertEquals(
            true,
            isSnapshotVersionCompatible(
                snapshotVersion = 0L,
                snapshotVersionNanos = 0,
                familyExists = true,
                updatedAt = null
            )
        )
    }

    @Test
    fun isSnapshotVersionCompatible_versionZeroRejectedForVersionedFamily() {
        assertEquals(
            false,
            isSnapshotVersionCompatible(
                snapshotVersion = 0L,
                snapshotVersionNanos = 0,
                familyExists = true,
                updatedAt = Timestamp(10L, 5)
            )
        )
    }

    @Test
    fun isSnapshotVersionCompatible_negativeVersionRejected() {
        assertEquals(
            false,
            isSnapshotVersionCompatible(
                snapshotVersion = -1L,
                snapshotVersionNanos = 0,
                familyExists = false,
                updatedAt = null
            )
        )
    }

    @Test
    fun isSnapshotVersionCompatible_matchingSecondsAndNanos() {
        assertEquals(
            true,
            isSnapshotVersionCompatible(
                snapshotVersion = 10L,
                snapshotVersionNanos = 5,
                familyExists = true,
                updatedAt = Timestamp(10L, 5)
            )
        )
    }

    @Test
    fun isSnapshotVersionCompatible_mismatchedSeconds() {
        assertEquals(
            false,
            isSnapshotVersionCompatible(
                snapshotVersion = 10L,
                snapshotVersionNanos = 5,
                familyExists = true,
                updatedAt = Timestamp(9L, 5)
            )
        )
    }

    @Test
    fun isSnapshotVersionCompatible_mismatchedNanosWithMatchingSeconds() {
        assertEquals(
            false,
            isSnapshotVersionCompatible(
                snapshotVersion = 10L,
                snapshotVersionNanos = 5,
                familyExists = true,
                updatedAt = Timestamp(10L, 4)
            )
        )
    }

    @Test
    fun isSnapshotVersionCompatible_positiveVersionRequiresExistingFamilyAndUpdatedAt() {
        assertEquals(
            false,
            isSnapshotVersionCompatible(
                snapshotVersion = 10L,
                snapshotVersionNanos = 5,
                familyExists = true,
                updatedAt = null
            )
        )
        assertEquals(
            false,
            isSnapshotVersionCompatible(
                snapshotVersion = 10L,
                snapshotVersionNanos = 5,
                familyExists = false,
                updatedAt = null
            )
        )
    }

    @Test
    fun buildUploadPlan_unchangedBaseAndTargetWithManyTasksProducesEmptyPlan() {
        val tasks = (1..600).map { index ->
            ExportTask(id = "task-$index", title = "Task $index")
        }
        val snapshot = ExportData(tasks = tasks)

        val plan = buildUploadPlan(
            baseSnapshot = snapshot,
            targetSnapshot = snapshot,
            isMemberWriteAllowed = true,
            selfMember = null
        )

        assertTrue(plan.isEmpty())
        assertTrue(isUploadWithinTransactionLimit(countUploadWriteOperations(plan, includesFamilyUpdatedAt = true)))
    }

    @Test
    fun buildUploadPlan_oneChangedTaskProducesSingleSet() {
        val base = ExportData(tasks = listOf(ExportTask(id = "task-1", title = "Old")))
        val target = ExportData(tasks = listOf(ExportTask(id = "task-1", title = "New")))

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertEquals(1, plan.size)
        assertEquals(
            PlannedUploadOperation.Set("tasks", "task-1", target.tasks.single()),
            plan.single()
        )
    }

    @Test
    fun buildUploadPlan_oneNewTaskProducesSingleSet() {
        val base = ExportData(tasks = emptyList())
        val target = ExportData(tasks = listOf(ExportTask(id = "task-1", title = "New")))

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertEquals(1, plan.size)
        assertTrue(plan.single() is PlannedUploadOperation.Set)
    }

    @Test
    fun buildUploadPlan_achievementUsesRoomLogicalPrimaryKey() {
        val achievement = ExportAchievement(
            userId = "child",
            type = "FIRST_TASK",
            earnedAt = 123L,
        )

        val plan = buildUploadPlan(
            baseSnapshot = ExportData(),
            targetSnapshot = ExportData(achievements = listOf(achievement)),
            isMemberWriteAllowed = true,
            selfMember = null,
        )

        val set = plan.single() as PlannedUploadOperation.Set
        assertEquals("achievements", set.collection)
        assertEquals("child_FIRST_TASK", set.documentId)
    }

    @Test
    fun buildUploadPlan_deletedRecipeProducesSingleDelete() {
        val recipe = ExportRecipe(id = "recipe-1", title = "Soup")
        val base = ExportData(recipes = listOf(recipe))
        val target = ExportData(recipes = emptyList())

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertEquals(1, plan.size)
        assertEquals(PlannedUploadOperation.Delete("recipes", "recipe-1"), plan.single())
    }

    @Test
    fun buildUploadPlan_nullBaseSnapshotSetsAllTargetDocuments() {
        val target = ExportData(
            tasks = listOf(ExportTask(id = "task-1", title = "A")),
            recipes = listOf(ExportRecipe(id = "recipe-1", title = "Soup"))
        )

        val plan = buildUploadPlan(
            baseSnapshot = null,
            targetSnapshot = target,
            isMemberWriteAllowed = true,
            selfMember = null
        )

        assertEquals(2, plan.size)
        assertTrue(plan.all { it is PlannedUploadOperation.Set })
        assertTrue(plan.none { it is PlannedUploadOperation.Delete })
    }

    @Test
    fun buildUploadPlan_allows499ChangedDocumentsPlusUpdatedAt() {
        val changedTasks = (1..499).map { index ->
            ExportTask(id = "task-$index", title = "Changed $index")
        }
        val base = ExportData(tasks = changedTasks.map { it.copy(title = "Old") })
        val target = ExportData(tasks = changedTasks)

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertEquals(499, plan.size)
        assertTrue(isUploadWithinTransactionLimit(countUploadWriteOperations(plan, includesFamilyUpdatedAt = true)))
    }

    @Test
    fun buildUploadPlan_rejects500ChangedDocumentsPlusUpdatedAt() {
        val changedTasks = (1..500).map { index ->
            ExportTask(id = "task-$index", title = "Changed $index")
        }
        val base = ExportData(tasks = changedTasks.map { it.copy(title = "Old") })
        val target = ExportData(tasks = changedTasks)

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertEquals(500, plan.size)
        assertFalse(isUploadWithinTransactionLimit(countUploadWriteOperations(plan, includesFamilyUpdatedAt = true)))
    }

    @Test
    fun buildUploadPlan_adminIncludesMemberDelta() {
        val baseMember = member("parent-1", "Old name")
        val targetMember = member("parent-1", "New name")
        val base = ExportData(members = listOf(baseMember))
        val target = ExportData(members = listOf(targetMember))

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertEquals(1, plan.size)
        assertEquals(
            PlannedUploadOperation.Set("members", "parent-1", targetMember),
            plan.single()
        )
    }

    @Test
    fun buildUploadPlan_nonAdminDoesNotIncludeBulkMemberDelta() {
        val baseMember = member("parent-1", "Old name")
        val targetMember = member("parent-1", "New name")
        val base = ExportData(members = listOf(baseMember))
        val target = ExportData(members = listOf(targetMember))

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = false, selfMember = null)

        assertTrue(plan.isEmpty())
    }

    @Test
    fun buildUploadPlan_changedSelfMemberProducesSingleMergeSet() {
        val selfMember = member("parent-1", "New name")
        val base = ExportData(members = listOf(member("parent-1", "Old name")))

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = base,
            isMemberWriteAllowed = false,
            selfMember = selfMember
        )

        assertEquals(1, plan.size)
        assertEquals(
            PlannedUploadOperation.Set("members", "parent-1", selfMember, merge = true),
            plan.single()
        )
    }

    @Test
    fun buildUploadPlan_unchangedSelfMemberProducesNoWrite() {
        val selfMember = member("parent-1", "Same name")
        val base = ExportData(members = listOf(selfMember))

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = base,
            isMemberWriteAllowed = false,
            selfMember = selfMember
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun buildUploadPlan_deletedTaskProducesSingleDelete() {
        val task = ExportTask(id = "task-deleted", title = "Gone")
        val base = ExportData(tasks = listOf(task))
        val target = ExportData(tasks = emptyList())

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertEquals(1, plan.size)
        assertEquals(
            PlannedUploadOperation.Delete(DeletedCloudDocumentCollections.TASKS, "task-deleted"),
            plan.single()
        )
    }

    @Test
    fun buildUploadPlan_deletedShoppingListCascadeProducesDeletes() {
        val base = ExportData(
            shoppingLists = listOf(
                ExportShoppingList(
                    id = "list-1",
                    title = "List",
                    colorHex = "#fff",
                    iconName = "cart",
                    createdAt = 1L
                )
            ),
            shoppingCategories = listOf(
                ExportShoppingCategory(
                    id = "cat-1",
                    listId = "list-1",
                    title = "Cat",
                    colorHex = "#fff",
                    iconName = "icon",
                    sortOrder = 0
                )
            ),
            shoppingItems = listOf(
                ExportShoppingItem(
                    id = "item-1",
                    listId = "list-1",
                    title = "Milk",
                    createdAt = 1L
                )
            )
        )
        val target = ExportData(
            shoppingLists = emptyList(),
            shoppingCategories = emptyList(),
            shoppingItems = emptyList()
        )

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)
        val deletes = plan.filterIsInstance<PlannedUploadOperation.Delete>()

        assertEquals(3, deletes.size)
        assertEquals(
            setOf(
                PlannedUploadOperation.Delete(DeletedCloudDocumentCollections.SHOPPING_LISTS, "list-1"),
                PlannedUploadOperation.Delete(DeletedCloudDocumentCollections.SHOPPING_CATEGORIES, "cat-1"),
                PlannedUploadOperation.Delete(DeletedCloudDocumentCollections.SHOPPING_ITEMS, "item-1")
            ),
            deletes.toSet()
        )
    }

    @Test
    fun buildUploadPlan_deletedEventProducesSingleDelete() {
        val base = ExportData(
            events = listOf(ExportEvent(id = "event-1", title = "Party", startDateTime = 1L))
        )
        val target = ExportData(events = emptyList())

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertEquals(1, plan.size)
        assertEquals(
            PlannedUploadOperation.Delete(DeletedCloudDocumentCollections.EVENTS, "event-1"),
            plan.single()
        )
    }

    @Test
    fun buildUploadPlan_deletedRewardProducesSingleDelete() {
        val base = ExportData(
            rewards = listOf(ExportReward(id = "reward-1", title = "Ice cream", cost = 10))
        )
        val target = ExportData(rewards = emptyList())

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertEquals(1, plan.size)
        assertEquals(
            PlannedUploadOperation.Delete(DeletedCloudDocumentCollections.REWARDS, "reward-1"),
            plan.single()
        )
    }

    @Test
    fun buildUploadPlan_deletedFamilyGoalCascadeProducesDeletes() {
        val base = ExportData(
            familyGoals = listOf(ExportFamilyGoal(id = "goal-1", title = "Trip")),
            familyGoalStages = listOf(
                ExportFamilyGoalStage(id = "stage-1", goalId = "goal-1", title = "Stage")
            ),
            familyGoalSteps = listOf(
                ExportFamilyGoalStep(id = "step-1", goalId = "goal-1", stageId = "stage-1", title = "Step")
            )
        )
        val target = ExportData(
            familyGoals = emptyList(),
            familyGoalStages = emptyList(),
            familyGoalSteps = emptyList()
        )

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)
        val deletes = plan.filterIsInstance<PlannedUploadOperation.Delete>()

        assertEquals(3, deletes.size)
        assertEquals(
            setOf(
                PlannedUploadOperation.Delete(DeletedCloudDocumentCollections.FAMILY_GOALS, "goal-1"),
                PlannedUploadOperation.Delete(DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES, "stage-1"),
                PlannedUploadOperation.Delete(DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS, "step-1")
            ),
            deletes.toSet()
        )
    }

    @Test
    fun buildUploadPlan_backupOnlyMenuCollectionsProduceNoFirestoreOperations() {
        val base = ExportData(
            menuTemplates = listOf(ExportMenuTemplate(id = "tpl-1", name = "Old", createdAt = 1L)),
            menuTemplateEntries = listOf(
                ExportMenuTemplateEntry(id = "te-1", templateId = "tpl-1", dayOfWeek = 1, mealType = "LUNCH", servings = 2)
            ),
            weeklyMenus = listOf(ExportWeeklyMenu(id = "menu-1", startDateMillis = 1L, createdAt = 1L)),
            menuEntries = listOf(ExportMenuEntry(id = "me-1", menuId = "menu-1", dayOfWeek = 1, mealType = "BREAKFAST")),
            taskHistory = listOf(
                ExportTaskHistory(
                    id = "hist-1",
                    taskId = "task-1",
                    changedByUserId = "user-1",
                    changeType = "created",
                    changeDescription = "Created",
                    timestamp = 1L
                )
            ),
            dailyAssignments = listOf(ExportDailyAssignment(id = "da-1", weekStartMillis = 1L, dayOfWeek = 1))
        )
        val target = ExportData(
            menuTemplates = listOf(ExportMenuTemplate(id = "tpl-2", name = "New", createdAt = 2L)),
            menuTemplateEntries = listOf(
                ExportMenuTemplateEntry(id = "te-2", templateId = "tpl-2", dayOfWeek = 2, mealType = "DINNER", servings = 3)
            ),
            weeklyMenus = listOf(ExportWeeklyMenu(id = "menu-2", startDateMillis = 2L, createdAt = 2L)),
            menuEntries = listOf(ExportMenuEntry(id = "me-2", menuId = "menu-2", dayOfWeek = 2, mealType = "DINNER")),
            taskHistory = listOf(
                ExportTaskHistory(
                    id = "hist-2",
                    taskId = "task-2",
                    changedByUserId = "user-1",
                    changeType = "updated",
                    changeDescription = "Updated",
                    timestamp = 2L
                )
            ),
            dailyAssignments = listOf(ExportDailyAssignment(id = "da-2", weekStartMillis = 2L, dayOfWeek = 2))
        )

        val plan = buildUploadPlan(base, target, isMemberWriteAllowed = true, selfMember = null)

        assertTrue(plan.isEmpty())
    }

    @Test
    fun buildUploadPlan_rejectsNonAdminSelfMemberRoleMismatch() {
        val baseMember = member("parent-1", "Parent").copy(role = MemberRole.PARENT.name)
        val staleSelf = baseMember.copy(role = MemberRole.CHILD.name, displayName = "Edited profile")
        val base = ExportData(members = listOf(baseMember))

        assertThrows(SelfMemberRoleMismatchException::class.java) {
            buildUploadPlan(
                baseSnapshot = base,
                targetSnapshot = base,
                isMemberWriteAllowed = false,
                selfMember = staleSelf,
            )
        }
    }

    @Test
    fun buildUploadPlan_rejectsMissingBaseSelfMember() {
        val selfMember = member("parent-1", "Parent").copy(role = MemberRole.PARENT.name)
        val base = ExportData(members = emptyList())

        assertThrows(SelfMemberMissingInBaseException::class.java) {
            buildUploadPlan(
                baseSnapshot = base,
                targetSnapshot = base,
                isMemberWriteAllowed = false,
                selfMember = selfMember,
            )
        }
    }

    @Test
    fun buildUploadPlan_allowsProfileEditWhenRoleMatchesBase() {
        val baseMember = member("parent-1", "Old name").copy(role = MemberRole.PARENT.name)
        val editedSelf = baseMember.copy(displayName = "New name")
        val base = ExportData(members = listOf(baseMember))

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = base,
            isMemberWriteAllowed = false,
            selfMember = editedSelf,
        )

        assertEquals(1, plan.size)
        val setOp = plan.single() as PlannedUploadOperation.Set
        assertEquals(MemberRole.PARENT.name, (setOp.data as ExportMember).role)
        assertEquals("New name", (setOp.data as ExportMember).displayName)
    }

    @Test
    fun buildUploadPlan_childOmitsAdminOnlyCollectionsButKeepsAllowedWrites() {
        val selfMember = member("child-1", "Child").copy(role = MemberRole.CHILD.name)
        val base = ExportData(
            members = listOf(selfMember),
            tasks = listOf(
                ExportTask(
                    id = "parent-task",
                    title = "Parent task",
                    createdByUserId = "parent-1",
                    assignedToUserId = "parent-1",
                )
            ),
            shoppingLists = listOf(ExportShoppingList(id = "list-1", title = "Old")),
            scores = listOf(ExportScore(userId = "child-1", weekStartDate = 1L, score = 0)),
            wallets = listOf(ExportWallet(userId = "child-1", balance = 0)),
            taskCompletions = listOf(ExportTaskCompletion(id = "tc-1", taskId = "task-1", userId = "child-1")),
        )
        val target = base.copy(
            tasks = listOf(
                ExportTask(
                    id = "parent-task",
                    title = "Changed by merge noise",
                    createdByUserId = "parent-1",
                    assignedToUserId = "parent-1",
                )
            ),
            shoppingLists = listOf(ExportShoppingList(id = "list-1", title = "New")),
            scores = listOf(ExportScore(userId = "child-1", weekStartDate = 1L, score = 10)),
            wallets = listOf(ExportWallet(userId = "child-1", balance = 5)),
            taskCompletions = listOf(
                ExportTaskCompletion(id = "tc-1", taskId = "task-1", userId = "child-1", totalPoints = 5)
            ),
        )
        val editedSelf = selfMember.copy(displayName = "Edited Child")

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = target,
            isMemberWriteAllowed = false,
            selfMember = editedSelf,
            writerRole = MemberRole.CHILD,
        )

        val collections = plan.map { operation ->
            when (operation) {
                is PlannedUploadOperation.Delete -> operation.collection
                is PlannedUploadOperation.Set -> operation.collection
            }
        }.toSet()
        assertFalse(collections.contains("scores"))
        assertFalse(collections.contains("wallets"))
        assertFalse(collections.contains("task_completions"))
        assertFalse(collections.contains("tasks"))
        assertTrue(collections.contains("shopping_lists"))
        assertTrue(collections.contains("members"))
        assertEquals(2, plan.size)
    }

    @Test
    fun buildUploadPlan_childAssigneeTodoToWaitingProducesMergeSetWithStatusFieldsOnly() {
        val selfMember = member("child-1", "Child").copy(role = MemberRole.CHILD.name)
        val baseTask = ExportTask(
            id = "task-1",
            title = "Clean room",
            createdByUserId = "parent-1",
            assignedToUserId = "child-1",
            status = "TODO",
            requiresApproval = false,
            attachments = listOf(
                com.familyhub.data.export.ExportTaskAttachment(id = "att-1", name = "photo.jpg", uri = "file://a")
            ),
        )
        val targetTask = baseTask.copy(
            status = "WAITING_FOR_APPROVAL",
            requiresApproval = true,
            attachments = emptyList(),
            comments = listOf(
                com.familyhub.data.export.ExportTaskComment(
                    id = "c-1",
                    authorId = "child-1",
                    message = "Done",
                    createdAt = 1L,
                )
            ),
        )
        val unrelatedTask = ExportTask(
            id = "task-2",
            title = "Old title",
            createdByUserId = "parent-1",
            assignedToUserId = "parent-1",
        )
        val base = ExportData(
            members = listOf(selfMember),
            tasks = listOf(baseTask, unrelatedTask),
        )
        val target = base.copy(
            tasks = listOf(
                targetTask,
                unrelatedTask.copy(title = "New title"),
            ),
        )

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = target,
            isMemberWriteAllowed = false,
            selfMember = selfMember,
            writerRole = MemberRole.CHILD,
        )

        val taskOps = plan.filter { op ->
            when (op) {
                is PlannedUploadOperation.Set -> op.collection == "tasks"
                else -> false
            }
        }
        assertEquals(1, taskOps.size)
        val setOp = taskOps.single() as PlannedUploadOperation.Set
        assertEquals("task-1", setOp.documentId)
        assertTrue(setOp.merge)
        assertEquals(
            mapOf("status" to "WAITING_FOR_APPROVAL", "requiresApproval" to true),
            setOp.data,
        )
    }

    @Test
    fun buildUploadPlan_childCannotUploadUpdateToSomeoneElsesTask() {
        val selfMember = member("child-1", "Child").copy(role = MemberRole.CHILD.name)
        val base = ExportData(
            members = listOf(selfMember),
            tasks = listOf(
                ExportTask(
                    id = "task-1",
                    title = "Old",
                    createdByUserId = "parent-1",
                    assignedToUserId = "parent-1",
                )
            ),
        )
        val target = base.copy(
            tasks = listOf(
                ExportTask(
                    id = "task-1",
                    title = "New",
                    createdByUserId = "parent-1",
                    assignedToUserId = "parent-1",
                )
            ),
        )

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = target,
            isMemberWriteAllowed = false,
            selfMember = selfMember,
            writerRole = MemberRole.CHILD,
        )

        assertTrue(plan.none { it is PlannedUploadOperation.Set && it.collection == "tasks" })
    }

    @Test
    fun buildUploadPlan_childCreateOwnTodoTaskProducesFullSet() {
        val selfMember = member("child-1", "Child").copy(role = MemberRole.CHILD.name)
        val newTask = ExportTask(
            id = "task-new",
            title = "My task",
            createdByUserId = "child-1",
            assignedToUserId = "child-1",
            status = "TODO",
        )
        val base = ExportData(members = listOf(selfMember))
        val target = ExportData(members = listOf(selfMember), tasks = listOf(newTask))

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = target,
            isMemberWriteAllowed = false,
            selfMember = selfMember,
            writerRole = MemberRole.CHILD,
        )

        assertEquals(1, plan.size)
        val setOp = plan.single() as PlannedUploadOperation.Set
        assertEquals("tasks", setOp.collection)
        assertEquals("task-new", setOp.documentId)
        assertFalse(setOp.merge)
        assertEquals(newTask, setOp.data)
    }

    @Test
    fun buildUploadPlan_childDeleteOwnTaskProducesDeleteAndSkipsOthers() {
        val selfMember = member("child-1", "Child").copy(role = MemberRole.CHILD.name)
        val ownTask = ExportTask(
            id = "task-own",
            title = "Mine",
            createdByUserId = "child-1",
            assignedToUserId = "child-1",
            status = "TODO",
        )
        val parentTask = ExportTask(
            id = "task-parent",
            title = "Parent task",
            createdByUserId = "parent-1",
            assignedToUserId = "parent-1",
        )
        val base = ExportData(members = listOf(selfMember), tasks = listOf(ownTask, parentTask))
        val target = ExportData(members = listOf(selfMember), tasks = emptyList())

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = target,
            isMemberWriteAllowed = false,
            selfMember = selfMember,
            writerRole = MemberRole.CHILD,
        )

        val taskDeletes = plan.filterIsInstance<PlannedUploadOperation.Delete>()
            .filter { it.collection == "tasks" }
        assertEquals(1, taskDeletes.size)
        assertEquals("task-own", taskDeletes.single().documentId)
    }

    @Test
    fun summarizePlannedUploadOperationsByCollection_groupsOperations() {
        val operations = listOf(
            PlannedUploadOperation.Set("tasks", "task-1", ExportTask(id = "task-1")),
            PlannedUploadOperation.Set("tasks", "task-2", ExportTask(id = "task-2")),
            PlannedUploadOperation.Delete("shopping_lists", "list-1"),
        )

        assertEquals(
            mapOf("tasks" to 2, "shopping_lists" to 1),
            summarizePlannedUploadOperationsByCollection(operations),
        )
    }

    @Test
    fun buildUploadPlan_childRewardRequestCreateOnly() {
        val existing = ExportRewardRequest(
            id = "req-1",
            rewardId = "reward-1",
            rewardTitle = "Ice cream",
            rewardCost = 5,
            requesterUserId = "child-1",
            status = "PENDING",
            requestedAt = 1L,
        )
        val updatedExisting = existing.copy(status = "APPROVED")
        val created = ExportRewardRequest(
            id = "req-2",
            rewardId = "reward-2",
            rewardTitle = "Movie",
            rewardCost = 10,
            requesterUserId = "child-1",
            status = "PENDING",
            requestedAt = 2L,
        )
        val base = ExportData(rewardRequests = listOf(existing))
        val target = ExportData(rewardRequests = listOf(updatedExisting, created))

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = target,
            isMemberWriteAllowed = false,
            selfMember = null,
            writerRole = MemberRole.CHILD,
        )

        assertEquals(1, plan.size)
        val setOp = plan.single() as PlannedUploadOperation.Set
        assertEquals("reward_requests", setOp.collection)
        assertEquals("req-2", setOp.documentId)
    }

    @Test
    fun buildUploadPlan_parentIncludesScoresWhenChanged() {
        val base = ExportData(scores = listOf(ExportScore(userId = "child-1", weekStartDate = 1L, score = 0)))
        val target = ExportData(scores = listOf(ExportScore(userId = "child-1", weekStartDate = 1L, score = 10)))

        val plan = buildUploadPlan(
            baseSnapshot = base,
            targetSnapshot = target,
            isMemberWriteAllowed = false,
            selfMember = null,
            writerRole = MemberRole.PARENT,
        )

        assertEquals(1, plan.size)
        assertEquals("scores", (plan.single() as PlannedUploadOperation.Set).collection)
    }

    @Test
    fun mergeUploadPlans_rejectsDuplicateCollectionDocumentPairs() {
        val duplicate = listOf(
            PlannedUploadOperation.Delete("tasks", "task-1"),
            PlannedUploadOperation.Set("tasks", "task-1", ExportTask(id = "task-1", title = "x")),
        )
        assertThrows(IllegalStateException::class.java) {
            mergeUploadPlans(duplicate, emptyList())
        }
    }

    @Test
    fun mergeUploadPlans_combinesTaskAndMenuOperationsForBoundaryCount() {
        val taskPlan = buildUploadPlan(
            baseSnapshot = ExportData(tasks = listOf(ExportTask(id = "task-1", title = "Old"))),
            targetSnapshot = ExportData(tasks = listOf(ExportTask(id = "task-1", title = "New"))),
            isMemberWriteAllowed = true,
            selfMember = null,
        )
        val menuOps = menuWriteOperationsToPlanned(
            listOf(
                com.familyhub.data.menu.cloud.MenuCloudWriteOperation.Set(
                    DeletedCloudDocumentCollections.WEEKLY_MENUS,
                    "week-1",
                    mapOf("id" to "week-1"),
                ),
            ),
        )
        val combined = mergeUploadPlans(taskPlan, menuOps)
        assertEquals(2, combined.size)
    }

    private fun member(id: String, displayName: String): ExportMember {
        return ExportMember(
            id = id,
            displayName = displayName,
            roleLabel = "PARENT",
            avatarColor = "#2196F3",
            role = "PARENT"
        )
    }
}
