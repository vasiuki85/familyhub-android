package com.familyhub.domain.usecase

import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.sync.SyncExclusiveLock
import com.familyhub.data.sync.SyncRepository
import com.google.firebase.auth.FirebaseAuth
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SignOutUseCaseTest {

    private val lock = SyncExclusiveLock()
    private val syncRepository = mockk<SyncRepository>(relaxed = true)
    private val appSettingsDataStore = mockk<AppSettingsDataStore>(relaxed = true)
    private val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
    private val useCase = SignOutUseCase(
        syncExclusiveLock = lock,
        syncRepository = syncRepository,
        appSettingsDataStore = appSettingsDataStore,
        firebaseAuth = firebaseAuth
    )

    @Test
    fun invoke_wipesResetsSessionAndSignsOutUnderSharedLock() = runTest {
        coEvery { syncRepository.wipeAndResetForSignOutLocked() } just Runs
        coEvery { appSettingsDataStore.resetUserSession() } just Runs
        every { firebaseAuth.signOut() } just Runs

        useCase()

        coVerifyOrder {
            syncRepository.wipeAndResetForSignOutLocked()
            appSettingsDataStore.resetUserSession()
            firebaseAuth.signOut()
        }
    }

    @Test
    fun invoke_wipeFailureDoesNotResetSessionOrSignOutFirebase() = runTest {
        coEvery { syncRepository.wipeAndResetForSignOutLocked() } throws IllegalStateException("wipe failed")
        coEvery { appSettingsDataStore.resetUserSession() } just Runs
        every { firebaseAuth.signOut() } just Runs

        val result = runCatching { useCase() }

        assert(result.isFailure)
        coVerify(exactly = 1) { syncRepository.wipeAndResetForSignOutLocked() }
        coVerify(exactly = 0) { appSettingsDataStore.resetUserSession() }
        verify(exactly = 0) { firebaseAuth.signOut() }
    }
}
