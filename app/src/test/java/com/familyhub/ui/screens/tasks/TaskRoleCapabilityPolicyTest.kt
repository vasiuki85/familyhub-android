package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRoleCapabilityPolicyTest {

    @Test
    fun roleMatrix_isFailClosedAndKeepsParentManagement() {
        assertTrue(TaskRoleCapabilityPolicy.resolve(MemberRole.ADMIN).canManageTaskConfiguration)
        assertTrue(TaskRoleCapabilityPolicy.resolve(MemberRole.PARENT).canChooseAssignee)

        val child = TaskRoleCapabilityPolicy.resolve(MemberRole.CHILD)
        assertTrue(child.canCreateTask)
        assertFalse(child.canChooseAssignee)
        assertFalse(child.canManageTaskConfiguration)

        val pending = TaskRoleCapabilityPolicy.resolve(MemberRole.PENDING)
        assertFalse(pending.canCreateTask)
        assertFalse(TaskRoleCapabilityPolicy.resolve(null).canCreateTask)
    }

    @Test
    fun createAssignee_childIsForcedToSelfAndPendingIsRejected() {
        assertEquals(
            "child",
            TaskRoleCapabilityPolicy.resolveCreateAssignee(
                role = MemberRole.CHILD,
                currentUserId = "child",
                requestedAssigneeId = "other",
            ),
        )
        assertNull(
            TaskRoleCapabilityPolicy.resolveCreateAssignee(
                role = MemberRole.PENDING,
                currentUserId = "pending",
                requestedAssigneeId = "other",
            ),
        )
        assertEquals(
            "other",
            TaskRoleCapabilityPolicy.resolveCreateAssignee(
                role = MemberRole.PARENT,
                currentUserId = "parent",
                requestedAssigneeId = "other",
            ),
        )
    }

    @Test
    fun statusMatrix_disablesAwaitingPendingForeignChildAndChildUndo() {
        val assignedTodo = task(assignedTo = "child", status = TaskStatus.TODO)
        val assignedWaiting = task(assignedTo = "child", status = TaskStatus.WAITING_FOR_APPROVAL)
        val assignedDone = task(assignedTo = "child", status = TaskStatus.DONE)

        assertTrue(
            TaskRoleCapabilityPolicy.canChangeStatus(
                MemberRole.CHILD,
                "child",
                assignedTodo,
                TaskTab.MY,
            ),
        )
        assertTrue(
            TaskRoleCapabilityPolicy.canChangeStatus(
                MemberRole.CHILD,
                "child",
                assignedWaiting,
                TaskTab.MY,
            ),
        )
        assertFalse(
            TaskRoleCapabilityPolicy.canChangeStatus(
                MemberRole.CHILD,
                "child",
                assignedDone,
                TaskTab.MY,
            ),
        )
        assertFalse(
            TaskRoleCapabilityPolicy.canChangeStatus(
                MemberRole.CHILD,
                "other",
                assignedTodo,
                TaskTab.ALL,
            ),
        )
        assertFalse(
            TaskRoleCapabilityPolicy.canChangeStatus(
                MemberRole.PARENT,
                "parent",
                assignedWaiting,
                TaskTab.AWAITING,
            ),
        )
        assertFalse(
            TaskRoleCapabilityPolicy.canChangeStatus(
                MemberRole.PENDING,
                "pending",
                assignedTodo,
                TaskTab.MY,
            ),
        )
    }

    @Test
    fun childUpdateAuthorityFields_mustRemainUnchanged() {
        val original = task(assignedTo = "child", status = TaskStatus.TODO)

        assertTrue(
            TaskRoleCapabilityPolicy.childUpdateKeepsAuthorityFields(
                original,
                original.copy(title = "Updated"),
            ),
        )
        assertFalse(
            TaskRoleCapabilityPolicy.childUpdateKeepsAuthorityFields(
                original,
                original.copy(assignedToUserId = "other"),
            ),
        )
        assertFalse(
            TaskRoleCapabilityPolicy.childUpdateKeepsAuthorityFields(
                original,
                original.copy(isPrivate = true),
            ),
        )
    }

    private fun task(
        assignedTo: String,
        status: TaskStatus,
    ): Task = Task(
        id = "task",
        title = "Task",
        description = null,
        createdByUserId = "child",
        assignedToUserId = assignedTo,
        dueDate = null,
        repeatRule = RepeatRule.NONE,
        status = status,
        createdAt = 1L,
    )
}
