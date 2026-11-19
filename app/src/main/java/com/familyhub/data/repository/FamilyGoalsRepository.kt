package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.FamilyGoalDao
import com.familyhub.data.local.entity.StageWithSteps
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalDetail
import com.familyhub.domain.model.FamilyGoalStage
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.domain.model.FamilyGoalStep
import com.familyhub.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FamilyGoalsRepository @Inject constructor(
    private val familyGoalDao: FamilyGoalDao,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    fun getAllGoalDetails(): Flow<List<FamilyGoalDetail>> =
        familyGoalDao.getAllWithStages().map { goals ->
            goals.map { relation ->
                val stages = relation.stages
                    .sortedBy { it.stage.position }
                    .map(StageWithSteps::toDomain)
                FamilyGoalDetail(
                    goal = relation.goal.toDomain(),
                    stages = stages
                )
            }
        }

    suspend fun upsert(goal: FamilyGoal, stages: List<FamilyGoalStage>) {
        val oldStageIds = familyGoalDao.getStageIdsByGoal(goal.id).toSet()
        val oldStepIds = familyGoalDao.getStepIdsByGoal(goal.id).toSet()
        val newStageIds = stages.map(FamilyGoalStage::id).toSet()
        val newStepIds = stages.flatMap { stage -> stage.steps.map(FamilyGoalStep::id) }.toSet()
        val removedStageIds = oldStageIds - newStageIds
        val removedStepIds = oldStepIds - newStepIds

        val tombstoneEntries = buildList {
            removedStageIds.forEach { stageId ->
                add(DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES to stageId)
            }
            removedStepIds.forEach { stepId ->
                add(DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS to stepId)
            }
        }
        if (tombstoneEntries.isNotEmpty()) {
            cloudDeletionLedger.recordDeletions(tombstoneEntries)
        }

        val normalizedGoal = normalizeGoal(goal, stages)
        val stageEntities = stages.sortedBy(FamilyGoalStage::position).map(FamilyGoalStage::toEntity)
        val stepEntities = stages.flatMap { stage ->
            stage.steps.sortedBy(FamilyGoalStep::position).map(FamilyGoalStep::toEntity)
        }
        familyGoalDao.upsertGoalWithPlan(normalizedGoal.toEntity(), stageEntities, stepEntities)

        val clearEntries = buildList {
            add(DeletedCloudDocumentCollections.FAMILY_GOALS to normalizedGoal.id)
            newStageIds.forEach { stageId ->
                add(DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES to stageId)
            }
            newStepIds.forEach { stepId ->
                add(DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS to stepId)
            }
        }
        cloudDeletionLedger.clearDeletions(clearEntries)
    }

    suspend fun upsert(goal: FamilyGoal) {
        familyGoalDao.upsertGoal(goal.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.FAMILY_GOALS, goal.id)
    }

    suspend fun upsertAll(
        goals: List<FamilyGoal>,
        stages: List<FamilyGoalStage>,
        steps: List<FamilyGoalStep>
    ) {
        familyGoalDao.upsertAll(
            goals.map(FamilyGoal::toEntity),
            stages.map(FamilyGoalStage::toEntity),
            steps.map(FamilyGoalStep::toEntity)
        )
    }

    suspend fun delete(goal: FamilyGoal) {
        val stageIds = familyGoalDao.getStageIdsByGoal(goal.id)
        val stepIds = familyGoalDao.getStepIdsByGoal(goal.id)
        cloudDeletionLedger.recordDeletions(
            buildList {
                add(DeletedCloudDocumentCollections.FAMILY_GOALS to goal.id)
                stageIds.forEach { stageId ->
                    add(DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES to stageId)
                }
                stepIds.forEach { stepId ->
                    add(DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS to stepId)
                }
            }
        )
        familyGoalDao.deleteGoal(goal.id)
    }

    suspend fun clear() {
        familyGoalDao.clearAll()
    }

    suspend fun toggleStep(goalId: String, stepId: String, isCompleted: Boolean) {
        familyGoalDao.setStepCompleted(stepId, isCompleted)
        recalculateProgress(goalId)
    }

    suspend fun completeNextSteps(goalId: String, count: Int): Int {
        if (count <= 0) return 0
        val nextSteps = familyGoalDao.findIncompleteStepIds(goalId).take(count)
        if (nextSteps.isEmpty()) return 0
        familyGoalDao.setStepsCompleted(nextSteps, true)
        recalculateProgress(goalId)
        return nextSteps.size
    }

    suspend fun markAllStepsCompleted(goalId: String) {
        val remaining = familyGoalDao.findIncompleteStepIds(goalId)
        if (remaining.isNotEmpty()) {
            familyGoalDao.setStepsCompleted(remaining, true)
        }
        recalculateProgress(goalId, FamilyGoalStatus.COMPLETED)
    }

    suspend fun updateStatus(goal: FamilyGoal, status: FamilyGoalStatus, updatedAt: Long) {
        val totalSteps = familyGoalDao.countSteps(goal.id)
        val total = if (totalSteps == 0) goal.targetPoints else totalSteps
        val completed = if (totalSteps == 0) goal.currentPoints else familyGoalDao.countCompletedSteps(goal.id)
        familyGoalDao.updateProgress(goal.id, total, completed.coerceAtMost(total), status.name, updatedAt)
    }

    private suspend fun recalculateProgress(goalId: String, forcedStatus: FamilyGoalStatus? = null) {
        val goal = familyGoalDao.getGoalById(goalId) ?: return
        val total = familyGoalDao.countSteps(goalId)
        if (total == 0) {
            return
        }
        val completed = familyGoalDao.countCompletedSteps(goalId).coerceAtMost(total)
        val currentStatus = runCatching { FamilyGoalStatus.valueOf(goal.status) }.getOrNull()
        val status = when {
            currentStatus == FamilyGoalStatus.ARCHIVED -> FamilyGoalStatus.ARCHIVED
            forcedStatus != null -> forcedStatus
            completed >= total -> FamilyGoalStatus.COMPLETED
            else -> FamilyGoalStatus.ACTIVE
        }
        val updatedAt = maxOf(goal.updatedAt, TimeUtils.now())
        familyGoalDao.updateProgress(goalId, total, completed, status.name, updatedAt)
    }

    private fun normalizeGoal(goal: FamilyGoal, stages: List<FamilyGoalStage>): FamilyGoal {
        val totalSteps = stages.sumOf(FamilyGoalStage::totalSteps)
        if (totalSteps == 0) {
            return goal
        }
        val completedSteps = stages.sumOf(FamilyGoalStage::completedSteps).coerceAtMost(totalSteps)
        val status = when {
            goal.status == FamilyGoalStatus.ARCHIVED -> FamilyGoalStatus.ARCHIVED
            completedSteps >= totalSteps -> FamilyGoalStatus.COMPLETED
            else -> FamilyGoalStatus.ACTIVE
        }
        return goal.copy(
            targetPoints = totalSteps,
            currentPoints = completedSteps,
            status = status
        )
    }
}

private fun StageWithSteps.toDomain(): FamilyGoalStage =
    stage.toDomain(steps)
