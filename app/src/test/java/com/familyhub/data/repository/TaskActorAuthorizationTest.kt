package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskTransitionForbiddenException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskActorAuthorizationTest {

    private val familyMemberDao: FamilyMemberDao = mockk()
    private val task = Task(
        id = "task-1",
        title = "Task",
        description = null,
        createdByUserId = "parent",
        assignedToUserId = "child",
        dueDate = null,
        repeatRule = RepeatRule.NONE,
        status = TaskStatus.TODO,
        createdAt = 0L
    )

    @Test
    fun adminCanCompleteAnyTask() {
        runBlocking {
            coEvery { familyMemberDao.getById("admin") } returns member("admin", MemberRole.ADMIN)
            TaskActorAuthorization.requireCanComplete(familyMemberDao, "admin", task)
        }
    }

    @Test
    fun parentCanCompleteAnyTask() {
        runBlocking {
            coEvery { familyMemberDao.getById("parent") } returns member("parent", MemberRole.PARENT)
            TaskActorAuthorization.requireCanComplete(familyMemberDao, "parent", task)
        }
    }

    @Test
    fun assignedChildCanCompleteOwnTask() {
        runBlocking {
            coEvery { familyMemberDao.getById("child") } returns member("child", MemberRole.CHILD)
            TaskActorAuthorization.requireCanComplete(familyMemberDao, "child", task)
        }
    }

    @Test
    fun foreignChildCannotComplete() {
        runBlocking {
            coEvery { familyMemberDao.getById("other") } returns member("other", MemberRole.CHILD)
            assertThrows(TaskTransitionForbiddenException::class.java) {
                runBlocking {
                    TaskActorAuthorization.requireCanComplete(familyMemberDao, "other", task)
                }
            }
        }
    }

    @Test
    fun pendingCannotComplete() {
        runBlocking {
            coEvery { familyMemberDao.getById("pending") } returns member("pending", MemberRole.PENDING)
            assertThrows(TaskTransitionForbiddenException::class.java) {
                runBlocking {
                    TaskActorAuthorization.requireCanComplete(familyMemberDao, "pending", task)
                }
            }
        }
    }

    @Test
    fun missingMemberCannotComplete() {
        runBlocking {
            coEvery { familyMemberDao.getById("missing") } returns null
            assertThrows(TaskTransitionForbiddenException::class.java) {
                runBlocking {
                    TaskActorAuthorization.requireCanComplete(familyMemberDao, "missing", task)
                }
            }
        }
    }

    @Test
    fun childNeedsApproval() {
        val child = member("child", MemberRole.CHILD)
        assertEquals(true, TaskActorAuthorization.needsApproval(child, task))
    }

    @Test
    fun parentDoesNotNeedApprovalByRole() {
        val parent = member("parent", MemberRole.PARENT)
        assertEquals(false, TaskActorAuthorization.needsApproval(parent, task.copy(requiresApproval = false)))
    }

    private fun member(id: String, role: MemberRole) = FamilyMemberEntity(
        id = id,
        displayName = id,
        roleLabel = id,
        avatarColor = "#000000",
        role = role
    )
}
