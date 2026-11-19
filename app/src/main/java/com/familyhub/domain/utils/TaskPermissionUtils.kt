package com.familyhub.domain.utils

import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.Task

/**
 * Утилиты для проверки прав на редактирование/удаление задач по роли пользователя.
 */
fun Task.canEdit(userId: String, userRole: MemberRole): Boolean {
    return when (userRole) {
        MemberRole.ADMIN, MemberRole.PARENT -> true
        MemberRole.CHILD -> createdByUserId == userId
        MemberRole.PENDING -> false
    }
}

fun Task.canDelete(userId: String, userRole: MemberRole): Boolean {
    return when (userRole) {
        MemberRole.ADMIN, MemberRole.PARENT -> true
        MemberRole.CHILD -> createdByUserId == userId
        MemberRole.PENDING -> false
    }
}
