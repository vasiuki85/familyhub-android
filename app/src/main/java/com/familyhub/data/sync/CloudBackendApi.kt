package com.familyhub.data.sync

import com.familyhub.data.export.ExportData
import com.familyhub.data.menu.cloud.MenuCloudUploadContext
import com.familyhub.domain.model.MemberRole

interface CloudBackendApi {
    suspend fun uploadSnapshot(
        accountId: String,
        snapshot: ExportData,
        isMemberWriteAllowed: Boolean,
        selfMember: com.familyhub.data.export.ExportMember? = null,
        baseSnapshot: ExportData? = null,
        menuUploadContext: MenuCloudUploadContext? = null,
        writerRole: MemberRole? = null,
    )
    suspend fun upsertSelfMember(accountId: String, member: com.familyhub.data.export.ExportMember)
    suspend fun deleteMember(accountId: String, memberId: String)
    suspend fun downloadSnapshot(accountId: String): ExportData?
}
