package com.familyhub.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.sync.SyncExclusiveLock
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.initDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_initialization"
)

/**
 * Performs the one-time, conservative cleanup of the legacy debug demo snapshot.
 *
 * New installs never receive demo members or demo content. Existing data is wiped only when the
 * entire four-member legacy signature is present. If authentication is already available, its
 * member is recreated immediately under the same sync lock; otherwise normal post-login bootstrap
 * creates it later.
 */
@Singleton
class InitializationRepository @Inject constructor(
    private val memberRepository: FamilyMemberRepository,
    private val localDataWiper: LocalDataWiper,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val syncExclusiveLock: SyncExclusiveLock,
    private val authenticatedMemberBootstrapCoordinator: AuthenticatedMemberBootstrapCoordinator,
    private val builtInRecipeSeeder: BuiltInRecipeSeeder,
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_LEGACY_DEMO_CLEANUP_COMPLETED =
            booleanPreferencesKey("legacy_demo_cleanup_completed")
        private val KEY_BUILTIN_RECIPES_V1_SEEDED =
            booleanPreferencesKey("builtin_recipes_v1_seeded")
    }

    suspend fun initializeIfNeeded() {
        syncExclusiveLock.withLock {
            val initializationSettings = context.initDataStore.data.first()
            val cleanupCompleted =
                initializationSettings[KEY_LEGACY_DEMO_CLEANUP_COMPLETED] == true
            val recipesSeeded =
                initializationSettings[KEY_BUILTIN_RECIPES_V1_SEEDED] == true
            if (cleanupCompleted && recipesSeeded) {
                return@withLock
            }

            val authenticatedUid = firebaseAuth.currentUser?.uid
                ?.takeIf { it.isNotBlank() }
            val syncSettings = syncSettingsDataStore.settingsFlow.first()
            if (
                syncSettings.restoreInProgress ||
                syncSettings.suppressAuthMemberBootstrap
            ) {
                return@withLock
            }

            if (!cleanupCompleted) {
                val members = memberRepository.getAllMembers().first()
                if (LegacyDemoFamilyDetector.matches(members, authenticatedUid)) {
                    localDataWiper.wipeAllFamilyData()
                    appSettingsDataStore.setCurrentUser("")
                    appSettingsDataStore.setActiveShoppingList("")
                    if (authenticatedUid != null) {
                        val bootstrapOutcome = authenticatedMemberBootstrapCoordinator
                            .ensureAuthenticatedMemberExistsLocked()
                        if (
                            bootstrapOutcome !is
                            AuthenticatedMemberBootstrapCoordinator.Outcome.MemberUpserted
                        ) {
                            return@withLock
                        }
                    }
                }
            }

            if (!recipesSeeded) {
                builtInRecipeSeeder.seedMissing()
            }

            context.initDataStore.edit { preferences ->
                preferences[KEY_LEGACY_DEMO_CLEANUP_COMPLETED] = true
                preferences[KEY_BUILTIN_RECIPES_V1_SEEDED] = true
            }
        }
    }
}
