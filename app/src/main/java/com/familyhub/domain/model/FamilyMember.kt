package com.familyhub.domain.model

data class FamilyMember(
    val id: String,            // UUID
    val displayName: String,   // ???
    val roleLabel: String,     // ??????? ????
    val avatarColor: String,   // ???? ???????
    val role: MemberRole = MemberRole.CHILD,
    val age: Int? = null,
    val birthDate: String? = null, // ISO yyyy-MM-dd
    val photoUrl: String? = null
)
