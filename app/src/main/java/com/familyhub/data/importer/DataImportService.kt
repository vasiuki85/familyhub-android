package com.familyhub.data.importer

import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import com.familyhub.data.export.ExportAchievement
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportEvent
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportHouseRule
import com.familyhub.data.export.ExportReward
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportShoppingCategory
import com.familyhub.data.export.ExportShoppingItem
import com.familyhub.data.export.ExportShoppingList
import com.familyhub.data.export.ExportShoppingActivity
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportTaskRotation
import com.familyhub.data.export.ExportWallet
import com.familyhub.data.export.ExportTaskTemplate
import com.familyhub.data.export.ExportTaskCategory
import com.familyhub.data.export.ExportAppreciation
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportEmergencyContact
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.export.ExportTaskAttachment

import com.familyhub.data.export.ExportTaskComment
import com.familyhub.data.export.ExportRecipe
import com.familyhub.data.export.ExportRecipeIngredient
import com.familyhub.data.export.ExportRecipeStep
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportTaskHistory
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.toDomain
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.FamilyEventDao
import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.db.dao.RewardDao
import com.familyhub.data.local.db.dao.RewardRequestDao
import com.familyhub.data.local.db.dao.ShoppingItemDao
import com.familyhub.data.local.db.dao.TaskCompletionDao
import com.familyhub.data.local.db.dao.TaskDao
import com.familyhub.data.local.db.dao.TaskRotationDao
import com.familyhub.data.local.db.dao.TaskTemplateDao
import com.familyhub.data.local.db.dao.TaskCategoryDao
import com.familyhub.data.local.db.dao.AppreciationDao
import com.familyhub.data.local.db.dao.HouseRuleDao
import com.familyhub.data.local.db.dao.FamilyGoalDao
import com.familyhub.data.local.db.dao.UserAchievementDao
import com.familyhub.data.local.db.dao.EmergencyContactDao
import com.familyhub.data.local.db.dao.UserScoreDao
import com.familyhub.data.local.db.dao.UserWalletDao
import com.familyhub.data.local.db.dao.PenaltyDao
import com.familyhub.data.local.db.dao.RecipeDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.TaskHistoryDao
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.menu.MenuSnapshotCanonicalizer
import com.familyhub.data.local.entity.FamilyEventEntity
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.RewardEntity
import com.familyhub.data.local.entity.RewardRequestEntity
import com.familyhub.data.local.entity.ShoppingCategoryEntity
import com.familyhub.data.local.entity.ShoppingItemEntity
import com.familyhub.data.local.entity.ShoppingListEntity
import com.familyhub.data.local.entity.ShoppingActivityEntity
import com.familyhub.data.local.entity.TaskCompletionEntity
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.data.local.entity.TaskAttachmentPayload
import com.familyhub.data.local.entity.TaskCommentPayload
import com.familyhub.data.local.entity.TaskRotationEntity
import com.familyhub.data.local.entity.TaskTemplateEntity
import com.familyhub.data.local.entity.TaskCategoryEntity
import com.familyhub.data.local.entity.AppreciationEntity
import com.familyhub.data.local.entity.HouseRuleEntity
import com.familyhub.data.local.entity.FamilyGoalEntity
import com.familyhub.data.local.entity.FamilyGoalStageEntity
import com.familyhub.data.local.entity.FamilyGoalStepEntity
import com.familyhub.data.local.entity.UserAchievementEntity
import com.familyhub.data.local.entity.EmergencyContactEntity
import com.familyhub.data.local.entity.UserScoreEntity
import com.familyhub.data.local.entity.UserWalletEntity
import com.familyhub.data.local.entity.PenaltyEntity
import com.familyhub.data.local.entity.RecipeEntities
import com.familyhub.data.local.entity.RecipeEntity
import com.familyhub.data.local.entity.RecipeIngredientEntity
import com.familyhub.data.local.entity.RecipeStepEntity
import com.familyhub.data.local.entity.WeeklyMenuEntity
import com.familyhub.data.local.entity.MenuEntryEntity
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.TaskHistoryEntity
import com.familyhub.data.local.entity.DailyAssignmentEntity
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.restore.ValidatedRestoreSnapshot
import com.familyhub.data.sync.SyncChangeTracker
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.PenaltySnapshotValidation
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskReminderMode
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import com.familyhub.data.repository.ShoppingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

@Singleton
class DataImportService @Inject constructor(
    private val database: FamilyHubDatabase,
    private val memberDao: FamilyMemberDao,
    private val taskDao: TaskDao,
    private val shoppingItemDao: ShoppingItemDao,
    private val eventDao: FamilyEventDao,
    private val scoreDao: UserScoreDao,
    private val taskCompletionDao: TaskCompletionDao,
    private val achievementDao: UserAchievementDao,
    private val rewardDao: RewardDao,
    private val rewardRequestDao: RewardRequestDao,
    private val walletDao: UserWalletDao,
    private val rotationDao: TaskRotationDao,
    private val houseRuleDao: HouseRuleDao,
    private val taskTemplateDao: TaskTemplateDao,
    private val taskCategoryDao: TaskCategoryDao,
    private val appreciationDao: AppreciationDao,
    private val familyGoalDao: FamilyGoalDao,
    private val emergencyContactDao: EmergencyContactDao,
    private val penaltyDao: PenaltyDao,
    private val recipeDao: RecipeDao,
    private val menuTemplateDao: MenuTemplateDao,
    private val weeklyMenuDao: WeeklyMenuDao,
    private val taskHistoryDao: TaskHistoryDao,
    private val dailyAssignmentDao: DailyAssignmentDao,
    private val json: Json,
    private val syncChangeTracker: SyncChangeTracker
) {
    suspend fun importFromFile(file: File) {
        val data = withContext(Dispatchers.IO) {
            json.decodeFromString(ExportData.serializer(), file.readText(StandardCharsets.UTF_8))
        }
        importData(data)
    }

    suspend fun importData(
        data: ExportData,
        markSynchronized: Boolean = false,
        expectedOutboxMaxSequence: Long? = null,
    ) {
        val validated = ImportCycleValidation.validateAndNormalize(data)
        PenaltySnapshotValidation.validateExportSnapshotPenalties(validated)
        replaceValidatedExport(
            data = validated,
            markSynchronized = markSynchronized,
            settleDelayMs = DEFAULT_SETTLE_DELAY_MS,
            expectedOutboxMaxSequence = expectedOutboxMaxSequence,
        )
    }

    /**
     * Production encrypted-restore path: clear+insert under tracking suppression,
     * hold [DEFAULT_SETTLE_DELAY_MS] so Room invalidations do not set hasPendingChanges,
     * then mark synchronized before releasing suppression.
     */
    suspend fun applyValidatedRestoreSnapshot(snapshot: ValidatedRestoreSnapshot) {
        replaceValidatedExport(
            data = snapshot.data,
            markSynchronized = true,
            settleDelayMs = DEFAULT_SETTLE_DELAY_MS
        )
    }

    /**
     * Shared clear+insert path for JSON import, sync merge import, and encrypted restore.
     * Zero settle delay is a test seam only.
     */
    @VisibleForTesting
    internal suspend fun replaceValidatedExport(
        data: ExportData,
        markSynchronized: Boolean = false,
        settleDelayMs: Long = DEFAULT_SETTLE_DELAY_MS,
        expectedOutboxMaxSequence: Long? = null,
    ) {
        val canonical = MenuSnapshotCanonicalizer.canonicalize(data)
        syncChangeTracker.runWithoutTracking {
            database.withTransaction {
                val outbox = database.syncOutboxDao()

                // Atomic guard: if a writer committed after the sync checkpoint,
                // abort before any clear/upsert so the post-checkpoint write survives.
                if (expectedOutboxMaxSequence != null) {
                    val currentMax = outbox.maxSequence() ?: 0L
                    if (currentMax > expectedOutboxMaxSequence) {
                        throw ImportRaceGuardException(
                            expectedOutboxMaxSequence = expectedOutboxMaxSequence,
                            actualOutboxMaxSequence = currentMax,
                        )
                    }
                }

                outbox.beginTrackingSuppression()
                try {
                    applyImportSnapshot(canonical)
                } finally {
                    outbox.endTrackingSuppression()
                }
            }
            if (settleDelayMs > 0L) {
                // Hold ignoreChanges until Room invalidation callbacks settle.
                delay(settleDelayMs)
            }
            if (markSynchronized) {
                syncChangeTracker.markSynchronized()
            }
        }
    }

    companion object {
        internal const val DEFAULT_SETTLE_DELAY_MS = 2000L
    }

    @VisibleForTesting
    internal suspend fun applyImportSnapshot(data: ExportData) {
        memberDao.clear()
        memberDao.upsertAll(data.members.map { it.toEntity() })

        taskCategoryDao.clear()
        taskCategoryDao.upsertAll(data.taskCategories.map { it.toEntity() })

        timber.log.Timber.d("DataImportService: Clearing tasks table and importing %d tasks", data.tasks.size)
        taskDao.clear()
        taskDao.upsertAll(data.tasks.map { it.toEntity() })
        timber.log.Timber.d("DataImportService: Tasks imported successfully. Count: %d", data.tasks.size)

        shoppingItemDao.clearAllItems()
        shoppingItemDao.clearCategories()
        shoppingItemDao.clearLists()
        shoppingItemDao.clearActivity()
        shoppingItemDao.upsertLists(data.shoppingLists.map { it.toEntity() })
        shoppingItemDao.upsertCategories(data.shoppingCategories.map { it.toEntity() })
        shoppingItemDao.upsertAll(data.shoppingItems.map { it.toEntity() })
        if (data.shoppingActivities.isNotEmpty()) {
            shoppingItemDao.insertActivities(data.shoppingActivities.map { it.toEntity(json) })
        }

        eventDao.clear()
        eventDao.upsertAll(data.events.map { it.toEntity() })

        scoreDao.clear()
        scoreDao.upsertAll(data.scores.map { it.toEntity() })

        taskCompletionDao.clear()
        taskCompletionDao.insertAll(data.taskCompletions.map { it.toEntity() })

        achievementDao.clear()
        achievementDao.insertAll(data.achievements.map { it.toEntity() })

        rewardDao.clear()
        rewardDao.upsertAll(data.rewards.map { it.toEntity() })

        rewardRequestDao.clear()
        rewardRequestDao.upsertAll(data.rewardRequests.map { it.toEntity() })

        penaltyDao.clear()
        penaltyDao.upsertAll(data.penalties.map { it.toDomain().toEntity() })

        walletDao.clear()
        walletDao.upsertAll(data.wallets.map { it.toEntity() })

        rotationDao.clear()
        rotationDao.upsertAll(data.taskRotations.map { it.toEntity() })

        houseRuleDao.clear()
        houseRuleDao.upsertAll(data.houseRules.map { it.toEntity() })

        taskTemplateDao.clear()
        taskTemplateDao.upsertAll(data.taskTemplates.map { it.toEntity() })

        appreciationDao.clear()
        appreciationDao.upsertAll(data.appreciations.map { it.toEntity() })

        val goalEntities = data.familyGoals.map { it.toEntity() }
        val stageEntities = data.familyGoalStages.map { it.toEntity() }
        val stepEntities = data.familyGoalSteps.map { it.toEntity() }
        familyGoalDao.upsertAll(goalEntities, stageEntities, stepEntities)

        emergencyContactDao.deleteAll()
        emergencyContactDao.upsertAll(data.emergencyContacts.map { it.toEntity() })

        recipeDao.clear()
        data.recipes.forEach { exportRecipe ->
            recipeDao.upsertRecipeWithDetails(exportRecipe.toEntity())
        }

        menuTemplateDao.clearEntries()
        menuTemplateDao.clearTemplates()
        data.menuTemplates.forEach { template ->
            menuTemplateDao.insertTemplate(template.toEntity())
        }
        if (data.menuTemplateEntries.isNotEmpty()) {
            menuTemplateDao.insertEntries(data.menuTemplateEntries.map { it.toEntity() })
        }

        weeklyMenuDao.clearEntries()
        weeklyMenuDao.clearMenus()
        data.weeklyMenus.forEach { menu ->
            weeklyMenuDao.upsertMenu(menu.toEntity())
        }
        if (data.menuEntries.isNotEmpty()) {
            weeklyMenuDao.upsertEntries(data.menuEntries.map { it.toEntity() })
        }

        taskHistoryDao.clearAll()
        if (data.taskHistory.isNotEmpty()) {
            taskHistoryDao.insertAll(data.taskHistory.map { it.toEntity() })
        }

        dailyAssignmentDao.clearAll()
        data.dailyAssignments.forEach { assignment ->
            dailyAssignmentDao.upsertAssignment(assignment.toEntity())
        }
    }

    private fun ExportMember.toEntity(): FamilyMemberEntity {
        val memberRole = try {
            MemberRole.valueOf(role)
        } catch (_: IllegalArgumentException) {
            MemberRole.CHILD
        }
        return FamilyMemberEntity(
            id = id,
            displayName = displayName,
            roleLabel = roleLabel,
            avatarColor = avatarColor,
            role = memberRole,
            birthDate = birthDate,
            age = age,
            photoUrl = photoUrl
        )
    }

    private fun ExportTask.toEntity(): TaskEntity {
        val repeat = runCatching { RepeatRule.valueOf(repeatRule) }.getOrDefault(RepeatRule.NONE)
        val status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.TODO)
        val type = runCatching { TaskType.valueOf(taskType) }.getOrDefault(TaskType.ROUTINE)
        val priorityValue = runCatching { TaskPriority.valueOf(priority) }.getOrDefault(TaskPriority.MEDIUM)
        val reminderModeValue = runCatching { TaskReminderMode.valueOf(reminderMode) }
            .getOrDefault(TaskReminderMode.NONE)
        return TaskEntity(
            id = id,
            title = title,
            description = description,
            createdByUserId = createdByUserId,
            assignedToUserId = assignedToUserId,
            dueDate = dueDate,
            dueDateHasTime = dueDateHasTime,
            reminderMode = reminderModeValue.name,
            reminderMinutesOfDay = reminderMinutesOfDay,
            repeatRule = repeat.name,
            status = status.name,
            createdAt = createdAt,
            orderIndex = orderIndex,
            isPrivate = this.`private`,
            taskType = type.name,
            priority = priorityValue.name,
            rewardPoints = rewardPoints,
            requiresApproval = requiresApproval,
            categoryId = categoryId,
            attachments = attachments.map { it.toPayload() },
            comments = comments.map { it.toPayload() },
            completionCycle = completionCycle
        )
    }

    private fun ExportTaskAttachment.toPayload(): TaskAttachmentPayload {
        return TaskAttachmentPayload(
            id = id,
            name = name,
            uri = uri,
            mimeType = mimeType,
            addedAt = addedAt
        )
    }

    private fun ExportTaskComment.toPayload(): TaskCommentPayload {
        return TaskCommentPayload(
            id = id,
            authorId = authorId,
            message = message,
            createdAt = createdAt
        )
    }

    private fun ExportShoppingList.toEntity(): ShoppingListEntity =
        ShoppingListEntity(
            id = id,
            title = title,
            colorHex = colorHex,
            iconName = iconName,
            createdAt = createdAt,
            isArchived = archived,
            archivedAt = archivedAt,
            templateKey = templateKey,
            participantIds = participantIds
        )

    private fun ExportShoppingCategory.toEntity(): ShoppingCategoryEntity =
        ShoppingCategoryEntity(
            id = id,
            listId = listId,
            title = title,
            colorHex = colorHex,
            iconName = iconName,
            sortOrder = sortOrder
        )

    private fun ExportShoppingActivity.toEntity(json: Json): ShoppingActivityEntity =
        ShoppingActivityEntity(
            id = id,
            listId = listId,
            itemId = itemId,
            actorId = actorId,
            actionType = actionType,
            payload = if (payload.isEmpty()) null else json.encodeToString(payload),
            occurredAt = occurredAt
        )

    private fun ExportShoppingItem.toEntity(): ShoppingItemEntity {
        val resolvedListId = if (listId.isBlank()) ShoppingRepository.DEFAULT_LIST_ID else listId
        return ShoppingItemEntity(
            id = id,
            listId = resolvedListId,
            title = title,
            createdByUserId = createdByUserId,
            isBought = bought,
            createdAt = createdAt,
            categoryId = categoryId,
            quantity = quantity,
            quantityUnit = quantityUnit,
            note = note,
            boughtAt = boughtAt
        )
    }

    private fun ExportEvent.toEntity(): FamilyEventEntity =
        FamilyEventEntity(
            id = id,
            title = title,
            startDateTime = startDateTime,
            createdByUserId = createdByUserId,
            durationMinutes = durationMinutes,
            recurrence = recurrence,
            reminderMinutesBefore = reminderMinutesBefore
        )

    private fun ExportScore.toEntity(): UserScoreEntity =
        UserScoreEntity(
            userId = userId,
            weekStartDate = weekStartDate,
            score = score,
            updatedAt = updatedAt
        )

    private fun ExportTaskCompletion.toEntity(): TaskCompletionEntity =
        TaskCompletionEntity(
            id = id,
            taskId = taskId,
            userId = userId,
            taskType = taskType,
            completionTime = completionTime,
            weekStartDate = weekStartDate,
            basePoints = basePoints,
            bonusPoints = bonusPoints,
            penaltyPoints = penaltyPoints,
            totalPoints = totalPoints,
            reversedAt = reversedAt,
            reversedByUserId = reversedByUserId,
            updatedAt = updatedAt,
            generatedRepeatTaskId = generatedRepeatTaskId,
            completionCycle = completionCycle
        )

    private fun ExportAchievement.toEntity(): UserAchievementEntity =
        UserAchievementEntity(
            userId = userId,
            type = type,
            earnedAt = earnedAt
        )

    private fun ExportReward.toEntity(): RewardEntity =
        RewardEntity(
            id = id,
            title = title,
            description = description,
            cost = cost,
            createdByUserId = createdByUserId,
            isActive = active,
            createdAt = createdAt
        )

    private fun ExportRewardRequest.toEntity(): RewardRequestEntity =
        RewardRequestEntity(
            id = id,
            rewardId = rewardId,
            rewardTitle = rewardTitle,
            rewardCost = rewardCost,
            requesterUserId = requesterUserId,
            note = note,
            status = status,
            requestedAt = requestedAt,
            processedAt = processedAt,
            processedByUserId = processedByUserId
        )

    private fun ExportWallet.toEntity(): UserWalletEntity =
        UserWalletEntity(
            userId = userId,
            balance = balance,
            updatedAt = updatedAt
        )

    private fun ExportTaskRotation.toEntity(): TaskRotationEntity =
        TaskRotationEntity(
            taskId = taskId,
            memberIds = memberIds,
            currentIndex = currentIndex,
            frequency = frequency,
            startDateTime = startDateTime,
            nextRotationAt = nextRotationAt,
            lastRotationAt = lastRotationAt
        )

    private fun ExportTaskTemplate.toEntity(): TaskTemplateEntity {
        val priorityValue = runCatching { TaskPriority.valueOf(priority) }.getOrDefault(TaskPriority.MEDIUM)
        return TaskTemplateEntity(
            id = id,
            title = title,
            description = description,
            repeatRule = repeatRule,
            defaultAssigneeId = defaultAssigneeId,
            isPrivate = this.`private`,
            taskType = taskType,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            categoryId = categoryId,
            priority = priorityValue.name
        )
    }

    private fun ExportTaskCategory.toEntity(): TaskCategoryEntity =
        TaskCategoryEntity(
            id = id,
            name = name,
            color = color,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun ExportHouseRule.toEntity(): HouseRuleEntity =
        HouseRuleEntity(
            id = id,
            title = title,
            description = description,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun ExportAppreciation.toEntity(): AppreciationEntity =
        AppreciationEntity(
            id = id,
            senderId = senderId,
            recipientId = recipientId,
            message = message,
            createdAt = createdAt
        )

    private fun ExportFamilyGoalStage.toEntity(): FamilyGoalStageEntity =
        FamilyGoalStageEntity(
            id = id,
            goalId = goalId,
            title = title,
            description = description,
            position = position
        )

    private fun ExportFamilyGoalStep.toEntity(): FamilyGoalStepEntity =
        FamilyGoalStepEntity(
            id = id,
            goalId = goalId,
            stageId = stageId,
            title = title,
            isCompleted = completed,
            position = position
        )

    private fun ExportFamilyGoal.toEntity(): FamilyGoalEntity =
        FamilyGoalEntity(
            id = id,
            title = title,
            description = description,
            targetPoints = targetPoints,
            currentPoints = currentPoints,
            deadlineAt = deadlineAt,
            status = status,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            personal = personal
        )

    private fun ExportEmergencyContact.toEntity(): EmergencyContactEntity =
        EmergencyContactEntity(
            id = id,
            name = name,
            phoneNumber = phoneNumber,
            description = description,
            createdAt = createdAt
        )

    private fun ExportRecipe.toEntity(): RecipeEntities {
        val difficultyEnum = try {
            com.familyhub.domain.model.RecipeDifficulty.valueOf(difficulty)
        } catch (_: Exception) {
            com.familyhub.domain.model.RecipeDifficulty.MEDIUM
        }
        val categoryEnum = try {
            com.familyhub.domain.model.RecipeCategory.valueOf(category)
        } catch (_: Exception) {
            com.familyhub.domain.model.RecipeCategory.SECOND // Fallback default
        }
        val recipeEntity = RecipeEntity(
            id = id,
            title = title,
            description = description ?: "",
            servings = servings,
            prepTimeMinutes = prepTimeMinutes,
            difficulty = difficultyEnum.name,
            isFavorite = isFavorite,
            imageUrl = imageUrl,
            notes = notes ?: "",
            tags = tags,
            category = categoryEnum.name,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
        val ingredientEntities = ingredients.map {
            RecipeIngredientEntity(
                id = it.id.ifBlank { java.util.UUID.randomUUID().toString() },
                recipeId = id,
                name = it.name,
                quantity = it.quantity
            )
        }
        val stepEntities = steps.map {
            RecipeStepEntity(
                id = it.id.ifBlank { java.util.UUID.randomUUID().toString() },
                recipeId = id,
                stepOrder = it.order,
                description = it.description
            )
        }
        return RecipeEntities(
            recipe = recipeEntity,
            ingredients = ingredientEntities,
            steps = stepEntities
        )
    }

    private fun ExportWeeklyMenu.toEntity(): WeeklyMenuEntity = WeeklyMenuEntity(
        id = id,
        startDateMillis = startDateMillis,
        isAutoGenerated = isAutoGenerated,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ExportMenuTemplate.toEntity(): MenuTemplateEntity = MenuTemplateEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ExportMenuTemplateEntry.toEntity(): MenuTemplateEntryEntity = MenuTemplateEntryEntity(
        id = id,
        templateId = templateId,
        dayOfWeek = dayOfWeek,
        mealType = mealType,
        recipeId = recipeId,
        customDish = customDish,
        servings = servings,
        assignedMemberId = assignedMemberId,
        cookingTaskId = cookingTaskId,
        prepTaskId = prepTaskId,
        updatedAt = updatedAt
    )

    private fun ExportMenuEntry.toEntity(): MenuEntryEntity = MenuEntryEntity(
        id = id,
        menuId = menuId,
        dayOfWeek = dayOfWeek,
        mealType = mealType,
        recipeId = recipeId,
        customDish = customDish,
        servings = servings,
        assignedMemberId = assignedMemberId,
        cookingTaskId = cookingTaskId,
        prepTaskId = prepTaskId,
        updatedAt = updatedAt
    )

    private fun ExportTaskHistory.toEntity(): TaskHistoryEntity = TaskHistoryEntity(
        id = id,
        taskId = taskId,
        changedByUserId = changedByUserId,
        changeType = changeType,
        changeDescription = changeDescription,
        oldValue = oldValue,
        newValue = newValue,
        timestamp = timestamp
    )

    private fun ExportDailyAssignment.toEntity(): DailyAssignmentEntity = DailyAssignmentEntity(
        id = id,
        weekStartMillis = weekStartMillis,
        dayOfWeek = dayOfWeek,
        assignedMemberId = assignedMemberId,
        rewardPoints = rewardPoints,
        isRated = isRated,
        updatedAt = updatedAt
    )
}
