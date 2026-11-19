package com.familyhub.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.familyhub.R
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.RewardRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.TaskRotationRepository
import com.familyhub.data.repository.HouseRulesRepository
import com.familyhub.data.repository.TaskTemplateRepository
import com.familyhub.data.repository.TaskCategoryRepository
import com.familyhub.data.repository.AppreciationRepository
import com.familyhub.data.repository.FamilyGoalsRepository
import com.familyhub.data.repository.EmergencyContactRepository
import com.familyhub.data.repository.PenaltyRepository
import com.familyhub.data.repository.RecipeRepository
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.export.toExport
import com.familyhub.domain.model.Reward
import com.familyhub.domain.model.RewardRequest
import com.familyhub.domain.model.TaskCompletion
import com.familyhub.domain.model.TaskAttachment
import com.familyhub.domain.model.TaskComment
import com.familyhub.domain.model.HouseRule
import com.familyhub.domain.model.TaskTemplate
import com.familyhub.domain.model.TaskCategory
import com.familyhub.domain.model.Appreciation
import com.familyhub.domain.model.UserAchievement
import com.familyhub.domain.model.UserWallet
import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalStage
import com.familyhub.domain.model.FamilyGoalStep
import com.familyhub.domain.model.ShoppingActivity
import com.familyhub.domain.utils.filterByVisibility
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.firestore.PropertyName

enum class ExportFormat {
    CSV,
    JSON
}

@Serializable
data class ExportData(
    val members: List<ExportMember> = emptyList(),
    val tasks: List<ExportTask> = emptyList(),
    val shoppingLists: List<ExportShoppingList> = emptyList(),
    val shoppingCategories: List<ExportShoppingCategory> = emptyList(),
    val shoppingItems: List<ExportShoppingItem> = emptyList(),
    val shoppingActivities: List<ExportShoppingActivity> = emptyList(),
    val events: List<ExportEvent> = emptyList(),
    val scores: List<ExportScore> = emptyList(),
    val taskCompletions: List<ExportTaskCompletion> = emptyList(),
    val achievements: List<ExportAchievement> = emptyList(),
    val rewards: List<ExportReward> = emptyList(),
    val rewardRequests: List<ExportRewardRequest> = emptyList(),
    val wallets: List<ExportWallet> = emptyList(),
    val taskRotations: List<ExportTaskRotation> = emptyList(),
    val houseRules: List<ExportHouseRule> = emptyList(),
    val taskTemplates: List<ExportTaskTemplate> = emptyList(),
    val taskCategories: List<ExportTaskCategory> = emptyList(),
    val appreciations: List<ExportAppreciation> = emptyList(),
    val familyGoals: List<ExportFamilyGoal> = emptyList(),
    val familyGoalStages: List<ExportFamilyGoalStage> = emptyList(),
    val familyGoalSteps: List<ExportFamilyGoalStep> = emptyList(),
    val penalties: List<ExportPenalty> = emptyList(),
    val emergencyContacts: List<ExportEmergencyContact> = emptyList(),
    val recipes: List<ExportRecipe> = emptyList(),
    val menuTemplates: List<ExportMenuTemplate> = emptyList(),
    val menuTemplateEntries: List<ExportMenuTemplateEntry> = emptyList(),
    val weeklyMenus: List<ExportWeeklyMenu> = emptyList(),
    val menuEntries: List<ExportMenuEntry> = emptyList(),
    val taskHistory: List<ExportTaskHistory> = emptyList(),
    val dailyAssignments: List<ExportDailyAssignment> = emptyList(),
    val exportDate: String = "",
    val version: Long = 0, // Firestore document version (updateTime seconds)
    val versionNanos: Int = 0 // Firestore document version (updateTime nanoseconds)
)

@Serializable
data class ExportMember(
    val id: String = "",
    val displayName: String = "",
    val roleLabel: String = "",
    val avatarColor: String = "",
    val role: String = "",
    val birthDate: String? = null,
    val age: Int? = null,
    val photoUrl: String? = null
)

@Serializable
data class ExportTask(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val createdByUserId: String = "",
    val assignedToUserId: String = "",
    val dueDate: Long? = null,
    val dueDateHasTime: Boolean = false,
    val reminderMode: String = "NONE",
    val reminderMinutesOfDay: Int? = null,
    val repeatRule: String = "",
    val status: String = "",
    val createdAt: Long = 0L,
    val orderIndex: Int = 0,
    val taskType: String = "",
    val `private`: Boolean = false,
    val priority: String = "MEDIUM",
    val categoryId: String? = null,
    val rewardPoints: Int? = null,
    val requiresApproval: Boolean = false,
    val attachments: List<ExportTaskAttachment> = emptyList(),
    val comments: List<ExportTaskComment> = emptyList(),
    val completionCycle: Long = 0L
)

@Serializable
data class ExportTaskAttachment(
    val id: String = "",
    val name: String = "",
    val uri: String = "",
    val mimeType: String? = null,
    val addedAt: Long = 0L
)

@Serializable
data class ExportTaskComment(
    val id: String = "",
    val authorId: String = "",
    val message: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class ExportShoppingList(
    val id: String = "",
    val title: String = "",
    val colorHex: String = "",
    val iconName: String = "",
    val createdAt: Long = 0L,
    val archived: Boolean = false,
    val archivedAt: Long? = null,
    val templateKey: String? = null,
    val participantIds: List<String> = emptyList()
)

@Serializable
data class ExportShoppingCategory(
    val id: String = "",
    val listId: String = "",
    val title: String = "",
    val colorHex: String = "",
    val iconName: String = "",
    val sortOrder: Int = 0
)

@Serializable
data class ExportShoppingItem(
    val id: String = "",
    val listId: String = "",
    val title: String = "",
    val createdByUserId: String = "",
    val bought: Boolean = false,
    val createdAt: Long = 0L,
    val categoryId: String? = null,
    val quantity: Double? = null,
    val quantityUnit: String? = null,
    val note: String? = null,
    val `private`: Boolean = false,
    val boughtAt: Long? = null
)

@Serializable
data class ExportShoppingActivity(
    val id: String = "",
    val listId: String = "",
    val itemId: String? = null,
    val actorId: String = "",
    val actionType: String = "",
    val occurredAt: Long = 0L,
    val payload: Map<String, String> = emptyMap()
)

@Serializable
data class ExportEvent(
    val id: String = "",
    val title: String = "",
    val startDateTime: Long = 0L,
    val createdByUserId: String = "",
    val durationMinutes: Int = 0,
    val recurrence: String = "",
    val reminderMinutesBefore: Int? = null
)

@Serializable
data class ExportScore(
    val userId: String = "",
    val weekStartDate: Long = 0L,
    val score: Int = 0,
    val updatedAt: Long = 0L
)

@Serializable
data class ExportTaskCompletion(
    val id: String = "",
    val taskId: String = "",
    val userId: String = "",
    val taskType: String = "",
    val completionTime: Long = 0L,
    val weekStartDate: Long = 0L,
    val basePoints: Int = 0,
    val bonusPoints: Int = 0,
    val penaltyPoints: Int = 0,
    val totalPoints: Int = 0,
    val reversedAt: Long? = null,
    val reversedByUserId: String? = null,
    val updatedAt: Long = 0L,
    val generatedRepeatTaskId: String? = null,
    val completionCycle: Long? = null
)

@Serializable
data class ExportAchievement(
    val userId: String = "",
    val type: String = "",
    val earnedAt: Long = 0L
)

@Serializable
data class ExportReward(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val cost: Int = 0,
    val createdByUserId: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L
)

@Serializable
data class ExportRewardRequest(
    val id: String = "",
    val rewardId: String = "",
    val rewardTitle: String = "",
    val rewardCost: Int = 0,
    val requesterUserId: String = "",
    val note: String? = null,
    val status: String = "",
    val requestedAt: Long = 0L,
    val processedAt: Long? = null,
    val processedByUserId: String? = null
)

@Serializable
data class ExportWallet(
    val userId: String = "",
    val balance: Int = 0,
    val updatedAt: Long = 0L
)

@Serializable
data class ExportTaskRotation(
    val taskId: String = "",
    val memberIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val frequency: String = "",
    val startDateTime: Long = 0L,
    val nextRotationAt: Long = 0L,
    val lastRotationAt: Long? = null
)

@Serializable
data class ExportHouseRule(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val createdByUserId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class ExportTaskTemplate(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val repeatRule: String = "",
    val defaultAssigneeId: String? = null,
    val `private`: Boolean = false,
    val taskType: String = "",
    val createdByUserId: String = "",
    val createdAt: Long = 0L,
    val categoryId: String? = null,
    val priority: String = "MEDIUM"
)

@Serializable
data class ExportTaskCategory(
    val id: String = "",
    val name: String = "",
    val color: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class ExportAppreciation(
    val id: String = "",
    val senderId: String = "",
    val recipientId: String = "",
    val message: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class ExportFamilyGoalStage(
    val id: String = "",
    val goalId: String = "",
    val title: String = "",
    val description: String? = null,
    val position: Int = 0
)

@Serializable
data class ExportFamilyGoalStep(
    val id: String = "",
    val goalId: String = "",
    val stageId: String = "",
    val title: String = "",
    @SerialName("completed")
    @get:PropertyName("completed")
    @set:PropertyName("completed")
    var completed: Boolean = false,
    val position: Int = 0
)

@Serializable
data class ExportFamilyGoal(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val targetPoints: Int = 0,
    val currentPoints: Int = 0,
    val deadlineAt: Long? = null,
    val status: String = "",
    val createdByUserId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val personal: Boolean = false
)

@Serializable
data class ExportEmergencyContact(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val description: String? = null,
    val createdAt: Long = 0L
)

@Serializable
data class ExportMenuTemplate(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class ExportMenuTemplateEntry(
    val id: String = "",
    val templateId: String = "",
    val dayOfWeek: Int = 0,
    val mealType: String = "",
    val recipeId: String? = null,
    val customDish: String? = null,
    val servings: Int = 1,
    val assignedMemberId: String? = null,
    val cookingTaskId: String? = null,
    val prepTaskId: String? = null,
    val updatedAt: Long = 0L
)

@Serializable
data class ExportWeeklyMenu(
    val id: String = "",
    val startDateMillis: Long = 0L,
    val isAutoGenerated: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class ExportMenuEntry(
    val id: String = "",
    val menuId: String = "",
    val dayOfWeek: Int = 0,
    val mealType: String = "",
    val recipeId: String? = null,
    val customDish: String? = null,
    val servings: Int = 1,
    val assignedMemberId: String? = null,
    val cookingTaskId: String? = null,
    val prepTaskId: String? = null,
    val updatedAt: Long = 0L
)

@Serializable
data class ExportTaskHistory(
    val id: String = "",
    val taskId: String = "",
    val changedByUserId: String = "",
    val changeType: String = "",
    val changeDescription: String = "",
    val oldValue: String? = null,
    val newValue: String? = null,
    val timestamp: Long = 0L
)

@Serializable
data class ExportDailyAssignment(
    val id: String = "",
    val weekStartMillis: Long = 0L,
    val dayOfWeek: Int = 0,
    val assignedMemberId: String? = null,
    val rewardPoints: Int? = null,
    val isRated: Boolean = false,
    val updatedAt: Long = 0L
)

@Singleton
class DataExportService @Inject constructor(
    private val context: Context,
    private val memberRepository: FamilyMemberRepository,
    private val taskRepository: TaskRepository,
    private val shoppingRepository: ShoppingRepository,
    private val eventRepository: FamilyEventRepository,
    private val scoreRepository: ScoreRepository,
    private val rewardRepository: RewardRepository,
    private val taskRotationRepository: TaskRotationRepository,
    private val houseRulesRepository: HouseRulesRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskCategoryRepository: TaskCategoryRepository,
    private val appreciationRepository: AppreciationRepository,
    private val familyGoalsRepository: FamilyGoalsRepository,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val penaltyRepository: PenaltyRepository,
    private val recipeRepository: RecipeRepository,
    private val weeklyMenuDao: com.familyhub.data.local.db.dao.WeeklyMenuDao,
    private val menuTemplateDao: com.familyhub.data.local.db.dao.MenuTemplateDao,
    private val taskHistoryDao: com.familyhub.data.local.db.dao.TaskHistoryDao,
    private val dailyAssignmentDao: com.familyhub.data.local.db.dao.DailyAssignmentDao
) {
    private val exportShareFileGuard = ExportShareFileGuard(
        cacheDir = context.cacheDir,
        backupsDir = File(context.filesDir, "backups")
    )

    companion object {
        private const val TASK_HISTORY_EXPORT_LIMIT = 1000
    }
    suspend fun exportData(format: ExportFormat, currentUserId: String): File {
        exportShareFileGuard.cleanupStaleExportCache()
        val exportData = collectAllData(currentUserId)
        val file = ExportTempFileFactory.createExportCacheFile(
            cacheDir = context.cacheDir,
            format = format,
            exportDate = exportData.exportDate
        )

        try {
            when (format) {
                ExportFormat.CSV -> exportToCsv(exportData, file)
                ExportFormat.JSON -> exportToJson(exportData, file)
            }
            return file
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }
    
    suspend fun getExportData(currentUserId: String): ExportData = collectAllData(currentUserId)

    private suspend fun collectAllData(currentUserId: String): ExportData {
        val members = memberRepository.getAllMembers().first()
        // КРИТИЧНО: Используем getAllTasksSync() для получения актуальных данных напрямую из БД
        // Это гарантирует, что мы получим все задачи, включая только что созданные,
        // даже если синхронизация запускается сразу после создания задачи
        val allTasks = taskRepository.getAllTasksSync()
        // Фильтруем приватные задачи для текущего пользователя
        val tasks = allTasks.filterByVisibility(currentUserId)
        val shoppingLists = shoppingRepository.observeLists().first()
        val shoppingCategories = shoppingLists.flatMap { list ->
            shoppingRepository.observeCategories(list.id).first()
        }
        val shoppingItems = shoppingLists.flatMap { list ->
            shoppingRepository.getItemsSnapshot(list.id)
        }
        val shoppingActivities = shoppingLists.flatMap { list ->
            shoppingRepository.getActivitySnapshot(list.id)
        }
        val events = eventRepository.getAllEvents().first()
        val completions = scoreRepository.getCompletionLedger().first()
        val achievements = scoreRepository.getAllAchievements().first()
        val rewards = rewardRepository.getAllRewards().first()
        val rewardRequests = rewardRepository.getAllRequests().first()
        val wallets = scoreRepository.getAllWallets().first()
        val penalties = penaltyRepository.getAllPenalties().first()
        val rotations = taskRotationRepository.getAllRotations().first()
        val houseRules = houseRulesRepository.getAllRules().first()
        val templates = taskTemplateRepository.getAllTemplates().first()
        val categories = taskCategoryRepository.getAllCategories().first()
        val appreciations = appreciationRepository.getAll().first()
        val goalDetails = familyGoalsRepository.getAllGoalDetails().first()
        val familyGoals = goalDetails.map { it.goal }
        val goalStages = goalDetails.flatMap { detail -> detail.stages }
            .sortedWith(compareBy(FamilyGoalStage::goalId, FamilyGoalStage::position))
        val goalSteps = goalStages.flatMap { stage ->
            stage.steps.sortedBy(FamilyGoalStep::position).map { Triple(stage.goalId, stage.position, it) }
        }.sortedWith(compareBy({ it.first }, { it.second }, { it.third.position })).map { it.third }
        val emergencyContacts = emergencyContactRepository.getAllContacts().first()
        val recipes = recipeRepository.observeRecipes().first()
        val menuTemplateSnapshots = menuTemplateDao.getAllWithEntries()
        val menuTemplates = menuTemplateSnapshots.map { snapshot ->
            ExportMenuTemplate(
                id = snapshot.template.id,
                name = snapshot.template.name,
                createdAt = snapshot.template.createdAt,
                updatedAt = snapshot.template.updatedAt
            )
        }
        val menuTemplateEntries = menuTemplateSnapshots.flatMap { snapshot ->
            snapshot.entries.map { entry ->
                ExportMenuTemplateEntry(
                    id = entry.id,
                    templateId = entry.templateId,
                    dayOfWeek = entry.dayOfWeek,
                    mealType = entry.mealType,
                    recipeId = entry.recipeId,
                    customDish = entry.customDish,
                    servings = entry.servings,
                    assignedMemberId = entry.assignedMemberId,
                    cookingTaskId = entry.cookingTaskId,
                    prepTaskId = entry.prepTaskId,
                    updatedAt = entry.updatedAt
                )
            }
        }
        val weeklyMenuSnapshots = weeklyMenuDao.getAllWithEntries()
        val weeklyMenus = weeklyMenuSnapshots.map { snapshot ->
            ExportWeeklyMenu(
                id = snapshot.menu.id,
                startDateMillis = snapshot.menu.startDateMillis,
                isAutoGenerated = snapshot.menu.isAutoGenerated,
                createdAt = snapshot.menu.createdAt,
                updatedAt = snapshot.menu.updatedAt
            )
        }
        val menuEntries = weeklyMenuSnapshots.flatMap { snapshot ->
            snapshot.entries.map { entry ->
                ExportMenuEntry(
                    id = entry.id,
                    menuId = entry.menuId,
                    dayOfWeek = entry.dayOfWeek,
                    mealType = entry.mealType,
                    recipeId = entry.recipeId,
                    customDish = entry.customDish,
                    servings = entry.servings,
                    assignedMemberId = entry.assignedMemberId,
                    cookingTaskId = entry.cookingTaskId,
                    prepTaskId = entry.prepTaskId,
                    updatedAt = entry.updatedAt
                )
            }
        }
        val taskHistory = taskHistoryDao.getRecentSnapshot(TASK_HISTORY_EXPORT_LIMIT).map { entry ->
            ExportTaskHistory(
                id = entry.id,
                taskId = entry.taskId,
                changedByUserId = entry.changedByUserId,
                changeType = entry.changeType,
                changeDescription = entry.changeDescription,
                oldValue = entry.oldValue,
                newValue = entry.newValue,
                timestamp = entry.timestamp
            )
        }
        val dailyAssignments = dailyAssignmentDao.getAllSnapshot().map { assignment ->
            ExportDailyAssignment(
                id = assignment.id,
                weekStartMillis = assignment.weekStartMillis,
                dayOfWeek = assignment.dayOfWeek,
                assignedMemberId = assignment.assignedMemberId,
                rewardPoints = assignment.rewardPoints,
                isRated = assignment.isRated,
                updatedAt = assignment.updatedAt
            )
        }

        val allScores = scoreRepository.getScoresFromWeek(0L).first()

        return ExportData(
            members = members.map {
                ExportMember(
                    id = it.id,
                    displayName = it.displayName,
                    roleLabel = it.roleLabel,
                    avatarColor = it.avatarColor,
                    role = it.role.name,
                    birthDate = it.birthDate,
                    age = it.age,
                    photoUrl = it.photoUrl
                )
            },
            tasks = tasks.map {
                ExportTask(
                    id = it.id,
                    title = it.title,
                    description = it.description,
                    createdByUserId = it.createdByUserId,
                    assignedToUserId = it.assignedToUserId,
                    dueDate = it.dueDate,
                    dueDateHasTime = it.dueDateHasTime,
                    reminderMode = it.reminderMode.name,
                    reminderMinutesOfDay = it.reminderMinutesOfDay,
                    repeatRule = it.repeatRule.name,
                    status = it.status.name,
                    createdAt = it.createdAt,
                    orderIndex = it.orderIndex,
                    taskType = it.taskType.name,
                    `private` = it.isPrivate,
                    priority = it.priority.name,
                    categoryId = it.categoryId,
                    rewardPoints = it.rewardPoints,
                    requiresApproval = it.requiresApproval,
                    attachments = it.attachments.map { attachment -> attachment.toExport() },
                    comments = it.comments.map { comment -> comment.toExport() },
                    completionCycle = it.completionCycle
                )
            },
            shoppingLists = shoppingLists.map {
                ExportShoppingList(
                    id = it.id,
                    title = it.title,
                    colorHex = it.colorHex,
                    iconName = it.iconName,
                    createdAt = it.createdAt,
                    archived = it.isArchived,
                    archivedAt = it.archivedAt,
                    templateKey = it.templateKey,
                    participantIds = it.participantIds
                )
            },
            shoppingCategories = shoppingCategories.map {
                ExportShoppingCategory(
                    id = it.id,
                    listId = it.listId,
                    title = it.title,
                    colorHex = it.colorHex,
                    iconName = it.iconName,
                    sortOrder = it.sortOrder
                )
            },
            shoppingItems = shoppingItems.map {
                ExportShoppingItem(
                    id = it.id,
                    listId = it.listId,
                    title = it.title,
                    createdByUserId = it.createdByUserId,
                    bought = it.isBought,
                    createdAt = it.createdAt,
                    categoryId = it.categoryId,
                    quantity = it.quantity,
                    quantityUnit = it.quantityUnit?.id,
                    note = it.note,
                    boughtAt = it.boughtAt
                )
            },
            shoppingActivities = shoppingActivities.map { it.toExport() },
            events = events.map {
                ExportEvent(
                    id = it.id,
                    title = it.title,
                    startDateTime = it.startDateTime,
                    createdByUserId = it.createdByUserId,
                    durationMinutes = it.durationMinutes,
                    recurrence = it.recurrence.name,
                    reminderMinutesBefore = it.reminderMinutesBefore
                )
            },
            scores = allScores.map {
                ExportScore(it.userId, it.weekStartDate, it.score, it.updatedAt)
            },
            taskCompletions = completions.map { it.toExport() },
            achievements = achievements.map { it.toExport() },
            rewards = rewards.map { it.toExport() },
            rewardRequests = rewardRequests.map { it.toExport() },
            wallets = wallets.map { it.toExport() },
            taskRotations = rotations.map {
                ExportTaskRotation(
                    taskId = it.taskId,
                    memberIds = it.memberIds,
                    currentIndex = it.currentIndex,
                    frequency = it.frequency.name,
                    startDateTime = it.startDateTime,
                    nextRotationAt = it.nextRotationAt,
                    lastRotationAt = it.lastRotationAt
                )
            },
            houseRules = houseRules.map { it.toExport() },
            taskTemplates = templates.map { it.toExport() },
            taskCategories = categories.map { it.toExport() },
            appreciations = appreciations.map { it.toExport() },
            familyGoals = familyGoals.map { it.toExport() },
            familyGoalStages = goalStages.map { it.toExport() },
            familyGoalSteps = goalSteps.map { it.toExport() },
            penalties = penalties.map { it.toExport() },
            emergencyContacts = emergencyContacts.map { it.toExport() },
            recipes = recipes.map { it.toExport() },
            menuTemplates = menuTemplates,
            menuTemplateEntries = menuTemplateEntries,
            weeklyMenus = weeklyMenus,
            menuEntries = menuEntries,
            taskHistory = taskHistory,
            dailyAssignments = dailyAssignments,
            exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
    }
    
    private fun exportToCsv(data: ExportData, file: File) {
        file.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            // Заголовок
            writer.append("=== FamilyHub Export ===\n")
            writer.append("Export Date: ${data.exportDate}\n\n")
            
            // Члены семьи
            writer.append("=== Members ===\n")
            writer.append("ID,Display Name,Role Label,Avatar Color,Role Type\n")
            data.members.forEach { member ->
                writer.append(
                    "${member.id},${member.displayName},${member.roleLabel},${member.avatarColor},${member.role}\n"
                )
            }
            writer.append("\n")
            
            // Задачи
            writer.append("=== Tasks ===\n")
            writer.append("ID,Title,Description,Created By,Assigned To,Due Date,Repeat Rule,Status,Created At,Order Index,Task Type,Is Private,Priority,Category ID,Attachments,Comments\n")
            data.tasks.forEach { task ->
                val dueDate = task.dueDate?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
                } ?: ""
                val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(task.createdAt))
                val attachmentsSummary = task.attachments.joinToString(" | ") { attachment ->
                    "${attachment.name.replace(",", ";")}: ${attachment.uri}"
                }
                val commentsSummary = task.comments.joinToString(" | ") { comment ->
                    "${comment.authorId}: ${comment.message.replace(",", ";")}".replace("\n", " ")
                }
                writer.append("${task.id},${task.title}," +
                    "${task.description?.replace(",", ";") ?: ""}," +
                    "${task.createdByUserId},${task.assignedToUserId}," +
                    "$dueDate,${task.repeatRule},${task.status}," +
                    "$createdAt,${task.orderIndex},${task.taskType},${task.private},${task.priority},${task.categoryId ?: ""},${attachmentsSummary},${commentsSummary}\n")
            }
            writer.append("\n")

            // Категории задач
            writer.append("=== Task Categories ===\n")
            writer.append("ID,Name,Color,Created At,Updated At\n")
            data.taskCategories.forEach { category ->
                val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(category.createdAt))
                val updatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(category.updatedAt))
                writer.append(
                    "${category.id},${category.name.replace(",", ";")},${category.color},$createdAt,$updatedAt\n"
                )
            }
            writer.append("\n")

            // Списки покупок
            writer.append("=== Shopping Lists ===\n")
            writer.append("ID,Title,Color,Icon,Created At\n")
            data.shoppingLists.forEach { list ->
                val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(list.createdAt))
                val safeTitle = list.title.replace(",", ";").replace("\n", " ")
                writer.append("${list.id},$safeTitle,${list.colorHex},${list.iconName},$createdAt\n")
            }
            writer.append("\n")

            // Категории покупок
            writer.append("=== Shopping Categories ===\n")
            writer.append("ID,List ID,Title,Color,Icon,Sort Order\n")
            data.shoppingCategories.forEach { category ->
                val safeTitle = category.title.replace(",", ";").replace("\n", " ")
                writer.append("${category.id},${category.listId},$safeTitle,${category.colorHex},${category.iconName},${category.sortOrder}\n")
            }
            writer.append("\n")

            // Покупки
            writer.append("=== Shopping Items ===\n")
            writer.append("ID,List ID,Title,Created By,Is Bought,Created At,Category ID,Quantity,Unit,Note,Bought At\n")
            data.shoppingItems.forEach { item ->
                val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.createdAt))
                val boughtAt = item.boughtAt?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
                } ?: ""
                val safeTitle = item.title.replace(",", ";").replace("\n", " ")
                val safeNote = item.note?.replace(",", ";")?.replace("\n", " ") ?: ""
                writer.append(
                    "${item.id},${item.listId},$safeTitle,${item.createdByUserId},${item.bought},$createdAt," +
                        "${item.categoryId ?: ""},${item.quantity ?: ""},${item.quantityUnit ?: ""},$safeNote,$boughtAt\n"
                )
            }
            writer.append("\n")

            writer.append("=== Shopping Activity ===\n")
            writer.append("ID,List ID,Item ID,Actor ID,Action Type,Occurred At,Payload\n")
            data.shoppingActivities.forEach { activity ->
                val occurredAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(activity.occurredAt))
                val payloadSummary = activity.payload.entries.joinToString(" | ") { (key, value) ->
                    "${key}:${value.replace(",", ";").replace("\n", " ")}" }
                writer.append(
                    "${activity.id},${activity.listId},${activity.itemId ?: ""},${activity.actorId},${activity.actionType},$occurredAt,$payloadSummary\n"
                )
            }
            writer.append("\n")

            // События
            writer.append("=== Events ===\n")
            writer.append("ID,Title,Start DateTime,Created By,Duration Minutes,Recurrence,Reminder Minutes\n")
            data.events.forEach { event ->
                val start = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(event.startDateTime))
                val reminder = event.reminderMinutesBefore?.toString() ?: ""
                writer.append(
                    "${event.id},${event.title},$start,${event.createdByUserId}," +
                        "${event.durationMinutes},${event.recurrence},$reminder\n"
                )
            }
            writer.append("\n")

            // Шаблоны задач
            writer.append("=== Task Templates ===\n")
            writer.append("ID,Title,Description,Repeat Rule,Default Assignee,Is Private,Task Type,Created By,Created At,Category ID,Priority\n")
            data.taskTemplates.forEach { template ->
                val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(template.createdAt))
                writer.append(
                    "${template.id},${template.title}," +
                        "${template.description?.replace(",", ";") ?: ""}," +
                        "${template.repeatRule},${template.defaultAssigneeId ?: ""}," +
                        "${template.private},${template.taskType},${template.createdByUserId},$createdAt,${template.categoryId ?: ""},${template.priority}\n"
                )
            }
            writer.append("\n")

            // Награды
            writer.append("=== Rewards ===\n")
            writer.append("ID,Title,Description,Cost,Created By,Is Active,Created At\n")
            data.rewards.forEach { reward ->
                val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(reward.createdAt))
                writer.append(
                    "${reward.id},${reward.title},${reward.description?.replace(",", ";") ?: ""}," +
                        "${reward.cost},${reward.createdByUserId},${reward.active},$createdAt\n"
                )
            }
            writer.append("\n")

            // Заявки на награды
            writer.append("=== Reward Requests ===\n")
            writer.append("ID,Reward ID,Reward Title,Cost,Requester,Note,Status,Requested At,Processed At,Processed By\n")
            data.rewardRequests.forEach { request ->
                val requestedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(request.requestedAt))
                val processedAt = request.processedAt?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
                } ?: ""
                writer.append(
                    "${request.id},${request.rewardId},${request.rewardTitle.replace(",", ";")}," +
                        "${request.rewardCost},${request.requesterUserId},${request.note?.replace(",", ";") ?: ""}," +
                        "${request.status},$requestedAt,$processedAt,${request.processedByUserId ?: ""}\n"
                )
            }
            writer.append("\n")

            // Благодарности
            writer.append("=== Appreciations ===\n")
            writer.append("ID,Sender ID,Recipient ID,Message,Created At\n")
            data.appreciations.forEach { appreciation ->
                val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(appreciation.createdAt))
                val message = appreciation.message
                    .replace(",", ";")
                    .replace("\n", " ")
                writer.append(
                    "${appreciation.id},${appreciation.senderId},${appreciation.recipientId},$message,$createdAt\n"
                )
            }
            writer.append("\n")

            // Цели семьи
            writer.append("=== Family Goals ===\n")
            writer.append("ID,Title,Description,Target Points,Current Points,Deadline,Status,Created By,Created At,Updated At,Is Personal\n")
            data.familyGoals.forEach { goal ->
                val deadline = goal.deadlineAt?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                } ?: ""
                val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(goal.createdAt))
                val updatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(goal.updatedAt))
                writer.append(
                    "${goal.id},${goal.title},${goal.description?.replace(",", ";") ?: ""}," +
                        "${goal.targetPoints},${goal.currentPoints},$deadline,${goal.status}," +
                        "${goal.createdByUserId},$createdAt,$updatedAt,${goal.personal}\n"
                )
            }
            writer.append("\n")

            // Этапы целей
            writer.append("=== Goal Stages ===\n")
            writer.append("ID,Goal ID,Title,Description,Position\n")
            data.familyGoalStages.forEach { stage ->
                val title = stage.title.replace(",", ";")
                val description = stage.description?.replace(",", ";") ?: ""
                writer.append("${stage.id},${stage.goalId},$title,$description,${stage.position}\n")
            }
            writer.append("\n")

            // Шаги целей
            writer.append("=== Goal Steps ===\n")
            writer.append("ID,Goal ID,Stage ID,Title,Is Completed,Position\n")
            data.familyGoalSteps.forEach { step ->
                val title = step.title.replace(",", ";")
                writer.append("${step.id},${step.goalId},${step.stageId},$title,${step.completed},${step.position}\n")
            }
            writer.append("\n")

            // Баллы
            writer.append("=== Scores ===\n")
            writer.append("User ID,Week Start Date,Score\n")
            data.scores.forEach { score ->
                val weekStart = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(score.weekStartDate))
                writer.append("${score.userId},$weekStart,${score.score}\n")
            }

            writer.append("\n")

            // Выполнения задач
            writer.append("=== Task Completions ===\n")
            writer.append("ID,Task ID,User ID,Task Type,Completion Time,Week Start,Base Points,Bonus Points,Penalty Points,Total Points\n")
            data.taskCompletions.forEach { completion ->
                val completionTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(completion.completionTime))
                val weekStart = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(completion.weekStartDate))
                writer.append(
                    "${completion.id},${completion.taskId},${completion.userId},${completion.taskType}," +
                        "$completionTime,$weekStart,${completion.basePoints},${completion.bonusPoints}," +
                        "${completion.penaltyPoints},${completion.totalPoints}\n"
                )
            }

            writer.append("\n")

            // Достижения
            writer.append("=== Achievements ===\n")
            writer.append("User ID,Type,Earned At\n")
            data.achievements.forEach { achievement ->
                val earnedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(achievement.earnedAt))
                writer.append("${achievement.userId},${achievement.type},$earnedAt\n")
            }

            writer.append("\n")

            // Балансы кошельков
            writer.append("=== Wallets ===\n")
            writer.append("User ID,Balance\n")
            data.wallets.forEach { wallet ->
                writer.append("${wallet.userId},${wallet.balance}\n")
            }

            writer.append("\n")

            // Ротации задач
            writer.append("=== Task Rotations ===\n")
            writer.append("Task ID,Member IDs,Current Index,Frequency,Start Date,Next Rotation,Last Rotation\n")
            data.taskRotations.forEach { rotation ->
                val start = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(rotation.startDateTime))
                val next = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(rotation.nextRotationAt))
                val last = rotation.lastRotationAt?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
                } ?: ""
                writer.append(
                    "${rotation.taskId},${rotation.memberIds.joinToString("|")},${rotation.currentIndex},${rotation.frequency}," +
                        "$start,$next,$last\n"
                )
            }

            writer.append("\n")

            // Правила дома
            writer.append("=== House Rules ===\n")
            writer.append("ID,Title,Description,Created By,Created At,Updated At\n")
            data.houseRules.forEach { rule ->
                val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(rule.createdAt))
                val updatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(rule.updatedAt))
                writer.append(
                    "${rule.id},${rule.title.replace(",", ";")},${rule.description?.replace(",", ";") ?: ""}," +
                        "${rule.createdByUserId},$createdAt,$updatedAt\n"
                )
            }
        }
    }
    
    private fun exportToJson(data: ExportData, file: File) {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        val jsonString = json.encodeToString(data)
        file.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write(jsonString)
        }
    }

    private fun TaskCompletion.toExport(): ExportTaskCompletion {
        return ExportTaskCompletion(
            id = id,
            taskId = taskId,
            userId = userId,
            taskType = taskType.name,
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
    }

    private fun UserAchievement.toExport(): ExportAchievement {
        return ExportAchievement(
            userId = userId,
            type = type.name,
            earnedAt = earnedAt
        )
    }

    private fun ShoppingActivity.toExport(): ExportShoppingActivity {
        return ExportShoppingActivity(
            id = id,
            listId = listId,
            itemId = itemId,
            actorId = actorId,
            actionType = actionType.id,
            payload = payload,
            occurredAt = occurredAt
        )
    }

    private fun Reward.toExport(): ExportReward {
        return ExportReward(
            id = id,
            title = title,
            description = description,
            cost = cost,
            createdByUserId = createdByUserId,
            active = isActive,
            createdAt = createdAt
        )
    }

    private fun RewardRequest.toExport(): ExportRewardRequest {
        return ExportRewardRequest(
            id = id,
            rewardId = rewardId,
            rewardTitle = rewardTitle,
            rewardCost = rewardCost,
            requesterUserId = requesterUserId,
            note = note,
            status = status.name,
            requestedAt = requestedAt,
            processedAt = processedAt,
            processedByUserId = processedByUserId
        )
    }

    private fun UserWallet.toExport(): ExportWallet {
        return ExportWallet(
            userId = userId,
            balance = balance,
            updatedAt = updatedAt
        )
    }

    private fun HouseRule.toExport(): ExportHouseRule {
        return ExportHouseRule(
            id = id,
            title = title,
            description = description,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun TaskTemplate.toExport(): ExportTaskTemplate {
        return ExportTaskTemplate(
            id = id,
            title = title,
            description = description,
            repeatRule = repeatRule.name,
            defaultAssigneeId = defaultAssigneeId,
            `private` = isPrivate,
            taskType = taskType.name,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            categoryId = categoryId,
            priority = priority.name
        )
    }

    private fun TaskCategory.toExport(): ExportTaskCategory {
        return ExportTaskCategory(
            id = id,
            name = name,
            color = color,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun TaskAttachment.toExport(): ExportTaskAttachment {
        return ExportTaskAttachment(
            id = id,
            name = name,
            uri = uri,
            mimeType = mimeType,
            addedAt = addedAt
        )
    }

    private fun TaskComment.toExport(): ExportTaskComment {
        return ExportTaskComment(
            id = id,
            authorId = authorId,
            message = message,
            createdAt = createdAt
        )
    }

    private fun Appreciation.toExport(): ExportAppreciation {
        return ExportAppreciation(
            id = id,
            senderId = senderId,
            recipientId = recipientId,
            message = message,
            createdAt = createdAt
        )
    }


    private fun FamilyGoalStage.toExport(): ExportFamilyGoalStage {
        return ExportFamilyGoalStage(
            id = id,
            goalId = goalId,
            title = title,
            description = description,
            position = position
        )
    }

    private fun FamilyGoalStep.toExport(): ExportFamilyGoalStep {
        return ExportFamilyGoalStep(
            id = id,
            goalId = goalId,
            stageId = stageId,
            title = title,
            completed = isCompleted,
            position = position
        )
    }

    private fun FamilyGoal.toExport(): ExportFamilyGoal {
        return ExportFamilyGoal(
            id = id,
            title = title,
            description = description,
            targetPoints = targetPoints,
            currentPoints = currentPoints,
            deadlineAt = deadlineAt,
            status = status.name,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            personal = isPersonal
        )
    }

    private fun com.familyhub.domain.model.EmergencyContact.toExport(): ExportEmergencyContact {
        return ExportEmergencyContact(
            id = id,
            name = name,
            phoneNumber = phoneNumber,
            description = description,
            createdAt = createdAt
        )
    }

    fun shareFile(file: File): Intent {
        val validated = exportShareFileGuard.validateShareable(file)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            validated.file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = validated.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, context.getString(R.string.export_share_title))
    }

    private fun com.familyhub.domain.model.Recipe.toExport(): ExportRecipe {
        return ExportRecipe(
            id = id,
            title = title,
            description = description,
            servings = servings,
            prepTimeMinutes = prepTimeMinutes,
            cookTimeMinutes = 0, // Not present in domain model, default to 0
            difficulty = difficulty.name,
            category = category.name,
            isFavorite = isFavorite,
            imageUrl = imageUrl,
            notes = notes,
            tags = tags,
            createdByUserId = "", // Not present in domain model, leave empty or TODO
            createdAt = createdAt,
            updatedAt = updatedAt,
            ingredients = ingredients.map {
                ExportRecipeIngredient(
                    id = it.id,
                    name = it.name,
                    quantity = it.quantity
                )
            },
            steps = steps.map {
                ExportRecipeStep(
                    id = it.id,
                    description = it.description,
                    order = it.order
                )
            }
        )
    }
}

@Serializable
data class ExportRecipe(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val servings: Int = 0,
    val prepTimeMinutes: Int = 0,
    val cookTimeMinutes: Int = 0,
    val difficulty: String = "MEDIUM",
    val category: String = "MAIN_DISH",
    val isFavorite: Boolean = false,
    val imageUrl: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val createdByUserId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val ingredients: List<ExportRecipeIngredient> = emptyList(),
    val steps: List<ExportRecipeStep> = emptyList()
)

@Serializable
data class ExportRecipeIngredient(
    val id: String = "",
    val name: String = "",
    val quantity: String = ""
)

@Serializable
data class ExportRecipeStep(
    val id: String = "",
    val description: String = "",
    val order: Int = 0
)
