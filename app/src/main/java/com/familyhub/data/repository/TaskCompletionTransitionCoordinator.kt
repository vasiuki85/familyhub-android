package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.entity.TaskCompletionEntity
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.data.local.entity.UserAchievementEntity
import com.familyhub.data.local.entity.UserScoreEntity
import com.familyhub.data.local.entity.UserWalletEntity
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.domain.model.AchievementType
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.ScoreImpact
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskTransitionInvalidException
import com.familyhub.domain.model.TaskTransitionResult
import com.familyhub.domain.utils.AchievementEligibility
import com.familyhub.domain.utils.AchievementEligibilitySnapshot
import com.familyhub.domain.utils.CompletionCycleIds
import com.familyhub.domain.utils.CompletionCycleMath
import com.familyhub.domain.utils.CompletionCycleValidation
import com.familyhub.domain.utils.RepeatSuccessorIds
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.util.TimeUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Atomic task status transitions with monotonic completion ledger.
 * All writes run inside a single Room [withTransaction]; callers must re-read task state here,
 * never trust a stale [Task] from UI.
 */
@Singleton
class TaskCompletionTransitionCoordinator @Inject constructor(
    private val database: FamilyHubDatabase
) {
    suspend fun complete(
        taskId: String,
        actorUserId: String,
        completionTime: Long = TimeUtils.now()
    ): TaskTransitionResult = database.withTransaction {
        val current = loadTaskOrThrow(taskId)
        val previousStatus = current.status
        when (current.status) {
            TaskStatus.DONE -> TaskTransitionResult.noOp(taskId, TaskStatus.DONE)
            TaskStatus.WAITING_FOR_APPROVAL -> TaskTransitionResult.noOp(taskId, TaskStatus.WAITING_FOR_APPROVAL)
            TaskStatus.TODO -> {
                val actor = TaskActorAuthorization.requireCanComplete(
                    familyMemberDao(),
                    actorUserId,
                    current
                )
                if (TaskActorAuthorization.needsApproval(actor, current)) {
                    val updated = current.copy(
                        status = TaskStatus.WAITING_FOR_APPROVAL,
                        requiresApproval = true
                    )
                    taskDao().upsert(updated.toEntity())
                    TaskTransitionResult(
                        taskId = taskId,
                        previousStatus = previousStatus,
                        newStatus = TaskStatus.WAITING_FOR_APPROVAL,
                        scoreImpact = emptyImpact(),
                        mutated = true,
                        repeatCreated = false
                    )
                } else {
                    awardAndComplete(current, completionTime)
                }
            }
        }
    }

    suspend fun confirm(
        taskId: String,
        actorUserId: String,
        completionTime: Long = TimeUtils.now()
    ): TaskTransitionResult = database.withTransaction {
        TaskActorAuthorization.requireAdminOrParent(
            familyMemberDao(),
            actorUserId,
            "Only parent or admin can confirm tasks"
        )
        val current = loadTaskOrThrow(taskId)
        when (current.status) {
            TaskStatus.DONE -> TaskTransitionResult.noOp(taskId, TaskStatus.DONE)
            TaskStatus.WAITING_FOR_APPROVAL -> awardAndComplete(
                current.copy(requiresApproval = false),
                completionTime
            )
            TaskStatus.TODO -> TaskTransitionResult.noOp(taskId, TaskStatus.TODO)
        }
    }

    suspend fun cancelWaiting(
        taskId: String,
        actorUserId: String
    ): TaskTransitionResult = database.withTransaction {
        val current = loadTaskOrThrow(taskId)
        val previousStatus = current.status
        when (current.status) {
            TaskStatus.WAITING_FOR_APPROVAL -> {
                TaskActorAuthorization.requireCanCancelWaiting(
                    familyMemberDao(),
                    actorUserId,
                    current
                )
                CompletionCycleValidation.requireNonNegativeTaskCycle(current.completionCycle)
                val nextCycle = CompletionCycleMath.nextCycle(current.completionCycle)
                val updated = current.copy(
                    status = TaskStatus.TODO,
                    requiresApproval = true,
                    // Rejecting/cancelling closes this approval attempt. The next
                    // cycle is a monotonic sync marker that prevents an older
                    // WAITING copy from resurrecting the rejected attempt.
                    completionCycle = nextCycle
                )
                taskDao().upsert(updated.toEntity())
                TaskTransitionResult(
                    taskId = taskId,
                    previousStatus = previousStatus,
                    newStatus = TaskStatus.TODO,
                    scoreImpact = emptyImpact(),
                    mutated = true,
                    repeatCreated = false
                )
            }
            TaskStatus.TODO -> TaskTransitionResult.noOp(taskId, TaskStatus.TODO)
            TaskStatus.DONE -> TaskTransitionResult.noOp(taskId, TaskStatus.DONE)
        }
    }

    suspend fun undo(
        taskId: String,
        actorUserId: String,
        undoTime: Long = TimeUtils.now()
    ): TaskTransitionResult = database.withTransaction {
        TaskActorAuthorization.requireAdminOrParent(
            familyMemberDao(),
            actorUserId,
            "Only parent or admin can undo completed tasks"
        )
        val current = loadTaskOrThrow(taskId)
        val previousStatus = current.status
        when (current.status) {
            TaskStatus.DONE -> {
                CompletionCycleValidation.requireNonNegativeTaskCycle(current.completionCycle)
                val nextCycle = CompletionCycleMath.nextCycle(current.completionCycle)
                val activeCompletion = resolveActiveCompletionForUndo(current)

                val updated = current.copy(
                    status = TaskStatus.TODO,
                    completionCycle = nextCycle
                )
                taskDao().upsert(updated.toEntity())

                if (activeCompletion == null) {
                    return@withTransaction TaskTransitionResult(
                        taskId = taskId,
                        previousStatus = previousStatus,
                        newStatus = TaskStatus.TODO,
                        scoreImpact = emptyImpact(),
                        mutated = true,
                        repeatCreated = false
                    )
                }

                requireValidTotalPoints(activeCompletion.totalPoints)
                val refundDelta = AccountingIntMath.negateExact(activeCompletion.totalPoints)

                val reversed = activeCompletion.copy(
                    reversedAt = undoTime,
                    reversedByUserId = actorUserId,
                    updatedAt = undoTime
                )
                taskCompletionDao().insert(reversed)

                adjustScore(
                    userId = activeCompletion.userId,
                    weekStartDate = activeCompletion.weekStartDate,
                    delta = refundDelta,
                    timestamp = undoTime
                )
                adjustWallet(
                    userId = activeCompletion.userId,
                    delta = refundDelta,
                    timestamp = undoTime
                )
                revokeIneligibleAchievements(activeCompletion.userId)

                TaskTransitionResult(
                    taskId = taskId,
                    previousStatus = previousStatus,
                    newStatus = TaskStatus.TODO,
                    scoreImpact = ScoreImpact(
                        basePoints = AccountingIntMath.negateExact(activeCompletion.basePoints),
                        bonusPoints = AccountingIntMath.negateExact(activeCompletion.bonusPoints),
                        penaltyPoints = AccountingIntMath.negateExact(activeCompletion.penaltyPoints),
                        totalDelta = refundDelta,
                        achievementsUnlocked = emptyList()
                    ),
                    mutated = true,
                    repeatCreated = false
                )
            }
            TaskStatus.TODO -> TaskTransitionResult.noOp(taskId, TaskStatus.TODO)
            TaskStatus.WAITING_FOR_APPROVAL -> TaskTransitionResult.noOp(taskId, TaskStatus.WAITING_FOR_APPROVAL)
        }
    }

    private suspend fun resolveActiveCompletionForUndo(task: Task): TaskCompletionEntity? {
        val cycleCompletion = taskCompletionDao().getActiveCompletionForTaskCycle(
            task.id,
            task.completionCycle
        )
        if (cycleCompletion != null) {
            return cycleCompletion
        }
        return taskCompletionDao().getLatestLegacyActiveCompletionForTask(task.id)
    }

    private suspend fun awardAndComplete(
        task: Task,
        completionTime: Long
    ): TaskTransitionResult {
        val fresh = loadTaskOrThrow(task.id)
        if (fresh.status == TaskStatus.DONE) {
            return TaskTransitionResult.noOp(task.id, TaskStatus.DONE)
        }

        CompletionCycleValidation.requireNonNegativeTaskCycle(fresh.completionCycle)
        val previousStatus = fresh.status
        val cycle = fresh.completionCycle
        val basePoints = fresh.rewardPoints ?: 0
        requireNonNegativeRewardPoints(basePoints)
        val bonusPoints = 0
        val penaltyPoints = 0
        val totalDelta = AccountingIntMath.applyDelta(0, basePoints)

        val doneTask = fresh.copy(status = TaskStatus.DONE, requiresApproval = false)
        taskDao().upsert(doneTask.toEntity())

        val weekStart = WeekUtils.getWeekStartMillis(completionTime)
        val (generatedRepeatTaskId, repeatCreated) = resolveAndMaybeCreateRepeatSuccessor(fresh, completionTime)

        val completionEntity = TaskCompletionEntity(
            id = CompletionCycleIds.deterministicId(fresh.id, cycle),
            taskId = fresh.id,
            userId = fresh.assignedToUserId,
            taskType = fresh.taskType.name,
            completionTime = completionTime,
            weekStartDate = weekStart,
            basePoints = basePoints,
            bonusPoints = bonusPoints,
            penaltyPoints = penaltyPoints,
            totalPoints = totalDelta,
            reversedAt = null,
            reversedByUserId = null,
            updatedAt = completionTime,
            generatedRepeatTaskId = generatedRepeatTaskId,
            completionCycle = cycle
        )
        taskCompletionDao().insert(completionEntity)

        adjustScore(fresh.assignedToUserId, weekStart, totalDelta, completionTime)
        val unlocked = evaluateAchievements(fresh.assignedToUserId, weekStart)
        unlocked.forEach { type ->
            userAchievementDao().insert(
                UserAchievementEntity(
                    userId = fresh.assignedToUserId,
                    type = type.name,
                    earnedAt = completionTime
                )
            )
        }
        adjustWallet(fresh.assignedToUserId, totalDelta, completionTime)

        return TaskTransitionResult(
            taskId = fresh.id,
            previousStatus = previousStatus,
            newStatus = TaskStatus.DONE,
            scoreImpact = ScoreImpact(
                basePoints = basePoints,
                bonusPoints = bonusPoints,
                penaltyPoints = penaltyPoints,
                totalDelta = totalDelta,
                achievementsUnlocked = unlocked
            ),
            mutated = true,
            repeatCreated = repeatCreated
        )
    }

    private fun requireNonNegativeRewardPoints(rewardPoints: Int) {
        if (rewardPoints < 0) {
            throw TaskTransitionInvalidException("Task rewardPoints must be >= 0")
        }
    }

    private fun requireValidTotalPoints(totalPoints: Int) {
        if (totalPoints == Int.MIN_VALUE) {
            throw TaskTransitionInvalidException("Corrupt completion totalPoints: Int.MIN_VALUE")
        }
    }

    private suspend fun resolveAndMaybeCreateRepeatSuccessor(
        task: Task,
        completionTime: Long
    ): Pair<String?, Boolean> {
        if (task.repeatRule == RepeatRule.NONE) {
            return null to false
        }

        val latestLedger = taskCompletionDao().getLatestCompletionForTask(task.id)
        val successorId = latestLedger?.generatedRepeatTaskId
            ?: RepeatSuccessorIds.deterministicId(task.id)

        if (taskDao().getById(successorId) != null) {
            return successorId to false
        }

        if (latestLedger?.generatedRepeatTaskId != null) {
            return successorId to false
        }

        taskDao().upsert(buildRepeatTask(task, successorId, completionTime))
        return successorId to true
    }

    private fun buildRepeatTask(original: Task, repeatId: String, now: Long): TaskEntity {
        val nextDue = when (original.repeatRule) {
            RepeatRule.DAILY -> TimeUtils.addDays(original.dueDate ?: now, 1)
            RepeatRule.WEEKLY -> TimeUtils.addDays(original.dueDate ?: now, 7)
            RepeatRule.NONE -> original.dueDate
        }
        return original.copy(
            id = repeatId,
            status = TaskStatus.TODO,
            dueDate = nextDue,
            createdAt = now,
            completionCycle = 0L
        ).toEntity()
    }

    private suspend fun adjustScore(
        userId: String,
        weekStartDate: Long,
        delta: Int,
        timestamp: Long
    ) {
        val existing = userScoreDao().getUserScoreForWeek(userId, weekStartDate)
        val newScore = AccountingIntMath.applyDelta(existing?.score, delta)
        userScoreDao().upsert(
            UserScoreEntity(
                userId = userId,
                weekStartDate = weekStartDate,
                score = newScore,
                updatedAt = timestamp
            )
        )
    }

    private suspend fun adjustWallet(userId: String, delta: Int, timestamp: Long) {
        val current = userWalletDao().get(userId)
        val newBalance = AccountingIntMath.applyDelta(current?.balance, delta)
        userWalletDao().upsert(
            UserWalletEntity(
                userId = userId,
                balance = newBalance,
                updatedAt = timestamp
            )
        )
    }

    private suspend fun evaluateAchievements(
        userId: String,
        weekStart: Long
    ): List<AchievementType> {
        val unlocked = mutableListOf<AchievementType>()
        val existing = userAchievementDao().getAchievementsForUser(userId).first()
        val existingTypes = existing.mapNotNull { entity ->
            runCatching { AchievementType.valueOf(entity.type) }.getOrNull()
        }.toSet()

        val totalCompletions = taskCompletionDao().getCompletionCount(userId)
        if (totalCompletions >= 1 && AchievementType.FIRST_TASK !in existingTypes) {
            unlocked += AchievementType.FIRST_TASK
        }
        if (totalCompletions >= 10 && AchievementType.TEN_TASKS !in existingTypes) {
            unlocked += AchievementType.TEN_TASKS
        }

        val lateCount = taskCompletionDao().getLateCompletionCountForWeek(userId, weekStart)
        val weekCompletions = taskCompletionDao().getCompletionsForUserInWeek(userId, weekStart)
        if (weekCompletions.isNotEmpty() && lateCount == 0 && AchievementType.PERFECT_WEEK !in existingTypes) {
            unlocked += AchievementType.PERFECT_WEEK
        }

        return unlocked
    }

    /**
     * Undo mirror of [evaluateAchievements]: after the reversal is ledgered, drop any
     * achievement whose eligibility no longer holds. Runs inside the undo transaction so a
     * failure here rolls back the whole undo (score, wallet and ledger included).
     */
    private suspend fun revokeIneligibleAchievements(userId: String) {
        val existing = userAchievementDao().getAchievementsForUser(userId).first()
        val existingTypes = existing.mapNotNull { entity ->
            runCatching { AchievementType.valueOf(entity.type) }.getOrNull()
        }.toSet()
        if (existingTypes.isEmpty()) return

        val active = taskCompletionDao().getActiveCompletionsForUser(userId).first()
        val hasAnyPerfectWeek = active
            .groupBy { it.weekStartDate }
            .values
            .any { week -> week.none { it.penaltyPoints > 0 } }
        val snapshot = AchievementEligibilitySnapshot(
            totalCompletions = active.size,
            // typesToRevoke only reads totalCompletions and hasAnyPerfectWeek.
            currentWeekCompletionCount = 0,
            currentWeekLateCompletionCount = 0,
            hasAnyPerfectWeek = hasAnyPerfectWeek
        )
        AchievementEligibility.typesToRevoke(snapshot, existingTypes).forEach { type ->
            userAchievementDao().deleteByUserAndType(userId, type.name)
        }
    }

    private suspend fun loadTaskOrThrow(taskId: String): Task {
        val entity = taskDao().getById(taskId)
            ?: throw TaskTransitionInvalidException("Task not found: $taskId")
        return entity.toDomain()
    }

    private fun emptyImpact() = ScoreImpact(0, 0, 0, 0, emptyList())

    private fun taskDao() = database.taskDao()
    private fun taskCompletionDao() = database.taskCompletionDao()
    private fun userScoreDao() = database.userScoreDao()
    private fun userWalletDao() = database.userWalletDao()
    private fun userAchievementDao() = database.userAchievementDao()
    private fun familyMemberDao() = database.familyMemberDao()
}
