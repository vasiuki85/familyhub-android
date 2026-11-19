package com.familyhub.data.repository

import android.content.Context
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.sync.SyncExclusiveLock
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.SyncSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthenticatedMemberBootstrapCoordinatorTest {

    private val lock = SyncExclusiveLock()
    private val syncSettings = mockk<SyncSettingsDataStore>(relaxed = true)
    private val appSettings = mockk<AppSettingsDataStore>(relaxed = true)
    private val members = mockk<FamilyMemberRepository>(relaxed = true)
    private val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private lateinit var coordinator: AuthenticatedMemberBootstrapCoordinator

    @Before
    fun setUp() {
        every { context.getString(any()) } returns "User"
        coordinator = AuthenticatedMemberBootstrapCoordinator(
            syncExclusiveLock = lock,
            syncSettingsDataStore = syncSettings,
            appSettingsDataStore = appSettings,
            memberRepository = members,
            firebaseAuth = firebaseAuth,
            context = context
        )
    }

    @Test
    fun parallelConsumers_upsertOnce() = runBlocking {
        val authUser = mockk<FirebaseUser>()
        every { authUser.uid } returns "uid-1"
        every { authUser.displayName } returns "User"
        every { authUser.email } returns null
        every { firebaseAuth.currentUser } returns authUser
        val membersList = mutableListOf<FamilyMember>()
        every { members.getAllMembers() } answers {
            flowOf(membersList.toList())
        }
        every { syncSettings.settingsFlow } returns flowOf(SyncSettings())
        val upserts = AtomicInteger(0)
        coEvery { members.upsertMember(any()) } coAnswers {
            upserts.incrementAndGet()
            membersList.add(firstArg())
        }
        coEvery { appSettings.setCurrentUser(any()) } just runs

        (1..2).map {
            async { coordinator.ensureAuthenticatedMemberExists() }
        }.awaitAll()

        assertEquals(1, upserts.get())
    }

    @Test
    fun restoreSetsSuppressionWhileBootstrapWaits_noMemberWritten() = runBlocking {
        val authUser = mockk<FirebaseUser>()
        every { authUser.uid } returns "uid-1"
        every { authUser.displayName } returns "User"
        every { authUser.email } returns null
        every { firebaseAuth.currentUser } returns authUser
        every { members.getAllMembers() } returns flowOf(emptyList())

        var suppression = false
        every { syncSettings.settingsFlow } answers {
            flowOf(
                SyncSettings(
                    suppressAuthMemberBootstrap = suppression,
                    restoreInProgress = suppression
                )
            )
        }

        val restoreStarted = CompletableDeferred<Unit>()
        val restoreCanFinish = CompletableDeferred<Unit>()

        val restoreJob = launch {
            lock.withLock {
                suppression = true
                restoreStarted.complete(Unit)
                restoreCanFinish.await()
            }
        }

        restoreStarted.await()

        val bootstrapDeferred = async {
            coordinator.ensureAuthenticatedMemberExists()
        }

        restoreCanFinish.complete(Unit)
        restoreJob.join()

        val outcome = bootstrapDeferred.await()
        assertTrue(outcome is AuthenticatedMemberBootstrapCoordinator.Outcome.Skipped)
        coVerify(exactly = 0) { members.upsertMember(any<FamilyMember>()) }
    }

    @Test
    fun signOutHoldsLockUntilFirebaseSignedOut_bootstrapSeesNullUser() = runBlocking {
        val authUser = mockk<FirebaseUser>()
        every { authUser.uid } returns "uid-1"
        every { authUser.displayName } returns "User"
        every { authUser.email } returns null
        var currentUser: FirebaseUser? = authUser
        every { firebaseAuth.currentUser } answers { currentUser }
        every { members.getAllMembers() } returns flowOf(emptyList())
        every { syncSettings.settingsFlow } returns flowOf(SyncSettings())

        val signOutStarted = CompletableDeferred<Unit>()
        val signOutCanFinish = CompletableDeferred<Unit>()

        val signOutJob = launch {
            lock.withLock {
                currentUser = null
                signOutStarted.complete(Unit)
                signOutCanFinish.await()
            }
        }

        signOutStarted.await()

        val bootstrapDeferred = async {
            coordinator.ensureAuthenticatedMemberExists()
        }

        delay(50)
        coVerify(exactly = 0) { members.upsertMember(any<FamilyMember>()) }

        signOutCanFinish.complete(Unit)
        signOutJob.join()

        val outcome = bootstrapDeferred.await()
        assertTrue(outcome is AuthenticatedMemberBootstrapCoordinator.Outcome.Skipped)
        coVerify(exactly = 0) { members.upsertMember(any<FamilyMember>()) }
    }
}
