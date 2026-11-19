package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.domain.model.AccountingTransitionForbiddenException
import com.familyhub.domain.model.MemberRole

internal object AccountingActorAuthorization {
    suspend fun requireAdminOrParentActor(
        familyMemberDao: FamilyMemberDao,
        actorUserId: String,
        message: String
    ) {
        val member = familyMemberDao.getById(actorUserId)
            ?: throw AccountingTransitionForbiddenException("$message: member not found")
        if (member.role != MemberRole.ADMIN && member.role != MemberRole.PARENT) {
            throw AccountingTransitionForbiddenException(message)
        }
    }
}
