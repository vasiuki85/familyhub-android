package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.MenuMutationForbiddenException

internal object MenuActorAuthorization {

    suspend fun requireActorMember(
        familyMemberDao: FamilyMemberDao,
        actorUserId: String
    ): FamilyMemberEntity {
        return familyMemberDao.getById(actorUserId)
            ?: throw MenuMutationForbiddenException("Member not found")
    }

    suspend fun requireCanManageMenu(
        familyMemberDao: FamilyMemberDao,
        actorUserId: String
    ): FamilyMemberEntity {
        val member = requireActorMember(familyMemberDao, actorUserId)
        when (member.role) {
            MemberRole.ADMIN, MemberRole.PARENT -> return member
            MemberRole.CHILD, MemberRole.PENDING ->
                throw MenuMutationForbiddenException("Role cannot manage menu")
            else -> throw MenuMutationForbiddenException("Role cannot manage menu")
        }
    }
}
