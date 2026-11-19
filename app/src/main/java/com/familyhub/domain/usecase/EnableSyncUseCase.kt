package com.familyhub.domain.usecase

import com.familyhub.data.sync.SyncRepository
import javax.inject.Inject

class EnableSyncUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(accountId: String, deviceId: String) {
        syncRepository.enableSync(accountId, deviceId)
    }
}
