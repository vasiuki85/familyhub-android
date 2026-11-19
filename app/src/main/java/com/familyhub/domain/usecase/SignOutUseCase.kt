package com.familyhub.domain.usecase

import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.sync.SyncExclusiveLock
import com.familyhub.data.sync.SyncRepository
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val syncExclusiveLock: SyncExclusiveLock,
    private val syncRepository: SyncRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val firebaseAuth: FirebaseAuth
) {
    suspend operator fun invoke() {
        syncExclusiveLock.withLock {
            syncRepository.wipeAndResetForSignOutLocked()
            appSettingsDataStore.resetUserSession()
            firebaseAuth.signOut()
        }
    }
}
