package com.familyhub.domain.usecase

import com.familyhub.data.sync.SyncRepository
import javax.inject.Inject

class PauseSyncUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke() {
        syncRepository.pauseSync()
    }
}
