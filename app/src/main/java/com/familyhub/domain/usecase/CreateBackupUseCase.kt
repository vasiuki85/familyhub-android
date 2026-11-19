package com.familyhub.domain.usecase

import com.familyhub.data.sync.SyncRepository
import java.io.File
import javax.inject.Inject

class CreateBackupUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(): File = syncRepository.createLocalBackup()
}
