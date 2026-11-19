package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.TaskCompletionDao
import com.familyhub.data.local.entity.TaskCompletionEntity
import com.familyhub.domain.model.TaskType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreRepositoryActiveLedgerTest {

    private val taskCompletionDao: TaskCompletionDao = mockk()

    private val repository = ScoreRepository(
        userScoreDao = mockk(relaxed = true),
        taskCompletionDao = taskCompletionDao,
        userAchievementDao = mockk(relaxed = true),
        userWalletDao = mockk(relaxed = true)
    )

    @Test
    fun getAllActiveTaskCompletions_excludesReversedRows() = runBlocking {
        val active = completion(id = "active", reversedAt = null)
        every { taskCompletionDao.getAllActiveCompletions() } returns flowOf(listOf(active))

        val result = repository.getAllActiveTaskCompletions()

        result.collect { completions ->
            assertEquals(1, completions.size)
            assertEquals("active", completions.single().id)
        }
    }

    @Test
    fun getCompletionLedger_includesReversedRows() = runBlocking {
        val active = completion(id = "active", reversedAt = null)
        val reversed = completion(id = "reversed", reversedAt = 100L)
        every { taskCompletionDao.getAllCompletions() } returns flowOf(listOf(active, reversed))

        val result = repository.getCompletionLedger()

        result.collect { completions ->
            assertEquals(2, completions.size)
        }
    }

    private fun completion(id: String, reversedAt: Long?) = TaskCompletionEntity(
        id = id,
        taskId = "task",
        userId = "user",
        taskType = TaskType.ROUTINE.name,
        completionTime = 1L,
        weekStartDate = 1L,
        basePoints = 1,
        bonusPoints = 0,
        penaltyPoints = 0,
        totalPoints = 1,
        reversedAt = reversedAt,
        reversedByUserId = if (reversedAt != null) "parent" else null,
        updatedAt = 1L
    )
}
