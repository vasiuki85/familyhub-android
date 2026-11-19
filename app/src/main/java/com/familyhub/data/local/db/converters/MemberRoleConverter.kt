package com.familyhub.data.local.db.converters

import androidx.room.TypeConverter
import com.familyhub.domain.model.MemberRole

/**
 * Конвертер для сохранения enum MemberRole в базе данных Room
 */
class MemberRoleConverter {
    @TypeConverter
    fun fromRole(role: MemberRole): String {
        return role.name
    }

    @TypeConverter
    fun toRole(roleName: String): MemberRole {
        return MemberRole.valueOf(roleName)
    }
}

