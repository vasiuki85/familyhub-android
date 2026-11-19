package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.familyhub.data.local.db.converters.MemberRoleConverter
import com.familyhub.domain.model.MemberRole

@Entity(tableName = "family_members")
@TypeConverters(MemberRoleConverter::class)
data class FamilyMemberEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val roleLabel: String,
    val avatarColor: String,
    val role: MemberRole = MemberRole.CHILD,
    val age: Int? = null,
    val birthDate: String? = null,
    val photoUrl: String? = null
)
