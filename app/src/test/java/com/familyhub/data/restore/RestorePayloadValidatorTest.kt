package com.familyhub.data.restore

import com.familyhub.data.backup.EncryptedBackupFormat
import com.familyhub.data.backup.EncryptedBackupPayload
import com.familyhub.data.export.ExportAchievement
import com.familyhub.data.export.ExportAppreciation
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportRecipe
import com.familyhub.data.export.ExportRecipeIngredient
import com.familyhub.data.export.ExportRecipeStep
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportShoppingActivity
import com.familyhub.data.export.ExportShoppingItem
import com.familyhub.data.export.ExportShoppingList
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportWeeklyMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePayloadValidatorTest {

    @Test
    fun validMinimalPayload_returnsSnapshot() {
        val snapshot = RestorePayloadValidator.validate(validPayload())
        assertNull(snapshot.sourceFamilyId)
        assertEquals(1, snapshot.counts.members)
        assertEquals(1, snapshot.counts.tasks)
    }

    @Test
    fun sourceFamilyId_nullAllowed() {
        RestorePayloadValidator.validateSourceFamilyId(null)
    }

    @Test
    fun sourceFamilyId_rejectsSlash() {
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validateSourceFamilyId("family/a")
        }
    }

    @Test
    fun sourceFamilyId_rejectsDotAndDotDot() {
        assertFalse(RestorePayloadValidator.isValidFirestoreDocumentId("."))
        assertFalse(RestorePayloadValidator.isValidFirestoreDocumentId(".."))
    }

    @Test
    fun sourceFamilyId_rejectsReservedDoubleUnderscorePattern() {
        assertFalse(RestorePayloadValidator.isValidFirestoreDocumentId("__name__"))
    }

    @Test
    fun sourceFamilyId_rejectsUnpairedSurrogate() {
        assertFalse(RestorePayloadValidator.isValidFirestoreDocumentId("\uD800"))
    }

    @Test
    fun sourceFamilyId_rejectsOver1500Utf8Bytes() {
        // Cyrillic chars are 2 UTF-8 bytes each: 751 * 2 = 1502 > 1500
        val tooLong = "я".repeat(751)
        assertTrue(tooLong.length < 1500)
        assertFalse(RestorePayloadValidator.isValidFirestoreDocumentId(tooLong))
    }

    @Test
    fun sourceFamilyId_accepts1500Utf8Bytes() {
        val ok = "a".repeat(1500)
        assertTrue(RestorePayloadValidator.isValidFirestoreDocumentId(ok))
    }

    @Test
    fun invalidMemberRole_rejectedWithoutFallback() {
        val payload = validPayload(
            data = validData().copy(
                members = listOf(
                    ExportMember(
                        id = "m1",
                        displayName = "A",
                        roleLabel = "x",
                        avatarColor = "#000",
                        role = "NOT_A_ROLE"
                    )
                )
            )
        )
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(payload)
        }
    }

    @Test
    fun duplicateTaskIds_rejected() {
        val task = validTask("t1")
        val payload = validPayload(data = validData().copy(tasks = listOf(task, task.copy(title = "other"))))
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(payload)
        }
    }

    @Test
    fun danglingStageGoal_rejected() {
        val payload = validPayload(
            data = validData().copy(
                familyGoals = listOf(
                    ExportFamilyGoal(
                        id = "g1",
                        title = "Goal",
                        status = "ACTIVE",
                        createdByUserId = "m1",
                        createdAt = 1L,
                        updatedAt = 1L
                    )
                ),
                familyGoalStages = listOf(
                    ExportFamilyGoalStage(id = "s1", goalId = "missing", title = "Stage")
                )
            )
        )
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(payload)
        }
    }

    @Test
    fun danglingShoppingItemList_rejected() {
        val payload = validPayload(
            data = validData().copy(
                shoppingLists = listOf(
                    ExportShoppingList(id = "l1", title = "List", createdAt = 1L)
                ),
                shoppingItems = listOf(
                    ExportShoppingItem(
                        id = "i1",
                        listId = "missing",
                        title = "Milk",
                        createdByUserId = "m1",
                        createdAt = 1L
                    )
                )
            )
        )
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(payload)
        }
    }

    @Test
    fun danglingStepStage_rejected() {
        val payload = validPayload(
            data = validData().copy(
                familyGoals = listOf(
                    ExportFamilyGoal(
                        id = "g1",
                        title = "Goal",
                        status = "ACTIVE",
                        createdByUserId = "m1",
                        createdAt = 1L,
                        updatedAt = 1L
                    )
                ),
                familyGoalStages = listOf(
                    ExportFamilyGoalStage(id = "st1", goalId = "g1", title = "Stage")
                ),
                familyGoalSteps = listOf(
                    ExportFamilyGoalStep(
                        id = "step1",
                        goalId = "g1",
                        stageId = "missing-stage",
                        title = "Step"
                    )
                )
            )
        )
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(payload)
        }
    }

    @Test
    fun historicalOrphanCompletion_allowed() {
        RestorePayloadValidator.validate(
            validPayload(
                data = validData().copy(
                    taskCompletions = listOf(
                        ExportTaskCompletion(
                            id = "c1",
                            taskId = "deleted-task",
                            userId = "m1",
                            taskType = "ROUTINE",
                            completionTime = 1L,
                            weekStartDate = 1L,
                            totalPoints = 1
                        )
                    )
                )
            )
        )
    }

    @Test
    fun historicalOrphanRewardRequest_allowed() {
        RestorePayloadValidator.validate(
            validPayload(
                data = validData().copy(
                    rewardRequests = listOf(
                        ExportRewardRequest(
                            id = "rr1",
                            rewardId = "deleted-reward",
                            rewardTitle = "Candy",
                            rewardCost = 5,
                            requesterUserId = "m1",
                            status = "PENDING",
                            requestedAt = 1L
                        )
                    )
                )
            )
        )
    }

    @Test
    fun historicalOrphanShoppingActivityItem_allowed() {
        RestorePayloadValidator.validate(
            validPayload(
                data = validData().copy(
                    shoppingLists = listOf(
                        ExportShoppingList(id = "l1", title = "List", createdAt = 1L)
                    ),
                    shoppingActivities = listOf(
                        ExportShoppingActivity(
                            id = "a1",
                            listId = "l1",
                            itemId = "deleted-item",
                            actorId = "m1",
                            actionType = "PURCHASED",
                            occurredAt = 1L
                        )
                    )
                )
            )
        )
    }

    @Test
    fun historicalOrphanAppreciationMembers_allowed() {
        RestorePayloadValidator.validate(
            validPayload(
                data = validData().copy(
                    appreciations = listOf(
                        ExportAppreciation(
                            id = "ap1",
                            senderId = "deleted-sender",
                            recipientId = "deleted-recipient",
                            message = "Thanks",
                            createdAt = 1L
                        )
                    )
                )
            )
        )
    }

    @Test
    fun globalDuplicateRecipeIngredientIds_rejected() {
        val recipeA = minimalRecipe("r1", ingredientId = "shared-ing")
        val recipeB = minimalRecipe("r2", ingredientId = "shared-ing")
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(data = validData().copy(recipes = listOf(recipeA, recipeB)))
            )
        }
    }

    @Test
    fun duplicateRecipeStepOrder_rejected() {
        val recipe = minimalRecipe("r1").copy(
            steps = listOf(
                ExportRecipeStep(id = "s1", description = "One", order = 1),
                ExportRecipeStep(id = "s2", description = "Two", order = 1)
            )
        )
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(data = validData().copy(recipes = listOf(recipe)))
            )
        }
    }

    @Test
    fun duplicateAchievementUserType_rejected() {
        val achievement = ExportAchievement(userId = "m1", type = "FIRST_TASK", earnedAt = 1L)
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(
                        achievements = listOf(achievement, achievement.copy(earnedAt = 2L))
                    )
                )
            )
        }
    }

    @Test
    fun duplicateMenuEntrySlot_resolvedByCanonicalizer() {
        val menu = ExportWeeklyMenu(id = "menu1", startDateMillis = 1L, createdAt = 1L)
        val entry = ExportMenuEntry(
            id = "e1",
            menuId = "menu1",
            dayOfWeek = 1,
            mealType = "BREAKFAST",
            servings = 1,
            updatedAt = 1L
        )
        val snapshot = RestorePayloadValidator.validate(
            validPayload(
                data = validData().copy(
                    weeklyMenus = listOf(menu),
                    menuEntries = listOf(
                        entry,
                        entry.copy(id = "e2", servings = 4, updatedAt = 9L)
                    )
                )
            )
        )
        assertEquals(1, snapshot.data.menuEntries.size)
        assertEquals(4, snapshot.data.menuEntries.single().servings)
    }

    @Test
    fun duplicateRawMenuIds_rejectedForAllFiveCollections() {
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(
                        menuTemplates = listOf(
                            ExportMenuTemplate(id = "t", name = "A", createdAt = 1L),
                            ExportMenuTemplate(id = "t", name = "B", createdAt = 2L)
                        )
                    )
                )
            )
        }
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(
                        menuTemplates = listOf(
                            ExportMenuTemplate(id = "tpl", name = "A", createdAt = 1L)
                        ),
                        menuTemplateEntries = listOf(
                            ExportMenuTemplateEntry(
                                id = "te",
                                templateId = "tpl",
                                dayOfWeek = 0,
                                mealType = "BREAKFAST",
                                servings = 1
                            ),
                            ExportMenuTemplateEntry(
                                id = "te",
                                templateId = "tpl",
                                dayOfWeek = 1,
                                mealType = "LUNCH",
                                servings = 1
                            )
                        )
                    )
                )
            )
        }
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(
                        weeklyMenus = listOf(
                            ExportWeeklyMenu(id = "wm", startDateMillis = 1L, createdAt = 1L),
                            ExportWeeklyMenu(id = "wm", startDateMillis = 2L, createdAt = 2L)
                        )
                    )
                )
            )
        }
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(
                        weeklyMenus = listOf(
                            ExportWeeklyMenu(id = "wm", startDateMillis = 1L, createdAt = 1L)
                        ),
                        menuEntries = listOf(
                            ExportMenuEntry(
                                id = "me",
                                menuId = "wm",
                                dayOfWeek = 0,
                                mealType = "BREAKFAST",
                                servings = 1
                            ),
                            ExportMenuEntry(
                                id = "me",
                                menuId = "wm",
                                dayOfWeek = 1,
                                mealType = "LUNCH",
                                servings = 1
                            )
                        )
                    )
                )
            )
        }
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(
                        dailyAssignments = listOf(
                            ExportDailyAssignment(
                                id = "da",
                                weekStartMillis = 1L,
                                dayOfWeek = 0,
                                assignedMemberId = "a"
                            ),
                            ExportDailyAssignment(
                                id = "da",
                                weekStartMillis = 1L,
                                dayOfWeek = 1,
                                assignedMemberId = "b"
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun updatedAtBelowZero_menuCollectionsRejected() {
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(
                        weeklyMenus = listOf(
                            ExportWeeklyMenu(
                                id = "wm",
                                startDateMillis = 1L,
                                createdAt = 1L,
                                updatedAt = -1L
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun duplicateDailyAssignmentSlot_resolvedByCanonicalizer() {
        val assignment = ExportDailyAssignment(
            id = "d1",
            weekStartMillis = 100L,
            dayOfWeek = 2,
            assignedMemberId = "a",
            updatedAt = 1L
        )
        val snapshot = RestorePayloadValidator.validate(
            validPayload(
                data = validData().copy(
                    dailyAssignments = listOf(
                        assignment,
                        assignment.copy(id = "d2", assignedMemberId = "b", updatedAt = 5L)
                    )
                )
            )
        )
        assertEquals(1, snapshot.data.dailyAssignments.size)
        assertEquals("b", snapshot.data.dailyAssignments.single().assignedMemberId)
    }

    @Test
    fun duplicateMenuTemplateEntrySlot_resolvedByCanonicalizer() {
        val template = ExportMenuTemplate(id = "tpl1", name = "Week", createdAt = 1L)
        val entry = ExportMenuTemplateEntry(
            id = "e1",
            templateId = "tpl1",
            dayOfWeek = 1,
            mealType = "BREAKFAST",
            servings = 2,
            updatedAt = 1L
        )
        val snapshot = RestorePayloadValidator.validate(
            validPayload(
                data = validData().copy(
                    menuTemplates = listOf(template),
                    menuTemplateEntries = listOf(
                        entry,
                        entry.copy(id = "e2", servings = 8, updatedAt = 7L)
                    )
                )
            )
        )
        assertEquals(1, snapshot.data.menuTemplateEntries.size)
        assertEquals(8, snapshot.data.menuTemplateEntries.single().servings)
    }

    @Test
    fun danglingMenuTemplateEntry_rejected() {
        val entry = ExportMenuTemplateEntry(
            id = "e1",
            templateId = "missing-template",
            dayOfWeek = 1,
            mealType = "LUNCH",
            servings = 2
        )
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(menuTemplateEntries = listOf(entry))
                )
            )
        }
    }

    @Test
    fun invalidMenuServings_rejected() {
        val menu = ExportWeeklyMenu(id = "menu1", startDateMillis = 1L, createdAt = 1L)
        val entry = ExportMenuEntry(
            id = "e1",
            menuId = "menu1",
            dayOfWeek = 1,
            mealType = "BREAKFAST",
            servings = 0
        )
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(
                        weeklyMenus = listOf(menu),
                        menuEntries = listOf(entry)
                    )
                )
            )
        }
    }

    @Test
    fun invalidMenuDayOfWeek_rejected() {
        val menu = ExportWeeklyMenu(id = "menu1", startDateMillis = 1L, createdAt = 1L)
        val entry = ExportMenuEntry(
            id = "e1",
            menuId = "menu1",
            dayOfWeek = 7,
            mealType = "BREAKFAST",
            servings = 2
        )
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(
                    data = validData().copy(
                        weeklyMenus = listOf(menu),
                        menuEntries = listOf(entry)
                    )
                )
            )
        }
    }

    @Test
    fun countsOf_includesWeeklyMenusAndMenuTemplates() {
        val data = validData().copy(
            weeklyMenus = listOf(ExportWeeklyMenu(id = "m1", startDateMillis = 1L, createdAt = 1L)),
            menuTemplates = listOf(ExportMenuTemplate(id = "t1", name = "Tpl", createdAt = 1L))
        )
        val counts = RestorePayloadValidator.countsOf(data)
        assertEquals(1, counts.weeklyMenus)
        assertEquals(1, counts.menuTemplates)
    }

    @Test
    fun invalidMenuTemplateFirestoreId_rejected() {
        val template = ExportMenuTemplate(id = "bad/id", name = "Week", createdAt = 1L)
        assertThrows(RestoreValidationException::class.java) {
            RestorePayloadValidator.validate(
                validPayload(data = validData().copy(menuTemplates = listOf(template)))
            )
        }
    }

    @Test
    fun firestoreId_acceptsAlphanumeric() {
        assertTrue(RestorePayloadValidator.isValidFirestoreDocumentId("family-abc_123"))
    }

    private fun minimalRecipe(
        id: String,
        ingredientId: String = "ing-$id"
    ): ExportRecipe =
        ExportRecipe(
            id = id,
            title = "Recipe $id",
            difficulty = "MEDIUM",
            category = "MAIN_DISH",
            createdByUserId = "m1",
            createdAt = 1L,
            updatedAt = 1L,
            ingredients = listOf(
                ExportRecipeIngredient(id = ingredientId, name = "Salt", quantity = "1")
            ),
            steps = listOf(
                ExportRecipeStep(id = "step-$id", description = "Mix", order = 0)
            )
        )

    private fun validPayload(
        sourceFamilyId: String? = null,
        data: ExportData = validData()
    ): EncryptedBackupPayload =
        EncryptedBackupPayload(
            payloadVersion = EncryptedBackupFormat.PAYLOAD_VERSION,
            createdAt = 1_700_000_000_000L,
            sourceFamilyId = sourceFamilyId,
            data = data
        )

    private fun validData(): ExportData =
        ExportData(
            members = listOf(
                ExportMember(
                    id = "m1",
                    displayName = "Admin",
                    roleLabel = "Admin",
                    avatarColor = "#2196F3",
                    role = "ADMIN"
                )
            ),
            tasks = listOf(validTask("t1")),
            shoppingLists = emptyList(),
            shoppingCategories = emptyList(),
            shoppingItems = emptyList(),
            exportDate = "2026-01-01 12:00:00"
        )

    private fun validTask(id: String): ExportTask =
        ExportTask(
            id = id,
            title = "Task",
            createdByUserId = "m1",
            assignedToUserId = "m1",
            repeatRule = "NONE",
            status = "TODO",
            createdAt = 1L,
            taskType = "ROUTINE",
            priority = "MEDIUM"
        )
}
