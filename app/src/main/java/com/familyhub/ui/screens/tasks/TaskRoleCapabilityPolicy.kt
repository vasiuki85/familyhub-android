package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus

data class TaskRoleCapabilities(
    val canCreateTask: Boolean,
    val canChooseAssignee: Boolean,
    val canManageTaskConfiguration: Boolean,
)

object TaskRoleCapabilityPolicy {

    fun resolve(role: MemberRole?): TaskRoleCapabilities = when (role ?: MemberRole.PENDING) {
        MemberRole.ADMIN, MemberRole.PARENT -> TaskRoleCapabilities(
            canCreateTask = true,
            canChooseAssignee = true,
            canManageTaskConfiguration = true,
        )
        MemberRole.CHILD -> TaskRoleCapabilities(
            canCreateTask = true,
            canChooseAssignee = false,
            canManageTaskConfiguration = false,
        )
        MemberRole.PENDING -> TaskRoleCapabilities(
            canCreateTask = false,
            canChooseAssignee = false,
            canManageTaskConfiguration = false,
        )
    }

    fun resolveCreateAssignee(
        role: MemberRole?,
        currentUserId: String,
        requestedAssigneeId: String,
    ): String? {
        if (currentUserId.isBlank()) return null
        return when (role ?: MemberRole.PENDING) {
            MemberRole.ADMIN, MemberRole.PARENT -> requestedAssigneeId.takeIf { it.isNotBlank() }
            MemberRole.CHILD -> currentUserId
            MemberRole.PENDING -> null
        }
    }

    fun canChangeStatus(
        role: MemberRole?,
        currentUserId: String,
        task: Task,
        effectiveTab: TaskTab? = null,
    ): Boolean {
        if (currentUserId.isBlank() || effectiveTab == TaskTab.AWAITING) return false
        return when (role ?: MemberRole.PENDING) {
            MemberRole.ADMIN, MemberRole.PARENT -> true
            MemberRole.CHILD ->
                task.assignedToUserId == currentUserId && task.status != TaskStatus.DONE
            MemberRole.PENDING -> false
        }
    }

    fun childUpdateKeepsAuthorityFields(
        oldTask: Task,
        updatedTask: Task,
    ): Boolean =
        oldTask.createdByUserId == updatedTask.createdByUserId &&
            oldTask.assignedToUserId == updatedTask.assignedToUserId &&
            oldTask.rewardPoints == updatedTask.rewardPoints &&
            oldTask.isPrivate == updatedTask.isPrivate &&
            oldTask.status == updatedTask.status &&
            oldTask.requiresApproval == updatedTask.requiresApproval &&
            oldTask.completionCycle == updatedTask.completionCycle
}
