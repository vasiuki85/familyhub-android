package com.familyhub.data.repository

import android.content.Context
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.sync.SyncExclusiveLock
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Serializes authenticated-member bootstrap under [SyncExclusiveLock] so restore cannot
 * race with a stale settings/members snapshot from Home or Settings.
 */
@Singleton
class AuthenticatedMemberBootstrapCoordinator @Inject constructor(
    private val syncExclusiveLock: SyncExclusiveLock,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val memberRepository: FamilyMemberRepository,
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context
) {
    sealed class Outcome {
        data object Skipped : Outcome()
        data object ClearedSuppression : Outcome()
        data class MemberUpserted(val memberId: String, val role: MemberRole) : Outcome()
    }

    suspend fun ensureAuthenticatedMemberExists(): Outcome {
        return syncExclusiveLock.withLock {
            ensureAuthenticatedMemberExistsLocked()
        }
    }

    /**
     * Same fresh-read bootstrap path for callers that already hold [SyncExclusiveLock].
     *
     * This avoids releasing the lock between a destructive local cleanup and recreating the
     * authenticated member. Calling this method without holding the shared lock is unsupported.
     */
    internal suspend fun ensureAuthenticatedMemberExistsLocked(): Outcome {
        val authUser = firebaseAuth.currentUser ?: return Outcome.Skipped
        val syncSettings = syncSettingsDataStore.settingsFlow.first()
        val members = memberRepository.getAllMembers().first()
        val membersContainUid = members.any { it.id == authUser.uid }
        return when (
            val action = AuthenticatedMemberBootstrapPolicy.resolve(
                authenticatedUid = authUser.uid,
                membersContainUid = membersContainUid,
                memberCount = members.size,
                accountId = syncSettings.accountId,
                localDataFamilyId = syncSettings.localDataFamilyId,
                suppressAuthMemberBootstrap = syncSettings.suppressAuthMemberBootstrap,
                restoreInProgress = syncSettings.restoreInProgress
            )
        ) {
            AuthenticatedMemberBootstrapPolicy.Action.NoOp -> Outcome.Skipped
            AuthenticatedMemberBootstrapPolicy.Action.ClearStaleSuppression -> {
                syncSettingsDataStore.setSuppressAuthMemberBootstrap(false)
                Outcome.ClearedSuppression
            }
            is AuthenticatedMemberBootstrapPolicy.Action.CreateMember -> {
                val member = buildMember(authUser, action.role)
                memberRepository.upsertMember(member)
                appSettingsDataStore.setCurrentUser(authUser.uid)
                Outcome.MemberUpserted(authUser.uid, action.role)
            }
        }
    }

    private fun buildMember(authUser: FirebaseUser, role: MemberRole): FamilyMember {
        val displayName = authUser.displayName
            ?.takeIf { it.isNotBlank() }
            ?: authUser.email
                ?.substringBefore('@')
                ?.takeIf { it.isNotBlank() }
            ?: context.getString(com.familyhub.R.string.home_unknown_member)
        return FamilyMember(
            id = authUser.uid,
            displayName = displayName,
            roleLabel = displayName,
            avatarColor = "#2196F3",
            role = role,
            age = null,
            birthDate = null,
            photoUrl = null
        )
    }
}
