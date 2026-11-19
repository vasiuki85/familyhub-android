package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.familyhub.data.local.entity.FamilyGoalEntity
import com.familyhub.data.local.entity.FamilyGoalStageEntity
import com.familyhub.data.local.entity.FamilyGoalStepEntity
import com.familyhub.data.local.entity.FamilyGoalWithStages
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyGoalDao {
    @Transaction
    @Query(
        "SELECT * FROM family_goals ORDER BY " +
            "CASE status WHEN 'ACTIVE' THEN 0 WHEN 'COMPLETED' THEN 1 ELSE 2 END, " +
            "CASE WHEN deadlineAt IS NULL THEN 1 ELSE 0 END, deadlineAt ASC, updatedAt DESC"
    )
    fun getAllWithStages(): Flow<List<FamilyGoalWithStages>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: FamilyGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoals(goals: List<FamilyGoalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStages(stages: List<FamilyGoalStageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<FamilyGoalStepEntity>)

    @Query("DELETE FROM goal_stages WHERE goalId = :goalId")
    suspend fun deleteStagesByGoal(goalId: String)

    @Query("DELETE FROM goal_steps WHERE goalId = :goalId")
    suspend fun deleteStepsByGoal(goalId: String)

    @Transaction
    suspend fun upsertGoalWithPlan(
        goal: FamilyGoalEntity,
        stages: List<FamilyGoalStageEntity>,
        steps: List<FamilyGoalStepEntity>
    ) {
        upsertGoal(goal)
        deleteStagesByGoal(goal.id)
        deleteStepsByGoal(goal.id)
        if (stages.isNotEmpty()) {
            insertStages(stages)
        }
        if (steps.isNotEmpty()) {
            insertSteps(steps)
        }
    }

    @Query("SELECT id FROM goal_stages WHERE goalId = :goalId")
    suspend fun getStageIdsByGoal(goalId: String): List<String>

    @Query("SELECT id FROM goal_steps WHERE goalId = :goalId")
    suspend fun getStepIdsByGoal(goalId: String): List<String>

    @Query("SELECT * FROM family_goals WHERE id = :goalId LIMIT 1")
    suspend fun getGoalById(goalId: String): FamilyGoalEntity?

    @Query("DELETE FROM family_goals WHERE id = :id")
    suspend fun deleteGoalById(id: String)

    @Transaction
    suspend fun deleteGoal(id: String) {
        deleteGoalById(id)
    }

    @Query("DELETE FROM family_goals")
    suspend fun clearGoals()

    @Transaction
    suspend fun clearAll() {
        clearGoals()
    }

    @Transaction
    suspend fun upsertAll(
        goals: List<FamilyGoalEntity>,
        stages: List<FamilyGoalStageEntity>,
        steps: List<FamilyGoalStepEntity>
    ) {
        clearAll()
        if (goals.isNotEmpty()) {
            upsertGoals(goals)
        }
        if (stages.isNotEmpty()) {
            insertStages(stages)
        }
        if (steps.isNotEmpty()) {
            insertSteps(steps)
        }
    }

    @Query("SELECT COUNT(*) FROM goal_steps WHERE goalId = :goalId")
    suspend fun countSteps(goalId: String): Int

    @Query("SELECT COUNT(*) FROM goal_steps WHERE goalId = :goalId AND isCompleted = 1")
    suspend fun countCompletedSteps(goalId: String): Int

    @Query("SELECT id FROM goal_steps WHERE goalId = :goalId AND isCompleted = 0 ORDER BY position")
    suspend fun findIncompleteStepIds(goalId: String): List<String>

    @Query("UPDATE goal_steps SET isCompleted = :isCompleted WHERE id = :stepId")
    suspend fun setStepCompleted(stepId: String, isCompleted: Boolean)

    @Query("UPDATE goal_steps SET isCompleted = :isCompleted WHERE id IN (:stepIds)")
    suspend fun setStepsCompleted(stepIds: List<String>, isCompleted: Boolean)

    @Query(
        "UPDATE family_goals SET currentPoints = :completed, targetPoints = :total, status = :status, updatedAt = :updatedAt WHERE id = :goalId"
    )
    suspend fun updateProgress(goalId: String, total: Int, completed: Int, status: String, updatedAt: Long)
}
