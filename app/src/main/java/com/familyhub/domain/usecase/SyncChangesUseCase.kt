package com.familyhub.domain.usecase

import com.familyhub.data.sync.SyncRepository
import javax.inject.Inject

class SyncChangesUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(force: Boolean = false) {
        syncRepository.syncNow(force)
    }
}
