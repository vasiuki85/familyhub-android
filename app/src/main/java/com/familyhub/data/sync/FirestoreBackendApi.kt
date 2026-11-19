package com.familyhub.data.sync

import androidx.annotation.VisibleForTesting
import com.familyhub.data.export.ExportAchievement
import com.familyhub.data.export.ExportAppreciation
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportEvent
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportHouseRule
import com.familyhub.data.export.ExportEmergencyContact
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportRecipe
import com.familyhub.data.export.ExportReward
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportShoppingActivity
import com.familyhub.data.export.ExportShoppingCategory
import com.familyhub.data.export.ExportShoppingItem
import com.familyhub.data.export.ExportShoppingList
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskCategory
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportTaskRotation
import com.familyhub.data.export.ExportTaskTemplate
import com.familyhub.data.export.ExportWallet
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.export.toExportRecipe
import com.familyhub.domain.model.MemberRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

internal const val MAX_TRANSACTION_WRITE_OPERATIONS = 500

@VisibleForTesting
internal data class CollectionWriteDiff<T>(
    val deleteIds: List<String>,
    val sets: List<Pair<String, T>>
)

@VisibleForTesting
internal sealed class PlannedUploadOperation {
    data class Delete(val collection: String, val documentId: String) : PlannedUploadOperation()
    data class Set(
        val collection: String,
        val documentId: String,
        val data: Any,
        val merge: Boolean = false
    ) : PlannedUploadOperation()
}

@VisibleForTesting
internal fun buildPlannedUploadOperations(
    collectionNames: List<String>,
    collectionDiffs: List<CollectionWriteDiff<*>>,
    selfMember: ExportMember?,
    membersCollectionName: String
): List<PlannedUploadOperation> {
    return buildList {
        collectionNames.zip(collectionDiffs).forEach { (collectionName, diff) ->
            diff.deleteIds.forEach { id ->
                add(PlannedUploadOperation.Delete(collectionName, id))
            }
            diff.sets.forEach { (id, item) ->
                add(PlannedUploadOperation.Set(collectionName, id, item as Any))
            }
        }
        if (selfMember != null) {
            add(
                PlannedUploadOperation.Set(
                    collection = membersCollectionName,
                    documentId = selfMember.id,
                    data = selfMember,
                    merge = true
                )
            )
        }
    }
}

@VisibleForTesting
internal fun countUploadWriteOperations(
    plannedOperations: List<PlannedUploadOperation>,
    includesFamilyUpdatedAt: Boolean
): Int {
    return plannedOperations.size + if (includesFamilyUpdatedAt) 1 else 0
}

@VisibleForTesting
internal fun <T> computeCollectionWriteDiff(
    previousItems: List<T>,
    targetItems: List<T>,
    idSelector: (T) -> String
): CollectionWriteDiff<T> {
    val previousById = previousItems.associateBy(idSelector)
    val targetById = targetItems.associateBy(idSelector)

    val deleteIds = (previousById.keys - targetById.keys).sorted()
    val sets = targetById.keys.sorted().mapNotNull { id ->
        val target = targetById.getValue(id)
        val previous = previousById[id]
        if (previous == null || previous != target) {
            id to target
        } else {
            null
        }
    }
    return CollectionWriteDiff(deleteIds = deleteIds, sets = sets)
}

@VisibleForTesting
internal fun resolveWriterRole(writerRole: MemberRole?, isMemberWriteAllowed: Boolean): MemberRole {
    return writerRole ?: if (isMemberWriteAllowed) MemberRole.ADMIN else MemberRole.PARENT
}

@VisibleForTesting
internal fun diffToPlannedOperations(
    collectionName: String,
    diff: CollectionWriteDiff<*>
): List<PlannedUploadOperation> {
    return buildList {
        diff.deleteIds.forEach { id ->
            add(PlannedUploadOperation.Delete(collectionName, id))
        }
        diff.sets.forEach { (id, item) ->
            add(PlannedUploadOperation.Set(collectionName, id, item as Any))
        }
    }
}

internal class SelfMemberRoleMismatchException(
    message: String,
) : IllegalStateException(message)

internal class SelfMemberMissingInBaseException(
    message: String,
) : IllegalStateException(message)

@VisibleForTesting
internal fun validateNonAdminSelfMemberWrite(
    baseSnapshot: ExportData?,
    selfMember: ExportMember,
) {
    if (baseSnapshot == null) return
    val baseSelfMember = baseSnapshot.members.find { it.id == selfMember.id }
        ?: throw SelfMemberMissingInBaseException(
            "Self member ${selfMember.id} missing from base snapshot for upload"
        )
    if (baseSelfMember.role != selfMember.role) {
        throw SelfMemberRoleMismatchException(
            "Self member role mismatch for upload: base=${baseSelfMember.role}, candidate=${selfMember.role}"
        )
    }
}

private fun PlannedUploadOperation.collectionName(): String = when (this) {
    is PlannedUploadOperation.Delete -> collection
    is PlannedUploadOperation.Set -> collection
}

private fun PlannedUploadOperation.documentId(): String = when (this) {
    is PlannedUploadOperation.Delete -> documentId
    is PlannedUploadOperation.Set -> documentId
}

private fun childTaskOperation(
    operation: PlannedUploadOperation,
    base: ExportData,
    userId: String,
): PlannedUploadOperation? {
    val baseTask = base.tasks.find { it.id == operation.documentId() }
    return when (operation) {
        is PlannedUploadOperation.Delete -> {
            operation.takeIf { baseTask?.createdByUserId == userId }
        }

        is PlannedUploadOperation.Set -> {
            val targetTask = operation.data as? ExportTask ?: return null
            if (baseTask == null) {
                return operation.takeIf {
                    targetTask.createdByUserId == userId &&
                        targetTask.assignedToUserId == userId &&
                        targetTask.status == "TODO" &&
                        targetTask.rewardPoints == null &&
                        targetTask.completionCycle == 0L
                }
            }

            if (
                baseTask.assignedToUserId == userId &&
                targetTask.status == "WAITING_FOR_APPROVAL" &&
                targetTask.requiresApproval
            ) {
                return PlannedUploadOperation.Set(
                    collection = TASKS_COLLECTION,
                    documentId = targetTask.id,
                    data = mapOf(
                        "status" to targetTask.status,
                        "requiresApproval" to true,
                    ),
                    merge = true,
                )
            }

            if (
                baseTask.assignedToUserId == userId &&
                baseTask.status == "WAITING_FOR_APPROVAL" &&
                baseTask.requiresApproval &&
                targetTask.status == "TODO" &&
                targetTask.requiresApproval == baseTask.requiresApproval
            ) {
                return PlannedUploadOperation.Set(
                    collection = TASKS_COLLECTION,
                    documentId = targetTask.id,
                    data = mapOf("status" to targetTask.status),
                    merge = true,
                )
            }

            val creatorControlledFieldsUnchanged =
                baseTask.createdByUserId == userId &&
                    targetTask.createdByUserId == baseTask.createdByUserId &&
                    targetTask.assignedToUserId == baseTask.assignedToUserId &&
                    targetTask.rewardPoints == baseTask.rewardPoints &&
                    targetTask.`private` == baseTask.`private` &&
                    targetTask.status == baseTask.status &&
                    targetTask.requiresApproval == baseTask.requiresApproval &&
                    targetTask.completionCycle == baseTask.completionCycle
            operation.takeIf { creatorControlledFieldsUnchanged }
        }
    }
}

private fun childRewardRequestOperation(
    operation: PlannedUploadOperation,
    base: ExportData,
    userId: String?,
): PlannedUploadOperation? {
    if (operation !is PlannedUploadOperation.Set) return null
    if (base.rewardRequests.any { it.id == operation.documentId }) return null
    val request = operation.data as? ExportRewardRequest ?: return null
    return operation.takeIf {
        (userId == null || request.requesterUserId == userId) &&
            request.status == "PENDING" &&
            request.processedAt == null &&
            request.processedByUserId == null
    }
}

private fun childFamilyGoalOperation(
    operation: PlannedUploadOperation,
    base: ExportData,
    userId: String,
): PlannedUploadOperation? {
    val baseGoal = base.familyGoals.find { it.id == operation.documentId() }
    return when (operation) {
        is PlannedUploadOperation.Delete -> operation.takeIf {
            baseGoal?.createdByUserId == userId
        }
        is PlannedUploadOperation.Set -> {
            val goal = operation.data as? ExportFamilyGoal ?: return null
            operation.takeIf {
                if (baseGoal == null) {
                    goal.createdByUserId == userId
                } else {
                    baseGoal.createdByUserId == userId &&
                        goal.createdByUserId == baseGoal.createdByUserId
                }
            }
        }
    }
}

private fun childCanWriteGoalChildren(
    operation: PlannedUploadOperation,
    base: ExportData,
    target: ExportData,
    userId: String,
): Boolean {
    val goalId = when (operation) {
        is PlannedUploadOperation.Delete -> when (operation.collection) {
            FAMILY_GOAL_STAGES_COLLECTION -> base.familyGoalStages.find { it.id == operation.documentId }?.goalId
            FAMILY_GOAL_STEPS_COLLECTION -> base.familyGoalSteps.find { it.id == operation.documentId }?.goalId
            else -> null
        }
        is PlannedUploadOperation.Set -> when (val data = operation.data) {
            is ExportFamilyGoalStage -> data.goalId
            is ExportFamilyGoalStep -> data.goalId
            else -> null
        }
    } ?: return false
    val goal = target.familyGoals.find { it.id == goalId }
        ?: base.familyGoals.find { it.id == goalId }
        ?: return false
    return !goal.personal || goal.createdByUserId == userId
}

private fun filterChildUploadPlan(
    operations: List<PlannedUploadOperation>,
    base: ExportData,
    target: ExportData,
    userId: String?,
): List<PlannedUploadOperation> {
    return operations.mapNotNull { operation ->
        when (operation.collectionName()) {
            MEMBERS_COLLECTION -> operation.takeIf {
                it is PlannedUploadOperation.Set &&
                    userId != null &&
                    it.documentId == userId &&
                    it.merge
            }
            TASKS_COLLECTION -> userId?.let { childTaskOperation(operation, base, it) }
            SHOPPING_LISTS_COLLECTION,
            SHOPPING_CATEGORIES_COLLECTION,
            SHOPPING_ITEMS_COLLECTION,
            SHOPPING_ACTIVITY_COLLECTION,
            EVENTS_COLLECTION,
            APPRECIATIONS_COLLECTION,
            RECIPES_COLLECTION -> operation
            REWARD_REQUESTS_COLLECTION -> childRewardRequestOperation(operation, base, userId)
            FAMILY_GOALS_COLLECTION -> userId?.let { childFamilyGoalOperation(operation, base, it) }
            FAMILY_GOAL_STAGES_COLLECTION,
            FAMILY_GOAL_STEPS_COLLECTION -> operation.takeIf {
                userId != null && childCanWriteGoalChildren(operation, base, target, userId)
            }
            else -> null
        }
    }
}

private fun filterUploadPlanForRole(
    operations: List<PlannedUploadOperation>,
    role: MemberRole,
    base: ExportData,
    target: ExportData,
    selfMember: ExportMember?,
): List<PlannedUploadOperation> = when (role) {
    MemberRole.ADMIN,
    MemberRole.PARENT -> operations
    MemberRole.CHILD -> filterChildUploadPlan(
        operations = operations,
        base = base,
        target = target,
        userId = selfMember?.id,
    )
    MemberRole.PENDING -> operations.filter { operation ->
        operation is PlannedUploadOperation.Set &&
            operation.collection == MEMBERS_COLLECTION &&
            operation.documentId == selfMember?.id &&
            operation.merge
    }
}

@VisibleForTesting
internal fun buildUploadPlan(
    baseSnapshot: ExportData?,
    targetSnapshot: ExportData,
    isMemberWriteAllowed: Boolean,
    selfMember: ExportMember?,
    writerRole: MemberRole? = null,
): List<PlannedUploadOperation> {
    val base = baseSnapshot ?: ExportData()
    fun <T> previousItems(items: List<T>): List<T> = if (baseSnapshot == null) emptyList() else items

    val unrestrictedPlan = buildList {
        if (isMemberWriteAllowed) {
            addAll(
                diffToPlannedOperations(
                    MEMBERS_COLLECTION,
                    computeCollectionWriteDiff(
                        previousItems(base.members),
                        targetSnapshot.members,
                        ExportMember::id
                    )
                )
            )
        }
        addAll(
            diffToPlannedOperations(
                TASKS_COLLECTION,
                computeCollectionWriteDiff(previousItems(base.tasks), targetSnapshot.tasks, ExportTask::id)
            )
        )
        addAll(
            diffToPlannedOperations(
                SHOPPING_LISTS_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.shoppingLists),
                    targetSnapshot.shoppingLists,
                    ExportShoppingList::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                SHOPPING_CATEGORIES_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.shoppingCategories),
                    targetSnapshot.shoppingCategories,
                    ExportShoppingCategory::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                SHOPPING_ITEMS_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.shoppingItems),
                    targetSnapshot.shoppingItems,
                    ExportShoppingItem::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                SHOPPING_ACTIVITY_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.shoppingActivities),
                    targetSnapshot.shoppingActivities,
                    ExportShoppingActivity::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                EVENTS_COLLECTION,
                computeCollectionWriteDiff(previousItems(base.events), targetSnapshot.events, ExportEvent::id)
            )
        )
        addAll(
            diffToPlannedOperations(
                SCORES_COLLECTION,
                computeCollectionWriteDiff(previousItems(base.scores), targetSnapshot.scores) {
                    "${it.userId}_${it.weekStartDate}"
                }
            )
        )
        addAll(
            diffToPlannedOperations(
                TASK_COMPLETIONS_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.taskCompletions),
                    targetSnapshot.taskCompletions,
                    ExportTaskCompletion::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                ACHIEVEMENTS_COLLECTION,
                computeCollectionWriteDiff(previousItems(base.achievements), targetSnapshot.achievements) {
                    achievementLogicalId(it.userId, it.type)
                }
            )
        )
        addAll(
            diffToPlannedOperations(
                WALLETS_COLLECTION,
                computeCollectionWriteDiff(previousItems(base.wallets), targetSnapshot.wallets, ExportWallet::userId)
            )
        )
        addAll(
            diffToPlannedOperations(
                PENALTIES_COLLECTION,
                computeCollectionWriteDiff(previousItems(base.penalties), targetSnapshot.penalties, ExportPenalty::id)
            )
        )
        addAll(
            diffToPlannedOperations(
                REWARDS_COLLECTION,
                computeCollectionWriteDiff(previousItems(base.rewards), targetSnapshot.rewards, ExportReward::id)
            )
        )
        addAll(
            diffToPlannedOperations(
                REWARD_REQUESTS_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.rewardRequests),
                    targetSnapshot.rewardRequests,
                    ExportRewardRequest::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                TASK_ROTATIONS_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.taskRotations),
                    targetSnapshot.taskRotations,
                    ExportTaskRotation::taskId
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                HOUSE_RULES_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.houseRules),
                    targetSnapshot.houseRules,
                    ExportHouseRule::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                TASK_TEMPLATES_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.taskTemplates),
                    targetSnapshot.taskTemplates,
                    ExportTaskTemplate::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                TASK_CATEGORIES_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.taskCategories),
                    targetSnapshot.taskCategories,
                    ExportTaskCategory::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                APPRECIATIONS_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.appreciations),
                    targetSnapshot.appreciations,
                    ExportAppreciation::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                FAMILY_GOALS_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.familyGoals),
                    targetSnapshot.familyGoals,
                    ExportFamilyGoal::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                FAMILY_GOAL_STAGES_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.familyGoalStages),
                    targetSnapshot.familyGoalStages,
                    ExportFamilyGoalStage::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                FAMILY_GOAL_STEPS_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.familyGoalSteps),
                    targetSnapshot.familyGoalSteps,
                    ExportFamilyGoalStep::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                EMERGENCY_CONTACTS_COLLECTION,
                computeCollectionWriteDiff(
                    previousItems(base.emergencyContacts),
                    targetSnapshot.emergencyContacts,
                    ExportEmergencyContact::id
                )
            )
        )
        addAll(
            diffToPlannedOperations(
                RECIPES_COLLECTION,
                computeCollectionWriteDiff(previousItems(base.recipes), targetSnapshot.recipes, ExportRecipe::id)
            )
        )

        if (!isMemberWriteAllowed && selfMember != null) {
            validateNonAdminSelfMemberWrite(baseSnapshot, selfMember)
            val baseSelfMember = base.members.find { it.id == selfMember.id }
            if (baseSelfMember != selfMember) {
                add(
                    PlannedUploadOperation.Set(
                        collection = MEMBERS_COLLECTION,
                        documentId = selfMember.id,
                        data = selfMember,
                        merge = true
                    )
                )
            }
        }
    }
    return filterUploadPlanForRole(
        operations = unrestrictedPlan,
        role = resolveWriterRole(writerRole, isMemberWriteAllowed),
        base = base,
        target = targetSnapshot,
        selfMember = selfMember,
    )
}

internal fun summarizePlannedUploadOperationsByCollection(
    operations: List<PlannedUploadOperation>
): Map<String, Int> = operations.groupingBy {
        when (it) {
            is PlannedUploadOperation.Delete -> it.collection
            is PlannedUploadOperation.Set -> it.collection
        }
    }.eachCount()

private const val FAMILIES_COLLECTION = "families"
private const val MEMBERS_COLLECTION = "members"
private const val TASKS_COLLECTION = "tasks"
private const val SHOPPING_ITEMS_COLLECTION = "shopping_items"
private const val SHOPPING_LISTS_COLLECTION = "shopping_lists"
private const val SHOPPING_CATEGORIES_COLLECTION = "shopping_categories"
private const val SHOPPING_ACTIVITY_COLLECTION = "shopping_activity"
private const val EVENTS_COLLECTION = "events"
private const val SCORES_COLLECTION = "scores"
private const val TASK_COMPLETIONS_COLLECTION = "task_completions"
private const val ACHIEVEMENTS_COLLECTION = "achievements"
private const val WALLETS_COLLECTION = "wallets"
private const val REWARDS_COLLECTION = "rewards"
private const val REWARD_REQUESTS_COLLECTION = "reward_requests"
private const val TASK_ROTATIONS_COLLECTION = "task_rotations"
private const val HOUSE_RULES_COLLECTION = "house_rules"
private const val TASK_TEMPLATES_COLLECTION = "task_templates"
private const val TASK_CATEGORIES_COLLECTION = "task_categories"
private const val APPRECIATIONS_COLLECTION = "appreciations"
private const val FAMILY_GOALS_COLLECTION = "family_goals"
private const val FAMILY_GOAL_STAGES_COLLECTION = "family_goal_stages"
private const val FAMILY_GOAL_STEPS_COLLECTION = "family_goal_steps"
private const val EMERGENCY_CONTACTS_COLLECTION = "emergency_contacts"
private const val PENALTIES_COLLECTION = "penalties"
private const val RECIPES_COLLECTION = "recipes"

@VisibleForTesting
internal fun isUploadWithinTransactionLimit(operationCount: Int): Boolean {
    return operationCount <= MAX_TRANSACTION_WRITE_OPERATIONS
}

@VisibleForTesting
internal fun requireUploadWithinTransactionLimit(operationCount: Int) {
    if (!isUploadWithinTransactionLimit(operationCount)) {
        throw FirebaseFirestoreException(
            "Snapshot exceeds Firestore transaction limit: $operationCount operations",
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED
        )
    }
}

@VisibleForTesting
internal suspend fun downloadVisibleTasks(
    familyDocRef: com.google.firebase.firestore.DocumentReference,
    source: Source,
    userId: String
): List<ExportTask> = coroutineScope {
    val publicTasks = async {
        familyDocRef.collection(TASKS_COLLECTION)
            .whereEqualTo("private", false)
            .get(source)
            .await()
            .toObjects<ExportTask>()
    }
    val createdPrivateTasks = async {
        familyDocRef.collection(TASKS_COLLECTION)
            .whereEqualTo("private", true)
            .whereEqualTo("createdByUserId", userId)
            .get(source)
            .await()
            .toObjects<ExportTask>()
    }
    val assignedPrivateTasks = async {
        familyDocRef.collection(TASKS_COLLECTION)
            .whereEqualTo("private", true)
            .whereEqualTo("assignedToUserId", userId)
            .get(source)
            .await()
            .toObjects<ExportTask>()
    }
    (publicTasks.await() + createdPrivateTasks.await() + assignedPrivateTasks.await())
        .distinctBy(ExportTask::id)
}

@VisibleForTesting
internal suspend fun downloadVisibleFamilyGoals(
    familyDocRef: com.google.firebase.firestore.DocumentReference,
    source: Source,
    userId: String
): List<ExportFamilyGoal> = coroutineScope {
    val sharedGoals = async {
        familyDocRef.collection(FAMILY_GOALS_COLLECTION)
            .whereEqualTo("personal", false)
            .get(source)
            .await()
            .toObjects<ExportFamilyGoal>()
    }
    val personalGoals = async {
        familyDocRef.collection(FAMILY_GOALS_COLLECTION)
            .whereEqualTo("personal", true)
            .whereEqualTo("createdByUserId", userId)
            .get(source)
            .await()
            .toObjects<ExportFamilyGoal>()
    }
    (sharedGoals.await() + personalGoals.await()).distinctBy(ExportFamilyGoal::id)
}

@VisibleForTesting
internal suspend fun downloadVisibleFamilyGoalStages(
    familyDocRef: com.google.firebase.firestore.DocumentReference,
    source: Source,
    visibleGoalIds: Set<String>
): List<ExportFamilyGoalStage> = coroutineScope {
    if (visibleGoalIds.isEmpty()) return@coroutineScope emptyList()
    visibleGoalIds.map { goalId ->
        async {
            familyDocRef.collection(FAMILY_GOAL_STAGES_COLLECTION)
                .whereEqualTo("goalId", goalId)
                .get(source)
                .await()
                .toObjects<ExportFamilyGoalStage>()
        }
    }
        .map { it.await() }
        .flatten()
        .distinctBy(ExportFamilyGoalStage::id)
}

@VisibleForTesting
internal suspend fun downloadVisibleFamilyGoalSteps(
    familyDocRef: com.google.firebase.firestore.DocumentReference,
    source: Source,
    visibleGoalIds: Set<String>
): List<ExportFamilyGoalStep> = coroutineScope {
    if (visibleGoalIds.isEmpty()) return@coroutineScope emptyList()
    visibleGoalIds.map { goalId ->
        async {
            familyDocRef.collection(FAMILY_GOAL_STEPS_COLLECTION)
                .whereEqualTo("goalId", goalId)
                .get(source)
                .await()
                .toObjects<ExportFamilyGoalStep>()
        }
    }
        .map { it.await() }
        .flatten()
        .distinctBy(ExportFamilyGoalStep::id)
}

@VisibleForTesting
internal fun isSnapshotVersionCompatible(
    snapshotVersion: Long,
    snapshotVersionNanos: Int,
    familyExists: Boolean,
    updatedAt: Timestamp?
): Boolean {
    if (snapshotVersion < 0L) return false
    if (snapshotVersion == 0L) {
        return !familyExists || updatedAt == null
    }
    if (!familyExists || updatedAt == null) return false
    return updatedAt.seconds == snapshotVersion && updatedAt.nanoseconds == snapshotVersionNanos
}

@VisibleForTesting
internal fun validateSnapshotVersionForUpload(
    snapshotVersion: Long,
    snapshotVersionNanos: Int,
    familyExists: Boolean,
    updatedAt: Timestamp?
) {
    if (!isSnapshotVersionCompatible(snapshotVersion, snapshotVersionNanos, familyExists, updatedAt)) {
        throw FirebaseFirestoreException(
            "Snapshot version mismatch",
            FirebaseFirestoreException.Code.ABORTED
        )
    }
}

@Singleton
class FirestoreBackendApi @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CloudBackendApi {

    override suspend fun uploadSnapshot(
        accountId: String,
        snapshot: ExportData,
        isMemberWriteAllowed: Boolean,
        selfMember: ExportMember?,
        baseSnapshot: ExportData?,
        menuUploadContext: com.familyhub.data.menu.cloud.MenuCloudUploadContext?,
        writerRole: MemberRole?,
    ) {
        runCatching { firestore.enableNetwork().await() }

        if (auth.currentUser == null) {
            throw FirebaseFirestoreException(
                "User not authenticated",
                FirebaseFirestoreException.Code.UNAUTHENTICATED
            )
        }

        Timber.d(
            "uploadSnapshot: building delta write plan. isMemberWriteAllowed=%s, hasBase=%s",
            isMemberWriteAllowed,
            baseSnapshot != null
        )

        val familyDocRef = firestore.collection(FAMILIES_COLLECTION).document(accountId)

        val nonMenuOperations = buildUploadPlan(
            baseSnapshot = baseSnapshot,
            targetSnapshot = snapshot,
            isMemberWriteAllowed = isMemberWriteAllowed,
            selfMember = selfMember,
            writerRole = writerRole
        )
        val menuOperations = menuUploadContext?.operations?.let(::menuWriteOperationsToPlanned).orEmpty()
        val plannedOperations = mergeUploadPlans(nonMenuOperations, menuOperations)

        if (plannedOperations.isEmpty()) {
            Timber.d("uploadSnapshot: no changes detected, skipping transaction")
            return
        }

        val operationCount = countUploadWriteOperations(
            plannedOperations = plannedOperations,
            includesFamilyUpdatedAt = true
        )
        requireUploadWithinTransactionLimit(operationCount)

        Timber.d(
            "uploadSnapshot: executing atomic transaction with %d operations (version=%d/%d)",
            operationCount,
            snapshot.version,
            snapshot.versionNanos
        )

        firestore.runTransaction { transaction ->
            val familySnapshot = transaction.get(familyDocRef)
            validateSnapshotVersionForUpload(
                snapshotVersion = snapshot.version,
                snapshotVersionNanos = snapshot.versionNanos,
                familyExists = familySnapshot.exists(),
                updatedAt = familySnapshot.getTimestamp("updatedAt")
            )

            plannedOperations.forEach { operation ->
                when (operation) {
                    is PlannedUploadOperation.Delete -> {
                        val docRef = familyDocRef.collection(operation.collection).document(operation.documentId)
                        transaction.delete(docRef)
                    }
                    is PlannedUploadOperation.Set -> {
                        val docRef = familyDocRef.collection(operation.collection).document(operation.documentId)
                        if (operation.merge) {
                            transaction.set(docRef, operation.data, SetOptions.merge())
                        } else {
                            transaction.set(docRef, operation.data)
                        }
                    }
                }
            }

            transaction.set(
                familyDocRef,
                mapOf("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            null
        }.await()

        Timber.d("uploadSnapshot: transaction committed successfully")
    }

    override suspend fun upsertSelfMember(accountId: String, member: ExportMember) {
        runCatching { firestore.enableNetwork().await() }

        if (auth.currentUser == null) {
            throw FirebaseFirestoreException(
                "User not authenticated",
                FirebaseFirestoreException.Code.UNAUTHENTICATED
            )
        }

        Timber.d("upsertSelfMember: syncing self member")

        firestore.collection(FAMILIES_COLLECTION)
            .document(accountId)
            .collection(MEMBERS_COLLECTION)
            .document(member.id)
            .set(member, SetOptions.merge())
            .await()
        Timber.d("upsertSelfMember: completed")
    }

    override suspend fun deleteMember(accountId: String, memberId: String) {
        runCatching { firestore.enableNetwork().await() }

        if (auth.currentUser == null) {
            throw FirebaseFirestoreException(
                "User not authenticated",
                FirebaseFirestoreException.Code.UNAUTHENTICATED
            )
        }

        Timber.d("deleteMember: deleting remote member")

        firestore.collection(FAMILIES_COLLECTION)
            .document(accountId)
            .collection(MEMBERS_COLLECTION)
            .document(memberId)
            .delete()
            .await()
        Timber.d("deleteMember: completed")
    }

    override suspend fun downloadSnapshot(accountId: String): ExportData? = coroutineScope {
        runCatching { firestore.enableNetwork().await() }
        val userId = auth.currentUser?.uid
            ?: throw FirebaseFirestoreException(
                "User not authenticated",
                FirebaseFirestoreException.Code.UNAUTHENTICATED
            )
        val familyDocRef = firestore.collection(FAMILIES_COLLECTION).document(accountId)
        val source = syncDownloadFirestoreSource()

        val snapshot = familyDocRef.get(source).await()
        if (!snapshot.exists()) return@coroutineScope null

        val updatedAt = snapshot.getTimestamp("updatedAt")
        val version = updatedAt?.seconds ?: 0L
        val versionNanos = updatedAt?.nanoseconds ?: 0

        val membersDeferred = async { familyDocRef.collection(MEMBERS_COLLECTION).get(source).await().toObjects<ExportMember>() }
        val tasksDeferred = async { downloadVisibleTasks(familyDocRef, source, userId) }
        val shoppingListsDeferred = async { familyDocRef.collection(SHOPPING_LISTS_COLLECTION).get(source).await().toObjects<ExportShoppingList>() }
        val shoppingCategoriesDeferred = async { familyDocRef.collection(SHOPPING_CATEGORIES_COLLECTION).get(source).await().toObjects<ExportShoppingCategory>() }
        val shoppingItemsDeferred = async { familyDocRef.collection(SHOPPING_ITEMS_COLLECTION).get(source).await().toObjects<ExportShoppingItem>() }
        val shoppingActivitiesDeferred = async { familyDocRef.collection(SHOPPING_ACTIVITY_COLLECTION).get(source).await().toObjects<ExportShoppingActivity>() }
        val eventsDeferred = async { familyDocRef.collection(EVENTS_COLLECTION).get(source).await().toObjects<ExportEvent>() }
        val scoresDeferred = async { familyDocRef.collection(SCORES_COLLECTION).get(source).await().toObjects<ExportScore>() }
        val rewardsDeferred = async { familyDocRef.collection(REWARDS_COLLECTION).get(source).await().toObjects<ExportReward>() }
        val rewardRequestsDeferred = async { familyDocRef.collection(REWARD_REQUESTS_COLLECTION).get(source).await().toObjects<ExportRewardRequest>() }
        val taskCompletionsDeferred = async { familyDocRef.collection(TASK_COMPLETIONS_COLLECTION).get(source).await().toObjects<ExportTaskCompletion>() }
        val achievementsDeferred = async { familyDocRef.collection(ACHIEVEMENTS_COLLECTION).get(source).await().toObjects<ExportAchievement>() }
        val walletsDeferred = async { familyDocRef.collection(WALLETS_COLLECTION).get(source).await().toObjects<ExportWallet>() }
        val penaltiesDeferred = async { familyDocRef.collection(PENALTIES_COLLECTION).get(source).await().toObjects<ExportPenalty>() }
        val taskRotationsDeferred = async { familyDocRef.collection(TASK_ROTATIONS_COLLECTION).get(source).await().toObjects<ExportTaskRotation>() }
        val houseRulesDeferred = async { familyDocRef.collection(HOUSE_RULES_COLLECTION).get(source).await().toObjects<ExportHouseRule>() }
        val taskTemplatesDeferred = async { familyDocRef.collection(TASK_TEMPLATES_COLLECTION).get(source).await().toObjects<ExportTaskTemplate>() }
        val taskCategoriesDeferred = async { familyDocRef.collection(TASK_CATEGORIES_COLLECTION).get(source).await().toObjects<ExportTaskCategory>() }
        val appreciationsDeferred = async { familyDocRef.collection(APPRECIATIONS_COLLECTION).get(source).await().toObjects<ExportAppreciation>() }
        val familyGoalsDeferred = async { downloadVisibleFamilyGoals(familyDocRef, source, userId) }
        val familyGoalStagesDeferred = async {
            val goals = familyGoalsDeferred.await()
            downloadVisibleFamilyGoalStages(familyDocRef, source, goals.map { it.id }.toSet())
        }
        val familyGoalStepsDeferred = async {
            val goals = familyGoalsDeferred.await()
            downloadVisibleFamilyGoalSteps(familyDocRef, source, goals.map { it.id }.toSet())
        }
        val emergencyContactsDeferred = async { familyDocRef.collection(EMERGENCY_CONTACTS_COLLECTION).get(source).await().toObjects<ExportEmergencyContact>() }
        val recipesDeferred = async {
            familyDocRef.collection(RECIPES_COLLECTION).get(source).await().documents.map { document ->
                (document.data ?: emptyMap<String, Any?>()).toExportRecipe(document.id)
            }
        }
        val menuDeferred = async { downloadMenuCollections(familyDocRef, source) }

        val menuSnapshot = menuDeferred.await()
        ExportData(
            members = membersDeferred.await(),
            tasks = tasksDeferred.await(),
            shoppingLists = shoppingListsDeferred.await(),
            shoppingCategories = shoppingCategoriesDeferred.await(),
            shoppingItems = shoppingItemsDeferred.await(),
            shoppingActivities = shoppingActivitiesDeferred.await(),
            events = eventsDeferred.await(),
            scores = scoresDeferred.await(),
            taskCompletions = taskCompletionsDeferred.await(),
            achievements = achievementsDeferred.await(),
            rewards = rewardsDeferred.await(),
            rewardRequests = rewardRequestsDeferred.await(),
            wallets = walletsDeferred.await(),
            penalties = penaltiesDeferred.await(),
            taskRotations = taskRotationsDeferred.await(),
            houseRules = houseRulesDeferred.await(),
            taskTemplates = taskTemplatesDeferred.await(),
            taskCategories = taskCategoriesDeferred.await(),
            appreciations = appreciationsDeferred.await(),
            familyGoals = familyGoalsDeferred.await(),
            familyGoalStages = familyGoalStagesDeferred.await(),
            familyGoalSteps = familyGoalStepsDeferred.await(),
            emergencyContacts = emergencyContactsDeferred.await(),
            recipes = recipesDeferred.await(),
            menuTemplates = menuSnapshot.menuTemplates,
            menuTemplateEntries = menuSnapshot.menuTemplateEntries,
            weeklyMenus = menuSnapshot.weeklyMenus,
            menuEntries = menuSnapshot.menuEntries,
            dailyAssignments = menuSnapshot.dailyAssignments,
            exportDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            version = version,
            versionNanos = versionNanos
        )
    }

    companion object {
        @VisibleForTesting
        internal fun syncDownloadFirestoreSource(): Source = Source.SERVER
    }
}
