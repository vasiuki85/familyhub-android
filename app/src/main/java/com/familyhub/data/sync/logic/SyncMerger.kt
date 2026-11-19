package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportAchievement
import com.familyhub.data.export.ExportAppreciation
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportEvent
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportHouseRule
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.export.ExportReward
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportRecipe
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
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.CompletionCycleValidation
import com.familyhub.domain.utils.PenaltySnapshotValidation
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.sync.achievementLogicalId
import timber.log.Timber
import com.familyhub.data.export.ExportEmergencyContact
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportTaskHistory
import com.familyhub.data.export.ExportWeeklyMenu
import androidx.annotation.VisibleForTesting
import javax.inject.Inject
import javax.inject.Singleton

@VisibleForTesting
internal fun filterLocalSnapshotForMerge(local: ExportData, currentUserId: String): ExportData {
    val visibleTasks = local.tasks.filter { task ->
        !task.private ||
            task.createdByUserId == currentUserId ||
            task.assignedToUserId == currentUserId
    }
    val visibleGoalIds = local.familyGoals
        .filter { !it.personal || it.createdByUserId == currentUserId }
        .map { it.id }
        .toSet()
    return local.copy(
        tasks = visibleTasks,
        familyGoals = local.familyGoals.filter { it.id in visibleGoalIds },
        familyGoalStages = local.familyGoalStages.filter { it.goalId in visibleGoalIds },
        familyGoalSteps = local.familyGoalSteps.filter { it.goalId in visibleGoalIds }
    )
}

@VisibleForTesting
internal fun isAssignedPrivateTask(task: ExportTask, currentUserId: String): Boolean {
    return task.private &&
        task.assignedToUserId == currentUserId &&
        task.createdByUserId != currentUserId
}

@VisibleForTesting
internal fun isOwnPrivateTask(task: ExportTask, currentUserId: String): Boolean {
    return task.private && task.createdByUserId == currentUserId
}

@Singleton
class SyncMerger @Inject constructor() {

    fun mergeSnapshots(
        local: ExportData,
        remote: ExportData?,
        currentUserId: String,
        isMemberWriteAllowed: Boolean,
        hasPendingChanges: Boolean = false,
        deletedMembers: Set<String> = emptySet(),
        deletedRecipeIds: Set<String> = emptySet(),
        deletedDocumentIdsByCollection: Map<String, Set<String>> = emptyMap(),
        /** Per-collection pending flags. Falls back to [hasPendingChanges] when key missing. */
        pendingByCollection: Map<String, Boolean> = emptyMap(),
        /** Exact dirty entity IDs for collections backed by the persisted operation journal. */
        pendingEntityIdsByCollection: Map<String, Set<String>> = emptyMap(),
        /** Entity IDs whose latest captured outbox operation is DELETE. Supersedes
         *  legacy tombstones for preventing remote-only entities from being restored. */
        pendingDeletedEntityIdsByCollection: Map<String, Set<String>> = emptyMap(),
    ): ExportData {
        fun hasPendingFor(key: String): Boolean = if (pendingByCollection.isEmpty()) {
            // Legacy callers (and a process restart before per-collection state is restored)
            // only have the aggregate flag.
            hasPendingChanges
        } else {
            // Once an explicit map is supplied, an absent key is deliberately clean.
            // Falling back to the aggregate flag here leaks a change in one collection
            // into every other collection.
            pendingByCollection[key] == true
        }
        fun hasPendingForEntity(collectionKey: String, entityId: String): Boolean {
            val explicitEntityIds = pendingEntityIdsByCollection[collectionKey]
            return explicitEntityIds?.contains(entityId) ?: hasPendingFor(collectionKey)
        }
        fun pendingDeletedIds(collectionKey: String): Set<String> =
            pendingDeletedEntityIdsByCollection[collectionKey].orEmpty()
        CompletionCycleValidation.validateExportSnapshotCycles(local)
        PenaltySnapshotValidation.validateExportSnapshotPenalties(local)
        if (remote != null) {
            CompletionCycleValidation.validateExportSnapshotCycles(remote)
            PenaltySnapshotValidation.validateExportSnapshotPenalties(remote)
        }

        if (remote == null) return filterLocalSnapshotForMerge(local, currentUserId)

        val weeklyMenus = local.weeklyMenus
        val menuEntries = local.menuEntries
        val menuTemplates = local.menuTemplates
        val menuTemplateEntries = local.menuTemplateEntries
        val taskHistory = local.taskHistory
        val dailyAssignments = local.dailyAssignments
        val filteredLocal = filterLocalSnapshotForMerge(local, currentUserId)

        val localNewer: (String) -> Boolean = { key -> hasPendingFor(key) }

        fun <T, K> mergeBy(
            localList: List<T>,
            remoteList: List<T>,
            keySelector: (T) -> K,
            resolver: (T, T) -> T,
            collectionKey: String,
            deletedIds: Set<K> = emptySet(),
        ): List<T> {
            val localMap = localList.associateBy(keySelector)
            val result = remoteList.associateBy(keySelector).toMutableMap()
            localList.forEach { l ->
                val k = keySelector(l)
                val r = result[k]
                if (r != null) {
                    result[k] = resolver(l, r)
                } else if (hasPendingForEntity(collectionKey, k.toString())) {
                    result[k] = l
                }
                // else: item not in remote and no pending → drop
            }
            deletedIds.forEach { id ->
                if (id !in localMap) {
                    result.remove(id)
                }
            }
            return result.values.toList()
        }

        fun deletedIds(collection: String): Set<String> =
            deletedDocumentIdsByCollection[collection].orEmpty()

        /** Union of legacy tombstones and outbox DELETE intents for a logical collection key. */
        fun effectiveDeletedIds(collectionKey: String, legacyCollection: String): Set<String> =
            deletedIds(legacyCollection) + pendingDeletedIds(collectionKey)

        /** Union for collections whose legacy tombstones are supplied as dedicated sets. */
        fun effectiveDeletedIds(collectionKey: String, legacyDeletedIds: Set<String>): Set<String> =
            legacyDeletedIds + pendingDeletedIds(collectionKey)

        fun <T, K> unionBy(a: List<T>, b: List<T>, key: (T) -> K): List<T> {
            val result = b.associateBy(key).toMutableMap()
            a.forEach { t -> result[key(t)] = t }
            return result.values.toList()
        }

        fun mergeAchievements(
            localAchievements: List<ExportAchievement>,
            remoteAchievements: List<ExportAchievement>,
        ): List<ExportAchievement> =
            (localAchievements + remoteAchievements)
                .groupBy { achievement ->
                    achievementLogicalId(achievement.userId, achievement.type)
                }
                .toSortedMap()
                .values
                .map { duplicates -> duplicates.maxBy(ExportAchievement::earnedAt) }

        fun resolveBySnapshot(l: ExportMember, r: ExportMember): ExportMember {
            fun pickNonBlankStr(pref: String, alt: String): String =
                when {
                    pref.isNotBlank() -> pref
                    alt.isNotBlank() -> alt
                    else -> pref
                }
            fun pickNonBlankOpt(pref: String?, alt: String?): String? =
                when {
                    !pref.isNullOrBlank() -> pref
                    !alt.isNullOrBlank() -> alt
                    else -> pref ?: alt
                }

            if (!isMemberWriteAllowed && l.id == currentUserId) {
                return r.copy(
                    displayName = pickNonBlankStr(r.displayName, l.displayName),
                    photoUrl = pickNonBlankOpt(r.photoUrl, l.photoUrl),
                    avatarColor = pickNonBlankStr(r.avatarColor, l.avatarColor),
                    birthDate = pickNonBlankOpt(r.birthDate, l.birthDate),
                    roleLabel = pickNonBlankStr(r.roleLabel ?: "", l.roleLabel ?: "")
                )
            }

            if (!isMemberWriteAllowed && l.id != currentUserId) {
                return r.copy(
                    displayName = pickNonBlankStr(r.displayName, l.displayName),
                    photoUrl = pickNonBlankOpt(r.photoUrl, l.photoUrl),
                    avatarColor = pickNonBlankStr(r.avatarColor, l.avatarColor),
                    birthDate = pickNonBlankOpt(r.birthDate, l.birthDate),
                    roleLabel = pickNonBlankStr(r.roleLabel ?: "", l.roleLabel ?: "")
                )
            }

            if (l.id != currentUserId) {
                if (l.role == r.role) {
                    return r.copy(
                        displayName = pickNonBlankStr(r.displayName, l.displayName),
                        photoUrl = pickNonBlankOpt(r.photoUrl, l.photoUrl),
                        avatarColor = pickNonBlankStr(r.avatarColor, l.avatarColor),
                        birthDate = pickNonBlankOpt(r.birthDate, l.birthDate),
                        roleLabel = pickNonBlankStr(r.roleLabel ?: "", l.roleLabel ?: "")
                    )
                }
            }

            if (l.id == currentUserId && l.role == MemberRole.PENDING.name && r.role == MemberRole.ADMIN.name) {
                Timber.w("resolveBySnapshot: Local PENDING but remote ADMIN for self. Keeping PENDING (joining new family).")
                return l
            }
            
            if (l.role == MemberRole.PENDING.name && r.role != MemberRole.PENDING.name && r.role != MemberRole.ADMIN.name) return r
            
            val base = if (localNewer("members")) l else r
            return base.copy(
                displayName = pickNonBlankStr(l.displayName, r.displayName),
                photoUrl = pickNonBlankOpt(l.photoUrl, r.photoUrl),
                avatarColor = pickNonBlankStr(l.avatarColor, r.avatarColor),
                birthDate = pickNonBlankOpt(l.birthDate, r.birthDate),
                roleLabel = pickNonBlankStr(l.roleLabel ?: "", r.roleLabel ?: "")
            )
        }
        fun resolveShoppingList(l: ExportShoppingList, r: ExportShoppingList) = if (l.createdAt >= r.createdAt) l else r
        fun resolveShoppingCategory(l: ExportShoppingCategory, r: ExportShoppingCategory) = if (localNewer("shoppingCategories")) l else r
        fun resolveShopping(l: ExportShoppingItem, r: ExportShoppingItem) =
            if (hasPendingForEntity("shoppingItems", l.id)) l else r
        fun resolveEvent(l: ExportEvent, r: ExportEvent) = if (localNewer("events")) l else r
        fun resolveReward(l: ExportReward, r: ExportReward) = if (localNewer("rewards")) l else r
        fun resolveTemplate(l: ExportTaskTemplate, r: ExportTaskTemplate) = if (localNewer("taskTemplates")) l else r

        fun resolveTask(l: ExportTask, r: ExportTask): ExportTask {
            if (l.completionCycle != r.completionCycle) {
                return CompletionCycleMerge.resolveTaskDifferentCycle(l, r)
            }

            fun statusRank(status: String): Int = when (status) {
                "DONE" -> 2
                "WAITING_FOR_APPROVAL" -> 1
                else -> 0
            }

            val remoteAhead = statusRank(r.status) > statusRank(l.status)
            val localAhead = statusRank(l.status) > statusRank(r.status)

            val base = when {
                !isMemberWriteAllowed -> when {
                    hasPendingForEntity("tasks", l.id) && !remoteAhead -> l
                    else -> r
                }
                hasPendingForEntity("tasks", l.id) -> l
                remoteAhead -> r
                localAhead -> l
                else -> r
            }
            return base.copy(
                attachments = CompletionCycleMerge.mergeAttachments(l, r),
                comments = CompletionCycleMerge.mergeComments(l, r)
            )
        }

        fun resolveHouseRule(l: ExportHouseRule, r: ExportHouseRule) = if (l.updatedAt >= r.updatedAt) l else r
        fun resolveTaskCategory(l: ExportTaskCategory, r: ExportTaskCategory) = if (l.updatedAt >= r.updatedAt) l else r
        fun resolveFamilyGoal(l: ExportFamilyGoal, r: ExportFamilyGoal) = if (l.updatedAt >= r.updatedAt) l else r
        fun resolveFamilyGoalStage(l: ExportFamilyGoalStage, r: ExportFamilyGoalStage) = if (localNewer("goalStages")) l else r
        fun resolveFamilyGoalStep(l: ExportFamilyGoalStep, r: ExportFamilyGoalStep) = if (localNewer("goalSteps")) l else r
        fun resolveEmergencyContact(l: ExportEmergencyContact, r: ExportEmergencyContact) = if (localNewer("emergencyContacts")) l else r
        fun resolvePenalty(l: ExportPenalty, r: ExportPenalty) =
            AccountingProjection.resolvePenaltyMonotonic(l, r, localNewer("penalties"))
        fun resolveRecipe(l: ExportRecipe, r: ExportRecipe) = if (l.updatedAt >= r.updatedAt) l else r
        fun resolveTaskCompletion(l: ExportTaskCompletion, r: ExportTaskCompletion): ExportTaskCompletion =
            CompletionCycleMerge.resolveTaskCompletion(l, r)
        fun resolveRotation(l: ExportTaskRotation, r: ExportTaskRotation): ExportTaskRotation {
            val lTs = listOfNotNull(l.lastRotationAt, l.nextRotationAt, l.startDateTime).maxOrNull() ?: 0L
            val rTs = listOfNotNull(r.lastRotationAt, r.nextRotationAt, r.startDateTime).maxOrNull() ?: 0L
            return if (lTs >= rTs) l else r
        }
        fun resolveShoppingActivity(l: ExportShoppingActivity, r: ExportShoppingActivity): ExportShoppingActivity {
            return if (l.occurredAt >= r.occurredAt) l else r
        }

        fun mergeTasks(): List<ExportTask> {
            val localMap = filteredLocal.tasks.associateBy { it.id }
            val remoteById = remote.tasks.associateBy { it.id }
            val result = remote.tasks.associateBy { it.id }.toMutableMap()
            filteredLocal.tasks.forEach { localTask ->
                val remoteTask = remoteById[localTask.id]
                when {
                    isAssignedPrivateTask(localTask, currentUserId) -> {
                        if (remoteTask != null) {
                            result[localTask.id] = resolveTask(localTask, remoteTask)
                        }
                    }
                    else -> {
                        if (remoteTask != null) {
                            result[localTask.id] = resolveTask(localTask, remoteTask)
                        } else if (hasPendingForEntity("tasks", localTask.id)) {
                            result[localTask.id] = localTask
                        }
                        // else: task not in remote and no pending → drop
                    }
                }
            }
            deletedIds(DeletedCloudDocumentCollections.TASKS).forEach { id ->
                if (id !in localMap) {
                    result.remove(id)
                }
            }
            pendingDeletedIds("tasks").forEach { id ->
                if (id !in localMap) {
                    result.remove(id)
                }
            }
            return result.values.toList()
        }

        fun mergeMembers(deletedMembers: Set<String> = emptySet()): List<ExportMember> {
            val localMap = filteredLocal.members.associateBy { it.id }
            val remoteMap = remote.members.associateBy { it.id }
            val ids = (localMap.keys + remoteMap.keys).toSet()
            
            Timber.d("mergeMembers: local members=%d (%s), remote members=%d (%s), isMemberWriteAllowed=%s, localNewer=%s, deletedMembers=%s",
                filteredLocal.members.size, filteredLocal.members.map { "${it.id}:${it.role}" }.joinToString(),
                remote.members.size, remote.members.map { "${it.id}:${it.role}" }.joinToString(),
                isMemberWriteAllowed, localNewer, deletedMembers)
            
            val result = ids.mapNotNull { id ->
                if (id in deletedMembers) {
                    Timber.w("mergeMembers: Member %s is in deleted members list, not adding back.", id)
                    return@mapNotNull null
                }
                
                val l = localMap[id]
                val r = remoteMap[id]
                when {
                    l != null && r != null -> resolveBySnapshot(l, r)
                    l != null -> {
                        if (isMemberWriteAllowed || l.id == currentUserId) l else null
                    }
                    r != null -> {
                        if (r.role == MemberRole.ADMIN.name) {
                            Timber.d("mergeMembers: Preserving ADMIN from remote: %s", r.id)
                            return@mapNotNull r
                        }
                        if (isMemberWriteAllowed && l == null) {
                            if (r.id in deletedMembers) {
                                Timber.w("mergeMembers: Admin rejected member locally (in deletedMembers), not adding from remote: %s (role=%s)", r.id, r.role)
                                null
                            } else {
                                Timber.d("mergeMembers: Adding new member from remote (admin hasn't seen yet): %s (role=%s)", r.id, r.role)
                                r
                            }
                        } else if (isMemberWriteAllowed && localNewer("members")) {
                            Timber.d("mergeMembers: Admin deleted member locally: %s", r.id)
                            null
                        } else {
                            Timber.d("mergeMembers: Adding member from remote: %s (role=%s)", r.id, r.role)
                            r
                        }
                    }
                    else -> null
                }
            }
            
            val adminCount = result.count { it.role == MemberRole.ADMIN.name }
            if (adminCount > 1) {
                Timber.e("mergeMembers: CRITICAL - Multiple admins detected (%d)! This should never happen.", adminCount)
                val localAdminId = localMap.values.firstOrNull { it.role == MemberRole.ADMIN.name }?.id
                val remoteAdminId = remote.members.firstOrNull { it.role == MemberRole.ADMIN.name }?.id
                val preferredAdminId = localAdminId ?: remoteAdminId ?: result.first { it.role == MemberRole.ADMIN.name }.id

                val demotedIds = mutableListOf<String>()
                val fixedResult = result.map { member ->
                    if (member.role == MemberRole.ADMIN.name && member.id != preferredAdminId) {
                        val previousRole = localMap[member.id]?.role ?: MemberRole.CHILD.name
                        val previousRoleLabel = localMap[member.id]?.roleLabel ?: when (previousRole) {
                            MemberRole.ADMIN.name -> "Администратор"
                            MemberRole.PARENT.name -> "Родитель"
                            MemberRole.CHILD.name -> "Ребенок"
                            MemberRole.PENDING.name -> "В ожидании"
                            else -> "Родитель"
                        }
                        demotedIds += member.id
                        member.copy(role = previousRole, roleLabel = previousRoleLabel)
                    } else {
                        member
                    }
                }

                Timber.w("mergeMembers: Fixed multiple admins issue. Kept admin: %s, demoted: %s", 
                    preferredAdminId, demotedIds.joinToString())
                return fixedResult
            } else if (adminCount == 0 && remote.members.isNotEmpty()) {
                val remoteAdmin = remote.members.firstOrNull { it.role == MemberRole.ADMIN.name }
                if (remoteAdmin != null && remoteAdmin.id !in deletedMembers) {
                    Timber.e("mergeMembers: CRITICAL - No admin in result but family exists! Restoring admin from remote: %s", remoteAdmin.id)
                    return result + remoteAdmin
                }
            }
            
            val resultIds = result.map { it.id }.toSet()
            val remoteAdminIds = remote.members.filter { it.role == MemberRole.ADMIN.name }.map { it.id }.toSet()
            val missingAdmins = remoteAdminIds - resultIds
            if (missingAdmins.isNotEmpty()) {
                Timber.e("mergeMembers: CRITICAL - Missing admins in result: %s! This should never happen!", missingAdmins)
                val fixedResult = result.toMutableList()
                remote.members.forEach { remoteMember ->
                    if (remoteMember.role == MemberRole.ADMIN.name && remoteMember.id !in resultIds) {
                        Timber.w("mergeMembers: Restoring missing admin: %s", remoteMember.id)
                        fixedResult.add(remoteMember)
                    }
                }
                return fixedResult
            }
            
            return result
        }

        fun mergeRecipes(deletedRecipeIds: Set<String>): List<ExportRecipe> {
            val localMap = filteredLocal.recipes.associateBy { it.id }
            val remoteMap = remote.recipes.associateBy { it.id }
            val ids = (localMap.keys + remoteMap.keys).toSet()

            return ids.mapNotNull { id ->
                if (id in deletedRecipeIds && id !in localMap) {
                    return@mapNotNull null
                }

                val localRecipe = localMap[id]
                val remoteRecipe = remoteMap[id]
                when {
                    localRecipe != null && remoteRecipe != null -> resolveRecipe(localRecipe, remoteRecipe)
                    localRecipe != null && hasPendingFor("recipes") -> localRecipe
                    remoteRecipe != null -> remoteRecipe
                    else -> null
                }
            }
        }

        val mergedTaskCompletions = CompletionCycleMerge.mergeTaskCompletions(
            filteredLocal.taskCompletions,
            remote.taskCompletions
        )
        val mergedRewardRequests = RewardRequestMerge.mergeRewardRequests(
            filteredLocal.rewardRequests,
            remote.rewardRequests
        )
        val mergedPenalties = mergeBy(
            filteredLocal.penalties,
            remote.penalties,
            ExportPenalty::id,
            ::resolvePenalty,
            "penalties",
        )
        val projectedAccounting = AccountingProjection.projectMaterializedCaches(
            local = filteredLocal,
            remote = remote,
            taskCompletions = mergedTaskCompletions,
            penalties = mergedPenalties,
            rewardRequests = mergedRewardRequests
        )

        return remote.copy(
            members = mergeMembers(effectiveDeletedIds("members", deletedMembers)),
            tasks = mergeTasks(),
            shoppingLists = mergeBy(
                filteredLocal.shoppingLists,
                remote.shoppingLists,
                ExportShoppingList::id,
                ::resolveShoppingList,
                "shoppingLists",
                effectiveDeletedIds("shoppingLists", DeletedCloudDocumentCollections.SHOPPING_LISTS)
            ),
            shoppingCategories = mergeBy(
                filteredLocal.shoppingCategories,
                remote.shoppingCategories,
                ExportShoppingCategory::id,
                ::resolveShoppingCategory,
                "shoppingCategories",
                effectiveDeletedIds("shoppingCategories", DeletedCloudDocumentCollections.SHOPPING_CATEGORIES)
            ),
            shoppingItems = mergeBy(
                filteredLocal.shoppingItems,
                remote.shoppingItems,
                ExportShoppingItem::id,
                ::resolveShopping,
                "shoppingItems",
                effectiveDeletedIds("shoppingItems", DeletedCloudDocumentCollections.SHOPPING_ITEMS),
            ),
            shoppingActivities = mergeBy(
                filteredLocal.shoppingActivities,
                remote.shoppingActivities,
                ExportShoppingActivity::id,
                ::resolveShoppingActivity,
                "shoppingActivity",
            ),
            events = mergeBy(
                filteredLocal.events,
                remote.events,
                ExportEvent::id,
                ::resolveEvent,
                "events",
                effectiveDeletedIds("events", DeletedCloudDocumentCollections.EVENTS),
            ),
            taskCompletions = mergedTaskCompletions,
            achievements = mergeAchievements(filteredLocal.achievements, remote.achievements),
            rewards = mergeBy(
                filteredLocal.rewards,
                remote.rewards,
                ExportReward::id,
                ::resolveReward,
                "rewards",
                effectiveDeletedIds("rewards", DeletedCloudDocumentCollections.REWARDS)
            ),
            rewardRequests = mergedRewardRequests,
            scores = projectedAccounting.scores,
            wallets = projectedAccounting.wallets,
            penalties = mergedPenalties,
            taskRotations = mergeBy(
                filteredLocal.taskRotations,
                remote.taskRotations,
                ExportTaskRotation::taskId,
                ::resolveRotation,
                "taskRotations",
                effectiveDeletedIds("taskRotations", DeletedCloudDocumentCollections.TASK_ROTATIONS)
            ),
            houseRules = mergeBy(
                filteredLocal.houseRules,
                remote.houseRules,
                ExportHouseRule::id,
                ::resolveHouseRule,
                "houseRules",
                effectiveDeletedIds("houseRules", DeletedCloudDocumentCollections.HOUSE_RULES)
            ),
            taskTemplates = mergeBy(
                filteredLocal.taskTemplates,
                remote.taskTemplates,
                ExportTaskTemplate::id,
                ::resolveTemplate,
                "taskTemplates",
                effectiveDeletedIds("taskTemplates", DeletedCloudDocumentCollections.TASK_TEMPLATES)
            ),
            taskCategories = mergeBy(
                filteredLocal.taskCategories,
                remote.taskCategories,
                ExportTaskCategory::id,
                ::resolveTaskCategory,
                "taskCategories",
                effectiveDeletedIds("taskCategories", DeletedCloudDocumentCollections.TASK_CATEGORIES)
            ),
            appreciations = mergeBy(
                filteredLocal.appreciations,
                remote.appreciations,
                ExportAppreciation::id,
                { local, _ -> local },
                "appreciations",
                effectiveDeletedIds("appreciations", DeletedCloudDocumentCollections.APPRECIATIONS)
            ),
            familyGoals = mergeBy(
                filteredLocal.familyGoals,
                remote.familyGoals,
                ExportFamilyGoal::id,
                ::resolveFamilyGoal,
                "familyGoals",
                effectiveDeletedIds("familyGoals", DeletedCloudDocumentCollections.FAMILY_GOALS)
            ),
            familyGoalStages = mergeBy(
                filteredLocal.familyGoalStages,
                remote.familyGoalStages,
                ExportFamilyGoalStage::id,
                ::resolveFamilyGoalStage,
                "goalStages",
                effectiveDeletedIds("goalStages", DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES)
            ),
            familyGoalSteps = mergeBy(
                filteredLocal.familyGoalSteps,
                remote.familyGoalSteps,
                ExportFamilyGoalStep::id,
                ::resolveFamilyGoalStep,
                "goalSteps",
                effectiveDeletedIds("goalSteps", DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS)
            ),
            emergencyContacts = mergeBy(
                filteredLocal.emergencyContacts,
                remote.emergencyContacts,
                ExportEmergencyContact::id,
                ::resolveEmergencyContact,
                "emergencyContacts",
                effectiveDeletedIds("emergencyContacts", DeletedCloudDocumentCollections.EMERGENCY_CONTACTS)
            ),
            recipes = mergeRecipes(effectiveDeletedIds("recipes", deletedRecipeIds)),
            exportDate = filteredLocal.exportDate,
            menuTemplates = menuTemplates,
            menuTemplateEntries = menuTemplateEntries,
            weeklyMenus = weeklyMenus,
            menuEntries = menuEntries,
            taskHistory = taskHistory,
            dailyAssignments = dailyAssignments
        )
    }

    fun normalizedForComparison(data: ExportData): ExportData {
        return data.copy(
            members = data.members.sortedBy(ExportMember::id),
            tasks = data.tasks
                .sortedBy(ExportTask::id)
                .map { task ->
                    task.copy(
                        attachments = task.attachments.sortedBy(ExportTaskAttachment::id),
                        comments = task.comments.sortedWith(compareBy(ExportTaskComment::createdAt, ExportTaskComment::id))
                    )
                },
            shoppingLists = data.shoppingLists.sortedBy(ExportShoppingList::id),
            shoppingCategories = data.shoppingCategories.sortedBy(ExportShoppingCategory::id),
            shoppingItems = data.shoppingItems.sortedBy(ExportShoppingItem::id),
            shoppingActivities = data.shoppingActivities.sortedWith(compareBy(ExportShoppingActivity::occurredAt, ExportShoppingActivity::id)),
            events = data.events.sortedBy(ExportEvent::id),
            scores = data.scores.sortedWith(compareBy(ExportScore::userId, ExportScore::weekStartDate)),
            taskCompletions = data.taskCompletions.sortedBy(ExportTaskCompletion::id),
            achievements = data.achievements.sortedBy { "${it.userId}:${it.type}:${it.earnedAt}" },
            rewards = data.rewards.sortedBy(ExportReward::id),
            rewardRequests = data.rewardRequests.sortedBy(ExportRewardRequest::id),
            wallets = data.wallets.sortedBy(ExportWallet::userId),
            taskRotations = data.taskRotations.sortedBy(ExportTaskRotation::taskId),
            houseRules = data.houseRules.sortedBy(ExportHouseRule::id),
            taskTemplates = data.taskTemplates.sortedBy(ExportTaskTemplate::id),
            taskCategories = data.taskCategories.sortedBy(ExportTaskCategory::id),
            appreciations = data.appreciations.sortedBy(ExportAppreciation::id),
            familyGoals = data.familyGoals.sortedBy(ExportFamilyGoal::id),
            familyGoalStages = data.familyGoalStages.sortedBy(ExportFamilyGoalStage::id),
            familyGoalSteps = data.familyGoalSteps.sortedBy(ExportFamilyGoalStep::id),
            penalties = data.penalties.sortedBy(ExportPenalty::id),
            emergencyContacts = data.emergencyContacts.sortedBy(ExportEmergencyContact::id),
            recipes = data.recipes
                .sortedBy(ExportRecipe::id)
                .map { recipe ->
                    recipe.copy(
                        tags = recipe.tags.sorted(),
                        ingredients = recipe.ingredients.sortedBy { ingredient ->
                            ingredient.id.ifBlank { "${ingredient.name}:${ingredient.quantity}" }
                        },
                        steps = recipe.steps.sortedWith(compareBy(ExportRecipeStep::order, ExportRecipeStep::id))
                    )
                },
            menuTemplates = data.menuTemplates.sortedBy(ExportMenuTemplate::id),
            menuTemplateEntries = data.menuTemplateEntries.sortedBy(ExportMenuTemplateEntry::id),
            weeklyMenus = data.weeklyMenus.sortedBy(ExportWeeklyMenu::id),
            menuEntries = data.menuEntries.sortedBy(ExportMenuEntry::id),
            taskHistory = data.taskHistory.sortedBy(ExportTaskHistory::id),
            dailyAssignments = data.dailyAssignments.sortedBy(ExportDailyAssignment::id)
        )
    }
}
