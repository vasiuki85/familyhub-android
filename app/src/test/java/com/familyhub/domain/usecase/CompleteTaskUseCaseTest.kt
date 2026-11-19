package com.familyhub.domain.usecase

import com.familyhub.data.repository.TaskCompletionTransitionCoordinator
import com.familyhub.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteTaskUseCaseTest {

    private val coordinator: TaskCompletionTransitionCoordinator = mockk()
    private val useCase = CompleteTaskUseCase(coordinator)

    @Test
    fun `delegates to coordinator with actor user id`() = runBlocking {
        val task = Task(
            id = "t1",
            title = "Task",
            description = null,
            status = TaskStatus.TODO,
            requiresApproval = false,
            assignedToUserId = "adult",
            createdByUserId = "adult",
            createdAt = 0L,
            repeatRule = RepeatRule.NONE,
            dueDate = null
        )
        val expected = TaskTransitionResult(
            taskId = "t1",
            previousStatus = TaskStatus.TODO,
            newStatus = TaskStatus.DONE,
            scoreImpact = ScoreImpact(10, 0, 0, 10, emptyList()),
            mutated = true,
            repeatCreated = false
        )
        coEvery {
            coordinator.complete("t1", "adult", any())
        } returns expected

        val result = useCase(task, "adult")

        assertEquals(expected, result)
        coVerify { coordinator.complete("t1", "adult", any()) }
    }

    @Test
    fun `waiting transition returns zero score impact from coordinator`() = runBlocking {
        val task = Task(
            id = "t1",
            title = "Task",
            description = null,
            status = TaskStatus.TODO,
            requiresApproval = true,
            assignedToUserId = "child",
            createdByUserId = "parent",
            createdAt = 0L,
            repeatRule = RepeatRule.NONE,
            dueDate = null
        )
        val expected = TaskTransitionResult(
            taskId = "t1",
            previousStatus = TaskStatus.TODO,
            newStatus = TaskStatus.WAITING_FOR_APPROVAL,
            scoreImpact = ScoreImpact(0, 0, 0, 0, emptyList()),
            mutated = true,
            repeatCreated = false
        )
        coEvery {
            coordinator.complete("t1", "child", any())
        } returns expected

        val result = useCase(task, "child")

        assertEquals(TaskStatus.WAITING_FOR_APPROVAL, result.newStatus)
        assertEquals(0, result.scoreImpact.totalDelta)
        assertTrue(result.mutated)
    }
}
