package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.TaskCompletionDao
import com.familyhub.data.local.db.dao.UserAchievementDao
import com.familyhub.data.local.db.dao.UserScoreDao
import com.familyhub.data.local.db.dao.UserWalletDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.domain.model.TaskCompletion
import com.familyhub.domain.model.UserAchievement
import com.familyhub.domain.model.UserScore
import com.familyhub.domain.model.UserWallet
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScoreRepository @Inject constructor(
    private val userScoreDao: UserScoreDao,
    private val taskCompletionDao: TaskCompletionDao,
    private val userAchievementDao: UserAchievementDao,
    private val userWalletDao: UserWalletDao
) {
    fun getScoresForWeek(weekStartDate: Long): Flow<List<UserScore>> {
        return userScoreDao.getScoresForWeek(weekStartDate).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getScoresFromWeek(weekStartDate: Long): Flow<List<UserScore>> {
        return userScoreDao.getScoresFromWeek(weekStartDate).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getScoreHistoryForUser(userId: String, fromWeekStart: Long): Flow<List<UserScore>> {
        return userScoreDao.getScoresForUserFromWeek(userId, fromWeekStart).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /** Active (unreversed) completions for statistics, UI, and reports. */
    fun getAllActiveTaskCompletions(): Flow<List<TaskCompletion>> {
        return taskCompletionDao.getAllActiveCompletions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getActiveCompletionsForUser(userId: String): Flow<List<TaskCompletion>> {
        return taskCompletionDao.getActiveCompletionsForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getTaskCompletionsBetweenWeeks(fromWeek: Long, toWeek: Long): Flow<List<TaskCompletion>> {
        return taskCompletionDao.getActiveCompletionsBetweenWeeks(fromWeek, toWeek).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /** Full completion ledger including reversed rows — export/import/sync only. */
    fun getCompletionLedger(): Flow<List<TaskCompletion>> {
        return taskCompletionDao.getAllCompletions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    @Deprecated("Use getCompletionLedger() for export/sync or getAllActiveTaskCompletions() for statistics")
    fun getAllTaskCompletions(): Flow<List<TaskCompletion>> = getCompletionLedger()

    fun getAchievementsForUser(userId: String): Flow<List<UserAchievement>> {
        return userAchievementDao.getAchievementsForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getAllAchievements(): Flow<List<UserAchievement>> {
        return userAchievementDao.getAllAchievements().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getWalletForUser(userId: String): Flow<UserWallet> {
        return userWalletDao.observe(userId).map { entity ->
            entity?.toDomain() ?: UserWallet(userId = userId, balance = 0)
        }
    }

    fun getAllWallets(): Flow<List<UserWallet>> {
        return userWalletDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun trySpendPoints(userId: String, amount: Int): Boolean {
        if (amount <= 0) {
            return true
        }
        val timestamp = System.currentTimeMillis()
        val rowsUpdated = userWalletDao.decreaseBalance(userId, amount, timestamp)
        return rowsUpdated > 0
    }

    suspend fun getUserScoreForWeek(userId: String, weekStartDate: Long): UserScore? {
        return userScoreDao.getUserScoreForWeek(userId, weekStartDate)?.toDomain()
    }

    suspend fun upsertScore(score: UserScore) {
        userScoreDao.upsert(score.toEntity())
    }
}
