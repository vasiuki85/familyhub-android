package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.domain.model.FamilyMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FamilyMemberRepository @Inject constructor(
    private val dao: FamilyMemberDao
) {
    fun getAllMembers(): Flow<List<FamilyMember>> {
        return dao.getAll().map { it.map { entity -> entity.toDomain() } }
    }

    suspend fun getMemberById(id: String): FamilyMember? {
        return dao.getById(id)?.toDomain()
    }

    suspend fun upsertMember(member: FamilyMember) {
        dao.upsert(member.toEntity())
    }

    suspend fun upsertMembers(members: List<FamilyMember>) {
        dao.upsertAll(members.map { it.toEntity() })
    }

    suspend fun deleteMember(member: FamilyMember) {
        dao.delete(member.toEntity())
    }
}
