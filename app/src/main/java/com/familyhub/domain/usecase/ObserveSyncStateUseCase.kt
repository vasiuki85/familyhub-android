package com.familyhub.domain.usecase

import com.familyhub.data.sync.SyncRepository
import com.familyhub.domain.model.SyncState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObserveSyncStateUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {
    operator fun invoke(): StateFlow<SyncState> = syncRepository.observeSyncState()
}
