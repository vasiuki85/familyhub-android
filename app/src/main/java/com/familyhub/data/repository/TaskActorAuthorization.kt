package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskTransitionForbiddenException

internal object TaskActorAuthorization {
    suspend fun requireActorMember(
        familyMemberDao: FamilyMemberDao,
        actorUserId: String
    ): FamilyMemberEntity {
        return familyMemberDao.getById(actorUserId)
            ?: throw TaskTransitionForbiddenException("Member not found")
    }

    suspend fun requireCanComplete(
        familyMemberDao: FamilyMemberDao,
        actorUserId: String,
        task: Task
    ): FamilyMemberEntity {
        val member = requireActorMember(familyMemberDao, actorUserId)
        when (member.role) {
            MemberRole.ADMIN, MemberRole.PARENT -> Unit
            MemberRole.CHILD -> {
                if (task.assignedToUserId != actorUserId) {
                    throw TaskTransitionForbiddenException("Child cannot complete another member's task")
                }
            }
            else -> throw TaskTransitionForbiddenException("Role cannot complete tasks")
        }
        return member
    }

    fun needsApproval(member: FamilyMemberEntity, task: Task): Boolean {
        return task.requiresApproval || member.role == MemberRole.CHILD
    }

    suspend fun requireAdminOrParent(
        familyMemberDao: FamilyMemberDao,
        actorUserId: String,
        message: String
    ) {
        val member = requireActorMember(familyMemberDao, actorUserId)
        if (member.role != MemberRole.ADMIN && member.role != MemberRole.PARENT) {
            throw TaskTransitionForbiddenException(message)
        }
    }

    suspend fun requireCanCancelWaiting(
        familyMemberDao: FamilyMemberDao,
        actorUserId: String,
        task: Task
    ) {
        val member = requireActorMember(familyMemberDao, actorUserId)
        when (member.role) {
            MemberRole.ADMIN, MemberRole.PARENT -> Unit
            MemberRole.CHILD -> {
                if (task.assignedToUserId != actorUserId) {
                    throw TaskTransitionForbiddenException("Child cannot cancel waiting for another member's task")
                }
            }
            else -> throw TaskTransitionForbiddenException("Role cannot cancel waiting tasks")
        }
    }
}
