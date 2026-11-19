package com.familyhub.data.restore

import com.familyhub.data.backup.EncryptedBackupFormat
import com.familyhub.data.backup.EncryptedBackupPayload
import com.familyhub.data.export.ExportData
import com.familyhub.data.importer.ImportCycleValidation
import com.familyhub.data.menu.MenuSnapshotCanonicalizer
import com.familyhub.domain.model.AchievementType
import com.familyhub.domain.model.EventRecurrence
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RecipeCategory
import com.familyhub.domain.model.RecipeDifficulty
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.model.TaskHistoryChangeType
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskRotationFrequency
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import com.familyhub.domain.utils.PenaltySnapshotValidation
import java.nio.charset.StandardCharsets

/**
 * Fail-closed validation for encrypted restore. Produces an immutable snapshot for Room replace.
 */
object RestorePayloadValidator {

    const val MAX_COLLECTION_SIZE = 50_000
    const val MAX_STRING_CHARS = 8_192
    const val MAX_MEMBERS = 500
    const val MAX_NESTED_LIST_SIZE = 2_000
    const val MAX_FIRESTORE_ID_UTF8_BYTES = 1500
    const val MIN_DAY_OF_WEEK = 0
    const val MAX_DAY_OF_WEEK = 6
    const val MIN_SERVINGS = 1
    const val MAX_SERVINGS = 99

    fun validate(payload: EncryptedBackupPayload): ValidatedRestoreSnapshot {
        if (payload.payloadVersion != EncryptedBackupFormat.PAYLOAD_VERSION) {
            throw RestoreValidationException("Unsupported restore payload version")
        }
        validateSourceFamilyId(payload.sourceFamilyId)

        var data = payload.data
        validateCollectionLimits(data)
        validateEnums(data)
        validateMenuFields(data)
        validateRelations(data)

        data = ImportCycleValidation.validateAndNormalize(data)
        data = try {
            MenuSnapshotCanonicalizer.canonicalize(data)
        } catch (e: IllegalArgumentException) {
            throw RestoreValidationException(e.message ?: "Invalid menu snapshot")
        }
        PenaltySnapshotValidation.validateExportSnapshotPenalties(data)
        validateUniqueIds(data)
        validateAccountingUniqueness(data)

        val counts = countsOf(data)
        return ValidatedRestoreSnapshot(
            payloadVersion = payload.payloadVersion,
            createdAt = payload.createdAt,
            sourceFamilyId = payload.sourceFamilyId,
            data = data,
            counts = counts
        )
    }

    fun countsOf(data: ExportData): RestoreCollectionCounts =
        RestoreCollectionCounts(
            members = data.members.size,
            tasks = data.tasks.size,
            shoppingLists = data.shoppingLists.size,
            shoppingItems = data.shoppingItems.size,
            events = data.events.size,
            recipes = data.recipes.size,
            familyGoals = data.familyGoals.size,
            rewards = data.rewards.size,
            penalties = data.penalties.size,
            weeklyMenus = data.weeklyMenus.size,
            menuTemplates = data.menuTemplates.size
        )

    fun validateSourceFamilyId(sourceFamilyId: String?) {
        if (sourceFamilyId == null) return
        if (!isValidFirestoreDocumentId(sourceFamilyId)) {
            throw RestoreValidationException("Invalid sourceFamilyId")
        }
    }

    fun isValidFirestoreDocumentId(id: String): Boolean {
        if (id.isEmpty()) return false
        if (id == "." || id == "..") return false
        if (id.contains('/')) return false
        // Firestore reserved document ids: __.*__
        if (id.length >= 4 && id.startsWith("__") && id.endsWith("__")) return false
        if (!hasValidUtf16Surrogates(id)) return false
        val utf8Bytes = id.toByteArray(StandardCharsets.UTF_8).size
        return utf8Bytes <= MAX_FIRESTORE_ID_UTF8_BYTES
    }

    private fun hasValidUtf16Surrogates(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            when {
                Character.isHighSurrogate(ch) -> {
                    if (i + 1 >= value.length || !Character.isLowSurrogate(value[i + 1])) {
                        return false
                    }
                    i += 2
                }
                Character.isLowSurrogate(ch) -> return false
                else -> i++
            }
        }
        return true
    }

    private fun validateCollectionLimits(data: ExportData) {
        fun check(name: String, size: Int, max: Int = MAX_COLLECTION_SIZE) {
            if (size > max) {
                throw RestoreValidationException("Collection $name exceeds limit ($size > $max)")
            }
        }
        check("members", data.members.size, MAX_MEMBERS)
        check("tasks", data.tasks.size)
        check("shoppingLists", data.shoppingLists.size)
        check("shoppingCategories", data.shoppingCategories.size)
        check("shoppingItems", data.shoppingItems.size)
        check("shoppingActivities", data.shoppingActivities.size)
        check("events", data.events.size)
        check("scores", data.scores.size)
        check("taskCompletions", data.taskCompletions.size)
        check("achievements", data.achievements.size)
        check("rewards", data.rewards.size)
        check("rewardRequests", data.rewardRequests.size)
        check("wallets", data.wallets.size)
        check("taskRotations", data.taskRotations.size)
        check("houseRules", data.houseRules.size)
        check("taskTemplates", data.taskTemplates.size)
        check("taskCategories", data.taskCategories.size)
        check("appreciations", data.appreciations.size)
        check("familyGoals", data.familyGoals.size)
        check("familyGoalStages", data.familyGoalStages.size)
        check("familyGoalSteps", data.familyGoalSteps.size)
        check("penalties", data.penalties.size)
        check("emergencyContacts", data.emergencyContacts.size)
        check("recipes", data.recipes.size)
        check("menuTemplates", data.menuTemplates.size)
        check("menuTemplateEntries", data.menuTemplateEntries.size)
        check("weeklyMenus", data.weeklyMenus.size)
        check("menuEntries", data.menuEntries.size)
        check("taskHistory", data.taskHistory.size)
        check("dailyAssignments", data.dailyAssignments.size)

        data.members.forEach { requireBoundedString("member.displayName", it.displayName) }
        data.tasks.forEach {
            requireBoundedString("task.title", it.title)
            it.description?.let { value -> requireBoundedString("task.description", value) }
        }
        data.recipes.forEach { recipe ->
            requireBoundedString("recipe.title", recipe.title)
            if (recipe.ingredients.size > MAX_NESTED_LIST_SIZE ||
                recipe.steps.size > MAX_NESTED_LIST_SIZE
            ) {
                throw RestoreValidationException("Recipe nested list exceeds limit")
            }
        }
        data.menuTemplates.forEach {
            requireBoundedString("menuTemplate.name", it.name)
        }
    }

    private fun requireBoundedString(label: String, value: String) {
        if (value.length > MAX_STRING_CHARS) {
            throw RestoreValidationException("$label exceeds maximum length")
        }
    }

    private fun validateUniqueIds(data: ExportData) {
        requireUnique("members", data.members.map { it.id })
        requireUnique("tasks", data.tasks.map { it.id })
        requireUnique("shoppingLists", data.shoppingLists.map { it.id })
        requireUnique("shoppingCategories", data.shoppingCategories.map { it.id })
        requireUnique("shoppingItems", data.shoppingItems.map { it.id })
        requireUnique("shoppingActivities", data.shoppingActivities.map { it.id })
        requireUnique("events", data.events.map { it.id })
        requireUnique("taskCompletions", data.taskCompletions.map { it.id })
        requireUnique("rewards", data.rewards.map { it.id })
        requireUnique("rewardRequests", data.rewardRequests.map { it.id })
        requireUnique("taskRotations", data.taskRotations.map { it.taskId })
        requireUnique("houseRules", data.houseRules.map { it.id })
        requireUnique("taskTemplates", data.taskTemplates.map { it.id })
        requireUnique("taskCategories", data.taskCategories.map { it.id })
        requireUnique("appreciations", data.appreciations.map { it.id })
        requireUnique("familyGoals", data.familyGoals.map { it.id })
        requireUnique("familyGoalStages", data.familyGoalStages.map { it.id })
        requireUnique("familyGoalSteps", data.familyGoalSteps.map { it.id })
        requireUnique("penalties", data.penalties.map { it.id })
        requireUnique("emergencyContacts", data.emergencyContacts.map { it.id })
        requireUnique("recipes", data.recipes.map { it.id })
        requireUnique("menuTemplates", data.menuTemplates.map { it.id })
        requireUniqueFirestoreIds("menuTemplates", data.menuTemplates.map { it.id })
        requireUnique("menuTemplateEntries", data.menuTemplateEntries.map { it.id })
        requireUniqueFirestoreIds("menuTemplateEntries", data.menuTemplateEntries.map { it.id })
        requireUnique("weeklyMenus", data.weeklyMenus.map { it.id })
        requireUniqueFirestoreIds("weeklyMenus", data.weeklyMenus.map { it.id })
        requireUnique("menuEntries", data.menuEntries.map { it.id })
        requireUniqueFirestoreIds("menuEntries", data.menuEntries.map { it.id })
        requireUnique("taskHistory", data.taskHistory.map { it.id })
        requireUnique("dailyAssignments", data.dailyAssignments.map { it.id })
        requireUniqueFirestoreIds("dailyAssignments", data.dailyAssignments.map { it.id })

        // Global PK uniqueness for nested recipe child rows (Room PK is id, not per-recipe).
        requireUnique(
            "recipeIngredients",
            data.recipes.flatMap { recipe -> recipe.ingredients.map { it.id } }
        )
        requireUnique(
            "recipeSteps",
            data.recipes.flatMap { recipe -> recipe.steps.map { it.id } }
        )

        val stepOrderKeys = HashSet<Pair<String, Int>>()
        data.recipes.forEach { recipe ->
            recipe.steps.forEach { step ->
                val key = recipe.id to step.order
                if (!stepOrderKeys.add(key)) {
                    throw RestoreValidationException(
                        "Duplicate recipe step order for recipeId=${recipe.id} order=${step.order}"
                    )
                }
            }
        }

        val achievementKeys = HashSet<Pair<String, String>>()
        data.achievements.forEach { achievement ->
            val key = achievement.userId to achievement.type
            if (achievement.userId.isBlank() || achievement.type.isBlank()) {
                throw RestoreValidationException("Achievement contains blank userId/type")
            }
            if (!achievementKeys.add(key)) {
                throw RestoreValidationException(
                    "Duplicate achievement for userId=${achievement.userId} type=${achievement.type}"
                )
            }
        }

        val menuEntryKeys = HashSet<Triple<String, Int, String>>()
        data.menuEntries.forEach { entry ->
            val key = Triple(entry.menuId, entry.dayOfWeek, entry.mealType)
            if (!menuEntryKeys.add(key)) {
                throw RestoreValidationException(
                    "Duplicate menu entry for menuId=${entry.menuId} " +
                        "day=${entry.dayOfWeek} meal=${entry.mealType}"
                )
            }
        }

        val menuTemplateEntryKeys = HashSet<Triple<String, Int, String>>()
        data.menuTemplateEntries.forEach { entry ->
            val key = Triple(entry.templateId, entry.dayOfWeek, entry.mealType)
            if (!menuTemplateEntryKeys.add(key)) {
                throw RestoreValidationException(
                    "Duplicate menu template entry for templateId=${entry.templateId} " +
                        "day=${entry.dayOfWeek} meal=${entry.mealType}"
                )
            }
        }

        val dailyAssignmentKeys = HashSet<Pair<Long, Int>>()
        data.dailyAssignments.forEach { assignment ->
            val key = assignment.weekStartMillis to assignment.dayOfWeek
            if (!dailyAssignmentKeys.add(key)) {
                throw RestoreValidationException(
                    "Duplicate daily assignment for week=${assignment.weekStartMillis} " +
                        "day=${assignment.dayOfWeek}"
                )
            }
        }
    }

    private fun requireUnique(label: String, ids: List<String>) {
        val seen = HashSet<String>()
        ids.forEach { id ->
            if (id.isBlank()) {
                throw RestoreValidationException("$label contains a blank id")
            }
            if (!seen.add(id)) {
                throw RestoreValidationException("Duplicate id in $label: $id")
            }
        }
    }

    private fun requireUniqueFirestoreIds(label: String, ids: List<String>) {
        ids.forEach { id ->
            if (!isValidFirestoreDocumentId(id)) {
                throw RestoreValidationException("Invalid Firestore id in $label: $id")
            }
        }
    }

    private fun validateEnums(data: ExportData) {
        data.members.forEach { requireEnum("member.role", it.role) { MemberRole.valueOf(it) } }
        data.tasks.forEach { task ->
            requireEnum("task.status", task.status) { TaskStatus.valueOf(it) }
            requireEnum("task.taskType", task.taskType) { TaskType.valueOf(it) }
            requireEnum("task.priority", task.priority) { TaskPriority.valueOf(it) }
            requireEnum("task.repeatRule", task.repeatRule) { RepeatRule.valueOf(it) }
        }
        data.events.forEach { event ->
            if (event.recurrence.isNotBlank()) {
                requireEnum("event.recurrence", event.recurrence) { EventRecurrence.valueOf(it) }
            }
        }
        data.rewardRequests.forEach {
            requireEnum("rewardRequest.status", it.status) { RewardRequestStatus.valueOf(it) }
        }
        data.taskRotations.forEach {
            requireEnum("taskRotation.frequency", it.frequency) { TaskRotationFrequency.valueOf(it) }
        }
        data.familyGoals.forEach {
            requireEnum("familyGoal.status", it.status) { FamilyGoalStatus.valueOf(it) }
        }
        data.recipes.forEach { recipe ->
            requireEnum("recipe.difficulty", recipe.difficulty) { RecipeDifficulty.valueOf(it) }
            requireEnum("recipe.category", recipe.category) { RecipeCategory.valueOf(it) }
        }
        data.menuEntries.forEach {
            requireEnum("menuEntry.mealType", it.mealType) { MealType.valueOf(it) }
        }
        data.menuTemplateEntries.forEach {
            requireEnum("menuTemplateEntry.mealType", it.mealType) { MealType.valueOf(it) }
        }
        data.taskHistory.forEach {
            requireEnum("taskHistory.changeType", it.changeType) { TaskHistoryChangeType.valueOf(it) }
        }
        data.achievements.forEach {
            requireEnum("achievement.type", it.type) { AchievementType.valueOf(it) }
        }
    }

    private inline fun <T> requireEnum(label: String, raw: String, parse: (String) -> T) {
        if (raw.isBlank()) {
            throw RestoreValidationException("$label is blank")
        }
        try {
            parse(raw)
        } catch (_: IllegalArgumentException) {
            throw RestoreValidationException("Invalid enum value for $label: $raw")
        }
    }

    private fun validateMenuFields(data: ExportData) {
        data.menuEntries.forEach { entry ->
            requireDayOfWeek("menuEntry", entry.id, entry.dayOfWeek)
            requireServings("menuEntry", entry.id, entry.servings)
        }
        data.menuTemplateEntries.forEach { entry ->
            requireDayOfWeek("menuTemplateEntry", entry.id, entry.dayOfWeek)
            requireServings("menuTemplateEntry", entry.id, entry.servings)
        }
        data.dailyAssignments.forEach { assignment ->
            requireDayOfWeek("dailyAssignment", assignment.id, assignment.dayOfWeek)
        }
    }

    private fun requireDayOfWeek(label: String, id: String, dayOfWeek: Int) {
        if (dayOfWeek !in MIN_DAY_OF_WEEK..MAX_DAY_OF_WEEK) {
            throw RestoreValidationException(
                "Invalid dayOfWeek for $label id=$id: $dayOfWeek"
            )
        }
    }

    private fun requireServings(label: String, id: String, servings: Int) {
        if (servings !in MIN_SERVINGS..MAX_SERVINGS) {
            throw RestoreValidationException(
                "Invalid servings for $label id=$id: $servings"
            )
        }
    }

    private fun validateRelations(data: ExportData) {
        val taskIds = data.tasks.map { it.id }.toSet()
        val listIds = data.shoppingLists.map { it.id }.toSet()
        val categoryIds = data.shoppingCategories.map { it.id }.toSet()
        val goalIds = data.familyGoals.map { it.id }.toSet()
        val stageIds = data.familyGoalStages.map { it.id }.toSet()
        val recipeIds = data.recipes.map { it.id }.toSet()
        val menuIds = data.weeklyMenus.map { it.id }.toSet()
        val templateIds = data.menuTemplates.map { it.id }.toSet()
        val taskCategoryIds = data.taskCategories.map { it.id }.toSet()

        data.familyGoalStages.forEach { stage ->
            if (stage.goalId !in goalIds) {
                throw RestoreValidationException("Dangling stage→goal: ${stage.id}")
            }
        }
        data.familyGoalSteps.forEach { step ->
            if (step.goalId !in goalIds) {
                throw RestoreValidationException("Dangling step→goal: ${step.id}")
            }
            if (step.stageId.isNotBlank() && step.stageId !in stageIds) {
                throw RestoreValidationException("Dangling step→stage: ${step.id}")
            }
        }
        data.shoppingCategories.forEach { category ->
            if (category.listId !in listIds) {
                throw RestoreValidationException("Dangling shopping category→list: ${category.id}")
            }
        }
        data.shoppingItems.forEach { item ->
            if (item.listId.isNotBlank() && item.listId !in listIds) {
                throw RestoreValidationException("Dangling shopping item→list: ${item.id}")
            }
            val categoryId = item.categoryId
            if (!categoryId.isNullOrBlank() && categoryId !in categoryIds) {
                throw RestoreValidationException("Dangling shopping item→category: ${item.id}")
            }
        }
        data.shoppingActivities.forEach { activity ->
            if (activity.listId !in listIds) {
                throw RestoreValidationException("Dangling shopping activity→list: ${activity.id}")
            }
            // Historical activity after item delete is retained in export; no Room FK.
        }
        data.menuEntries.forEach { entry ->
            if (entry.menuId !in menuIds) {
                throw RestoreValidationException("Dangling menu entry→menu: ${entry.id}")
            }
            val recipeId = entry.recipeId
            if (!recipeId.isNullOrBlank() && recipeId !in recipeIds) {
                throw RestoreValidationException("Dangling menu entry→recipe: ${entry.id}")
            }
        }
        data.menuTemplateEntries.forEach { entry ->
            if (entry.templateId !in templateIds) {
                throw RestoreValidationException("Dangling menu template entry→template: ${entry.id}")
            }
            val recipeId = entry.recipeId
            if (!recipeId.isNullOrBlank() && recipeId !in recipeIds) {
                throw RestoreValidationException("Dangling menu template entry→recipe: ${entry.id}")
            }
        }
        data.tasks.forEach { task ->
            val categoryId = task.categoryId
            if (!categoryId.isNullOrBlank() && categoryId !in taskCategoryIds) {
                throw RestoreValidationException("Dangling task→category: ${task.id}")
            }
        }
        data.taskRotations.forEach { rotation ->
            if (rotation.taskId !in taskIds) {
                throw RestoreValidationException("Dangling task rotation→task: ${rotation.taskId}")
            }
        }
        // taskCompletions.taskId and rewardRequests.rewardId may orphan after delete; no Room FK.
        // Historical appreciations may outlive deleted members; no Room FK.
        data.taskHistory.forEach { history ->
            if (history.taskId.isNotBlank() && history.taskId !in taskIds) {
                throw RestoreValidationException("Dangling task history→task: ${history.id}")
            }
        }
    }

    private fun validateAccountingUniqueness(data: ExportData) {
        val scoreKeys = HashSet<Pair<String, Long>>()
        data.scores.forEach { score ->
            val key = score.userId to score.weekStartDate
            if (!scoreKeys.add(key)) {
                throw RestoreValidationException(
                    "Duplicate score ledger for userId=${score.userId} week=${score.weekStartDate}"
                )
            }
        }
        val walletKeys = HashSet<String>()
        data.wallets.forEach { wallet ->
            if (!walletKeys.add(wallet.userId)) {
                throw RestoreValidationException("Duplicate wallet for userId=${wallet.userId}")
            }
        }
    }
}
