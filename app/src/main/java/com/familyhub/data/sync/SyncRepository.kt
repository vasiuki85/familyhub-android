package com.familyhub.data.sync

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.familyhub.R
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import com.familyhub.data.export.DataExportService
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportTempFileFactory
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportMember
import com.familyhub.data.importer.DataImportService
import com.familyhub.data.sync.logic.SyncMerger
import com.familyhub.data.datastore.DeletedCloudDocument
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.datastore.filterRemoteActiveDeletedDocuments
import com.familyhub.data.datastore.filterPreMergeConfirmableDeletedDocuments
import com.familyhub.data.datastore.filterMenuDeletedDocuments
import com.familyhub.data.datastore.groupDeletedDocumentIdsByCollection
import com.familyhub.data.datastore.remoteActiveDeletedDocumentIdsByCollection
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.LocalDataWiper
import com.familyhub.data.restore.RestoreBlockedException
import com.familyhub.domain.model.FamilyBindingMode
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.SyncState
import com.familyhub.domain.utils.LocalDataFamilySwitchPolicy
import com.familyhub.domain.utils.SyncJoinPolicy
import com.familyhub.data.menu.firestore.MenuFirestoreDocumentCodecException
import com.familyhub.data.menu.cloud.MenuCloudSnapshotInvalidException
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Singleton
class SyncRepository @Inject constructor(
    private val exportService: DataExportService,
    private val importService: DataImportService,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val backendApi: CloudBackendApi,
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val firestore: FirebaseFirestore,
    private val cloudSyncScheduler: CloudSyncScheduler,
    private val memberRepository: com.familyhub.data.repository.FamilyMemberRepository,
    private val syncMerger: SyncMerger,
    private val localDataWiper: LocalDataWiper,
    private val syncExclusiveLock: SyncExclusiveLock,
    private val syncChangeTracker: SyncChangeTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    init {
        scope.launch {
            syncSettingsDataStore.settingsFlow.collectLatest { settings ->
                _state.update { current ->
                    SyncStateReducer.applyHydratedSettings(current, settings)
                }
                if (settings.isEnabled && !settings.restoreInProgress) {
                    cloudSyncScheduler.schedulePeriodicSync()
                } else {
                    // Crash recovery / paused / restore barrier: never leave a periodic worker armed.
                    cloudSyncScheduler.cancelPeriodicSync()
                }
            }
        }
    }

    fun observeSyncState(): StateFlow<SyncState> = state

    suspend fun enableSync(accountId: String, deviceId: String) {
        syncExclusiveLock.withLock {
            refuseIfRestoreBarrierLocked()
            enableSyncLocked(accountId, deviceId)
        }
    }

    private suspend fun enableSyncLocked(accountId: String, deviceId: String) {
        if (!syncSettingsDataStore.enableSyncForAccount(accountId, deviceId)) {
            throw LocalDataFamilyMismatchException(
                context.getString(R.string.settings_sync_local_data_family_mismatch)
            )
        }
        cloudSyncScheduler.schedulePeriodicSync()
    }

    /**
     * Crash-safe barrier before binding Room snapshot to a target family.
     * Must run before enable/create/publish when switching families.
     */
    suspend fun prepareFamilyBinding(targetFamilyId: String, mode: FamilyBindingMode) {
        syncExclusiveLock.withLock {
            refuseIfRestoreBarrierLocked()
            cloudSyncScheduler.cancelPeriodicSync()

            val settings = syncSettingsDataStore.settingsFlow.first()
            val action = LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
                localDataFamilyId = settings.localDataFamilyId,
                storedAccountId = settings.accountId,
                targetFamilyId = targetFamilyId,
                mode = mode
            )

            if (action == com.familyhub.domain.model.LocalDataFamilyAction.REFUSE) {
                refuseFamilyBindingLocked()
                throw FamilyBindingRefusedException(
                    context.getString(R.string.settings_sync_family_binding_refused)
                )
            }

            val deviceId = settings.deviceId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            syncSettingsDataStore.beginFamilyBindingTransition(targetFamilyId, deviceId)

            if (action == com.familyhub.domain.model.LocalDataFamilyAction.WIPE) {
                try {
                    localDataWiper.wipeAllFamilyData()
                } catch (error: Exception) {
                    syncSettingsDataStore.setLastError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Sync,
                            tag = "SyncRepository",
                        ),
                    )
                    Timber.e(error, "prepareFamilyBinding: wipe failed for target=%s", targetFamilyId)
                    throw error
                }
            }

            syncSettingsDataStore.setLocalDataFamilyId(targetFamilyId)
            Timber.d(
                "prepareFamilyBinding: target=%s mode=%s action=%s owner=%s",
                targetFamilyId,
                mode,
                action,
                targetFamilyId
            )
        }
    }

    private suspend fun refuseFamilyBindingLocked() {
        cloudSyncScheduler.cancelPeriodicSync()
        pauseSyncLocked()
        syncSettingsDataStore.setLastError(
            context.getString(R.string.settings_sync_family_binding_refused)
        )
    }

    private suspend fun refuseIfRestoreBarrierLocked() {
        val settings = syncSettingsDataStore.settingsFlow.first()
        if (settings.restoreInProgress) {
            throw RestoreBlockedException(
                "Sync enable/binding blocked while encrypted restore is incomplete"
            )
        }
    }

    suspend fun pauseSync() {
        syncExclusiveLock.withLock {
            pauseSyncLocked()
        }
    }

    private suspend fun pauseSyncLocked() {
        syncSettingsDataStore.pauseSync()
        cloudSyncScheduler.cancelPeriodicSync()
        Timber.d("Sync paused, preserving account binding and tombstones")
    }

    suspend fun disableSync() {
        syncExclusiveLock.withLock {
            disableSyncLocked()
        }
    }

    private suspend fun disableSyncLocked() {
        syncSettingsDataStore.disableSync()
        cloudSyncScheduler.cancelPeriodicSync()
        Timber.d("Sync disabled, cleared tombstones and account binding")
    }

    suspend fun wipeAndResetForSignOut() {
        syncExclusiveLock.withLock {
            wipeAndResetForSignOutLocked()
        }
    }

    internal suspend fun wipeAndResetForSignOutLocked() {
        cloudSyncScheduler.cancelPeriodicSync()
        pauseSyncLocked()
        localDataWiper.wipeAllFamilyData()
        syncSettingsDataStore.resetForSignOut()
        Timber.d("Sync wiped and reset for sign-out")
    }

    /**
     * Разрешить повторную подачу заявки после отклонения/удаления.
     * Убираем текущего пользователя из списка deletedMembers.
     */
    suspend fun clearDeletedFlagForCurrentUser(accountId: String) {
        val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
        if (currentUserId.isNotBlank()) {
            syncSettingsDataStore.removeDeletedMember(accountId, currentUserId)
            Timber.d("clearDeletedFlagForCurrentUser: removed %s from deletedMembers for family %s", currentUserId, accountId)
        }
    }

    suspend fun createFamily(accountId: String) {
        require(accountId.isNotBlank()) { "Family id must not be blank" }
        val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("User not authenticated")
        val member = memberRepository.getMemberById(authUser.uid)
            ?: throw IllegalStateException("Current member not found")
        require(member.id == authUser.uid) { "Current member does not match authenticated user" }
        require(member.role == MemberRole.ADMIN) { "Family creator must be ADMIN" }

        val familyRef = firestore.collection("families").document(accountId)
        val memberRef = familyRef.collection("members").document(authUser.uid)
        withTimeout(15_000) {
            firestore.runBatch { batch ->
                batch.set(familyRef, mapOf("ownerUid" to authUser.uid), SetOptions.merge())
                batch.set(memberRef, member.toExportMember())
            }.await()
        }
    }

    suspend fun publishSelfMember(accountId: String, forceResubmit: Boolean = false) {
        val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
        // Получаем данные НАПРЯМУЮ из репозитория, чтобы избежать race condition
        // при обновлении роли (ensureSelfMemberExists -> publishSelfMember)
        val member = memberRepository.getMemberById(currentUserId) ?: run {
            Timber.w("publishSelfMember: member not found for id=%s", currentUserId)
            return
        }

        if (forceResubmit && member.role == MemberRole.PENDING) {
            syncSettingsDataStore.removeDeletedMember(accountId, member.id)
            backendApi.upsertSelfMember(accountId, member.toExportMember())
            return
        }
        
        // КРИТИЧНО: Проверяем remote перед отправкой
        val remoteData = runCatching { backendApi.downloadSnapshot(accountId) }.getOrNull()
        val remoteMember = remoteData?.members?.find { it.id == currentUserId }
        
                // КРИТИЧНО: Если remote не существует или пустой - это создание новой семьи
                // В этом случае отправляем роль как есть (ADMIN для создателя)
                // И очищаем список удаленных участников (новая семья = новый контекст)
                if (remoteData == null || remoteData.members.isEmpty()) {
                    syncSettingsDataStore.clearDeletedMembers(accountId)
                    val self = member.toExportMember()
                    Timber.d("publishSelfMember: Creating new family. id=%s, role=%s. Cleared deleted members list.", self.id, self.role)
                    backendApi.upsertSelfMember(accountId, self)
                    return
                }
        
        // КРИТИЧНО: Если пользователя нет в remote (был отклонен/удален), но remote существует и не пустой
        // Разрешаем отправку заявки ТОЛЬКО если пользователь PENDING локально (повторная попытка после отклонения)
        // НО: Проверяем список удаленных участников - если пользователь там есть, значит админ его отклонил
        // и не нужно автоматически отправлять заявку (только при явном действии пользователя)
        if (remoteMember == null) {
            // Проверяем, находится ли пользователь в списке удаленных
            val deletedMembers = syncSettingsDataStore.getDeletedMembers(accountId)
            val isDeleted = member.id in deletedMembers
            
            if (member.role == MemberRole.PENDING) {
                if (isDeleted && !forceResubmit) {
                    // Пользователь в списке удаленных - админ его отклонил
                    // НЕ отправляем заявку автоматически - только при явном действии пользователя (forceResubmit=true)
                    Timber.w("publishSelfMember: User not in remote and in deleted members list (was rejected by admin). Not sending request automatically. User must explicitly resubmit. id=%s", member.id)
                    return
                } else {
                    // Пользователь PENDING и его нет в remote
                    // Если он в списке удаленных, но forceResubmit=true - удаляем из списка и отправляем заявку
                    if (isDeleted && forceResubmit) {
                        syncSettingsDataStore.removeDeletedMember(accountId, member.id)
                        Timber.d("publishSelfMember: User was in deleted list, but forceResubmit=true. Removed from deleted list and resubmitting request. id=%s", member.id)
                    }
                    // Пользователь PENDING и его нет в remote - это может быть первая попытка присоединения или повторная после отклонения
                    // Разрешаем отправку заявки, так как это явное действие пользователя
                    val self = member.toExportMember()
                    Timber.d("publishSelfMember: User not in remote (was rejected), but PENDING locally. Resubmitting request (user explicitly wants to rejoin). id=%s, role=%s", self.id, self.role)
                    backendApi.upsertSelfMember(accountId, self)
                    return
                }
            } else {
                // Пользователь не PENDING и его нет в remote - это означает, что он был удален админом
                // НЕ отправляем заявку - вместо этого syncNow должен запустить очистку данных
                Timber.w("publishSelfMember: User not in remote (was removed) and not PENDING locally. Not sending request. Local role: %s. syncNow will handle data clearing.", member.role)
                return
            }
        }
        
        // КРИТИЧНО: Если в remote пользователь уже ADMIN, а локально PENDING -> не отправляем PENDING
        // Это защита от перезаписи ADMIN роли при присоединении к новой семье
        if (remoteMember != null && remoteMember.role == MemberRole.ADMIN.name && member.role == MemberRole.PENDING) {
            Timber.w("publishSelfMember: User is ADMIN in remote but PENDING locally. Not sending PENDING to avoid overwriting ADMIN role.")
            return
        }
        
        // КРИТИЧНО: Если локально PENDING, а в remote уже есть роль (PARENT/CHILD) - не отправляем PENDING
        // Это означает, что админ уже одобрил заявку, и мы должны принять роль из remote
        if (member.role == MemberRole.PENDING && remoteMember != null && remoteMember.role != MemberRole.PENDING.name) {
            Timber.d("publishSelfMember: User is PENDING locally but has role %s in remote (was approved). Not sending PENDING, will accept role from remote.", remoteMember.role)
            return
        }
        
        // КРИТИЧНО: Если локально не PENDING, а в remote PENDING - не отправляем локальную роль
        // Это означает, что мы уже подали заявку и ждем одобрения
        if (member.role != MemberRole.PENDING && remoteMember != null && remoteMember.role == MemberRole.PENDING.name) {
            Timber.d("publishSelfMember: User has role %s locally but is PENDING in remote (awaiting approval). Not sending local role.", member.role)
            return
        }
        
        // КРИТИЧНО: Проверка на единственного админа перед отправкой
        // Если пытаемся отправить себя как ADMIN, проверяем что в remote нет другого ADMIN
        if (member.role == MemberRole.ADMIN && remoteData != null) {
            val remoteAdmins = remoteData.members.filter { it.role == MemberRole.ADMIN.name && it.id != member.id }
            if (remoteAdmins.isNotEmpty()) {
                Timber.e("publishSelfMember: Cannot send ADMIN role - admin already exists in remote: %s. Rejecting.", remoteAdmins.map { it.id }.joinToString())
                syncSettingsDataStore.setLastError(
                    context.getString(R.string.settings_error_only_one_admin)
                )
                return
            }
        }
        
        val self = member.toExportMember()
        Timber.d("publishSelfMember: id=%s, role=%s", self.id, self.role)
        backendApi.upsertSelfMember(accountId, self)
    }

    suspend fun deleteMemberFromServer(accountId: String, memberId: String) {
        backendApi.deleteMember(accountId, memberId)
        // Отслеживаем удаленного участника, чтобы он не восстановился при следующей синхронизации
        syncSettingsDataStore.addDeletedMember(accountId, memberId)
        Timber.d("deleteMemberFromServer: Deleted member %s from family %s. Added to deleted members list.", memberId, accountId)
    }

    suspend fun downloadSnapshot(accountId: String): ExportData? {
        return backendApi.downloadSnapshot(accountId)
    }

    suspend fun createLocalBackup(): File {
        val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
        val exportData = exportService.getExportData(currentUserId)
        val backupsDir = File(context.filesDir, "backups")
        val file = ExportTempFileFactory.createBackupFile(
            backupsDir = backupsDir,
            exportDate = exportData.exportDate
        )
        try {
            file.outputStream().bufferedWriter(java.nio.charset.StandardCharsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(exportData))
            }
            return file
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private suspend fun blockSyncForOwnerMismatchLocked() {
        cloudSyncScheduler.cancelPeriodicSync()
        pauseSyncLocked()
        syncSettingsDataStore.setLastError(
            context.getString(R.string.settings_sync_local_data_family_mismatch)
        )
    }

    suspend fun syncNow(force: Boolean = false): Result<Unit> {
        return syncExclusiveLock.withLock {
            val settings = syncSettingsDataStore.settingsFlow.first()
            if (settings.restoreInProgress) {
                Timber.w("syncNow: blocked by encrypted restore barrier")
                return@withLock Result.failure(
                    RestoreBlockedException("Sync blocked while encrypted restore is incomplete")
                )
            }
            if (!settings.isEnabled) {
                return@withLock Result.success(Unit)
            }

            val accountId = settings.accountId ?: return@withLock Result.success(Unit)

            when (
                val exportGuard = LocalDataFamilySwitchPolicy.resolveSyncExportGuard(
                    settings.localDataFamilyId,
                    accountId
                )
            ) {
                LocalDataFamilySwitchPolicy.SyncExportGuardResult.Block -> {
                    Timber.w(
                        "syncNow: local data owner %s does not match account %s; blocking export",
                        settings.localDataFamilyId,
                        accountId
                    )
                    blockSyncForOwnerMismatchLocked()
                    _state.update { it.copy(isSyncing = false, isEnabled = false) }
                    return@withLock Result.failure(
                        LocalDataFamilyMismatchException(
                            context.getString(R.string.settings_sync_local_data_family_mismatch)
                        )
                    )
                }
                is LocalDataFamilySwitchPolicy.SyncExportGuardResult.LegacyAdopt -> {
                    if (settings.localDataFamilyId.isNullOrBlank() && settings.accountId == accountId) {
                        syncSettingsDataStore.setLocalDataFamilyId(exportGuard.familyId)
                        Timber.d(
                            "syncNow: legacy adopt localDataFamilyId=%s before export",
                            exportGuard.familyId
                        )
                    } else {
                        blockSyncForOwnerMismatchLocked()
                        _state.update { it.copy(isSyncing = false, isEnabled = false) }
                        return@withLock Result.failure(
                            LocalDataFamilyMismatchException(
                                context.getString(R.string.settings_sync_local_data_family_mismatch)
                            )
                        )
                    }
                }
                LocalDataFamilySwitchPolicy.SyncExportGuardResult.Allow -> Unit
            }

            val lastSyncAt = settings.lastSyncAt
            val recentlySynced = lastSyncAt != null && (System.currentTimeMillis() - lastSyncAt) < 60_000
            val trackerHasPendingChanges = syncChangeTracker.hasPendingChangesIncludingPersisted()
            if (
                !force &&
                !settings.hasPendingChanges &&
                !trackerHasPendingChanges &&
                settings.lastError.isNullOrBlank() &&
                recentlySynced
            ) {
                return@withLock Result.success(Unit)
            }

            // Проверяем авторизацию перед синхронизацией
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (firebaseUser == null) {
                Timber.w("syncNow: User not authenticated")
                syncSettingsDataStore.setLastError("User not authenticated. Please sign in.")
                _state.update { it.copy(isSyncing = false) }
                return@withLock Result.failure(Exception("User not authenticated"))
            }
            Timber.d("syncNow: Starting sync")

            _state.update { it.copy(isSyncing = true, lastError = null) }
            try {
                // Получаем currentUserId для проверки роли
                val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
                val capturedLocalData = syncChangeTracker.captureSyncSnapshot {
                    exportService.getExportData(currentUserId)
                }
                val localData = capturedLocalData.value
                val syncCheckpoint = capturedLocalData.checkpoint
                val hasPendingChanges = syncCheckpoint.hasPendingChanges

                // Определяем роль текущего пользователя
                val currentUser = localData.members.find { it.id == currentUserId }

                // КРИТИЧНО: Если stored currentUserId не совпадает с auth.uid, значит мы работаем под чужим профилем.
                // Это может происходить после удаления/очистки, когда остался старый currentUserId.
                // В этом случае сразу очищаем данные и показываем сообщение об удалении.
                val authUserId = firebaseUser.uid
                if (currentUserId != authUserId) {
                    Timber.w("syncNow: currentUserId (%s) != authUserId (%s). Treating as removed and clearing data.", currentUserId, authUserId)
                    syncSettingsDataStore.addDeletedMember(accountId, authUserId)
                    clearFamilyDataForRemovedMember(authUserId)
                    appSettingsDataStore.setCurrentUser(authUserId)
                    syncSettingsDataStore.setLastError(context.getString(R.string.settings_member_removed_notice))
                    _state.update { it.copy(isSyncing = false) }
                    return@withLock Result.success(Unit)
                }

                if (currentUser?.role == MemberRole.PENDING.name) {
                    val remoteSelf = withTimeout(15_000) {
                        firestore.collection("families")
                            .document(accountId)
                            .collection("members")
                            .document(authUserId)
                            .get()
                            .await()
                    }
                    val remoteSelfRole = remoteSelf.getString("role")
                        ?.let { runCatching { MemberRole.valueOf(it) }.getOrNull() }
                    if (!SyncJoinPolicy.canReadFamilySnapshot(MemberRole.PENDING, remoteSelfRole)) {
                        syncSettingsDataStore.setLastError(
                            context.getString(R.string.settings_member_pending_notice)
                        )
                        _state.update { it.copy(isSyncing = false) }
                        return@withLock Result.success(Unit)
                    }
                }
                
                // Download with timeout; при ошибке трактуем как временную проблему (не как удалённую семью)
                // КРИТИЧНО: Различаем сетевые ошибки от реального отсутствия семьи
                val remoteResult = runCatching {
                    withTimeout(15_000) { backendApi.downloadSnapshot(accountId) }
                }
                val remoteException = remoteResult.exceptionOrNull()
                val remoteData = remoteResult.getOrNull()
                val remoteFetchSucceeded = remoteResult.isSuccess
                val remoteAvailable = remoteFetchSucceeded && remoteData != null
                // КРИТИЧНО: remoteMissing = true только если запрос успешен, но данных нет
                // Это означает реальное отсутствие семьи, а не сетевую ошибку
                val remoteMissing = remoteFetchSucceeded && remoteData == null
                
                // Проверяем тип ошибки для правильной обработки
                val isNetworkError = remoteException != null && remoteException !is FirebaseFirestoreException
                val isFirestoreError = remoteException is FirebaseFirestoreException
                val isTimeoutError = remoteException is kotlinx.coroutines.TimeoutCancellationException
                val isMalformedMenuSnapshot = remoteException?.let { root ->
                    generateSequence(root as Throwable?) { it.cause }.any { throwable ->
                        throwable is MenuFirestoreDocumentCodecException ||
                            throwable is MenuCloudSnapshotInvalidException
                    }
                } == true

                if (!remoteFetchSucceeded && isMalformedMenuSnapshot) {
                    Timber.e(remoteException, "Malformed remote menu snapshot. Aborting sync without import/upload.")
                    syncSettingsDataStore.setLastError(
                        context.getString(R.string.settings_sync_download_failed)
                    )
                    _state.update { it.copy(isSyncing = false) }
                    return@withLock Result.failure(remoteException ?: Exception("Malformed menu snapshot"))
                }

                if (!remoteFetchSucceeded) {
                    val firestoreCode = (remoteException as? FirebaseFirestoreException)?.code
                    if (
                        firestoreCode == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                        firestoreCode == FirebaseFirestoreException.Code.UNAUTHENTICATED
                    ) {
                        if (
                            firestoreCode == FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                            currentUser?.role == MemberRole.PENDING.name
                        ) {
                            syncSettingsDataStore.setLastError(
                                context.getString(R.string.settings_member_pending_notice)
                            )
                            _state.update { it.copy(isSyncing = false) }
                            return@withLock Result.success(Unit)
                        }
                        Timber.w(remoteException, "Sync download forbidden, disabling sync.")
                        syncSettingsDataStore.setLastError(
                            context.getString(R.string.settings_sync_no_access)
                        )
                        disableSyncLocked()
                        _state.update { it.copy(isSyncing = false) }
                        return@withLock Result.failure(remoteException ?: Exception("No access to family"))
                    }

                    // КРИТИЧНО: Сетевые ошибки и таймауты - это временные проблемы
                    // НЕ трактуем их как удаление семьи
                    // Сохраняем hasPendingChanges и показываем ошибку, но не отключаем синк
                    Timber.w(remoteException, "Sync download failed (network/timeout error), keep pending changes. Not treating as family deletion.")
                    syncSettingsDataStore.setLastError(
                        remoteException?.localizedMessage
                            ?: context.getString(R.string.settings_sync_download_failed)
                    )
                    _state.update { it.copy(isSyncing = false) }
                    return@withLock Result.failure(remoteException ?: Exception("Download failed"))
                }
                remoteData?.let { Timber.d("Sync remote snapshot loaded: members=%d tasks=%d", it.members.size, it.tasks.size) }

                // 1. Проверяем статус в облаке (Source of Truth)
                val remoteMember = remoteData?.members?.find { it.id == currentUserId }
                val isRemotePending = remoteMember?.role == MemberRole.PENDING.name
                val isRemoteAdmin = remoteMember?.role == MemberRole.ADMIN.name
                val isLocalPending = currentUser?.role == MemberRole.PENDING.name
                
                val lastSyncAtForFirstSync = settings.lastSyncAt ?: 0L
                val isFirstSync = lastSyncAtForFirstSync == 0L
                
                // Если нас нет в remote, но remote существует, и мы были PENDING/MEMBER -> нас удалили/отклонили
                // Исключение: мы только что создали семью (мы Admin) или только что присоединяемся (ещё не отправили данные)
                
                // Флаг, разрешающий запись (только Admin)
                // ВАЖНО: Разрешаем только если:
                // 1. В облаке мы Admin (и мы точно в этой семье)
                // 2. ИЛИ Облака нет (null), НО это первая синхронизация (создание семьи) и мы локально Admin.
                // КРИТИЧНО: Если локально PENDING, а в remote ADMIN -> это означает, что пользователь подключается к новой семье,
                // где он уже был ADMIN (из предыдущей синхронизации). В этом случае НЕ даем права админа.
                val isMemberWriteAllowed = if (remoteData != null) {
                    // Remote существует - права определяются ТОЛЬКО по remote
                    // НО: если локально PENDING, а в remote ADMIN -> это присоединение к новой семье, не даем права
                    if (isLocalPending && isRemoteAdmin) {
                        false // Присоединение к новой семье - не даем права админа
                    } else {
                        isRemoteAdmin && remoteMember != null
                    }
                } else {
                    // Remote не существует - это создание новой семьи
                    // Разрешаем только если это первая синхронизация и локально мы Admin
                    isFirstSync && currentUser?.role == MemberRole.ADMIN.name
                }
                
                Timber.d(
                    "Sync: writeAllowed=%s remoteAdmin=%s remoteMissing=%s firstSync=%s localRole=%s",
                    isMemberWriteAllowed,
                    isRemoteAdmin,
                    remoteMissing,
                    isFirstSync,
                    currentUser?.role
                )
                
                // КРИТИЧНО: Логируем загруженные данные для диагностики
                if (remoteData != null) {
                    Timber.d("Sync: Remote data loaded. Tasks=%d, Members=%d, isMemberWriteAllowed=%s", 
                        remoteData.tasks.size, remoteData.members.size, isMemberWriteAllowed)
                } else {
                    Timber.d("Sync: Remote data is null. remoteFetchSucceeded=%s", remoteFetchSucceeded)
                }

                // 2. Обработка отклонения/удаления
                val wasPendingOrMember = currentUser != null
                val isInRemote = remoteMember != null

                // КРИТИЧНО: Если нас нет в remote, но remote существует, и локального профиля уже нет
                // (currentUser == null), это означает, что нас удалили. Нужно очистить данные и показать сообщение.
                if (remoteData != null && !isInRemote && currentUser == null) {
                    Timber.w("User missing in remote and no local profile. Treating as removed. Clearing data for userId=%s", currentUserId)
                    syncSettingsDataStore.addDeletedMember(accountId, currentUserId)
                    clearFamilyDataForRemovedMember(currentUserId)
                    syncSettingsDataStore.setLastError(context.getString(R.string.settings_member_removed_notice))
                    _state.update { it.copy(isSyncing = false) }
                    return@withLock Result.success(Unit)
                }
                
                // Кейс: Семья удалена полностью (remoteData == null), но мы уже были в ней (не первый синк)
                // КРИТИЧНО: Проверяем что это не сетевая ошибка, а реальное отсутствие семьи
                if (remoteMissing && !isFirstSync && wasPendingOrMember && !isNetworkError && !isTimeoutError) {
                    // remoteMissing = true только если запрос успешен, но данных нет
                    // Это означает реальное удаление семьи, а не временную проблему сети
                    Timber.w("Family does not exist (deleted remotely). Disabling sync and clearing data.")
                    disableSyncLocked()
                    clearFamilyDataForRemovedMember(currentUserId)
                    _state.update { it.copy(isSyncing = false) }
                    return@withLock Result.failure(Exception("Family was deleted"))
                }
                
                // КРИТИЧНО: Если пользователя нет в remote (был отклонен/удален), но remote существует
                // Нужно очистить локальные данные семьи и установить роль PENDING (для повторного запроса вручную)
                // Обрабатываем даже если локальный профиль уже отсутствует (currentUser == null), главное что нас нет в remote.
                if (
                    remoteData != null &&
                    !isInRemote &&
                    SyncJoinPolicy.shouldTreatMissingMemberAsRemoval(
                        currentUser?.role?.let { runCatching { MemberRole.valueOf(it) }.getOrNull() }
                    )
                ) {
                    val memberCount = localData.members.size
                    val taskCount = localData.tasks.size
                    val eventCount = localData.events.size
                    val shoppingListCount = localData.shoppingLists.size
                    
                    // Проверяем, не были ли данные уже очищены
                    // Данные считаются очищенными, если:
                    // 1. Роль уже PENDING
                    // 2. И в локальных данных только один участник (сам пользователь)
                    // 3. И нет задач/событий/списков покупок
                    // ВАЖНО: Если пользователь был PENDING, но у него есть данные - это локальные данные до присоединения,
                    // их тоже нужно очистить при отклонении. Поэтому проверяем наличие данных, а не только роль.
                    val hasData = memberCount > 1 || taskCount > 0 || eventCount > 0 || shoppingListCount > 0
                    val alreadyCleared = isLocalPending && !hasData
                    
                    // КРИТИЧНО: Различаем отклонение заявки (PENDING) и удаление участника (PARENT/CHILD)
                    val wasRejected = isLocalPending  // Если был PENDING - это отклонение заявки
                    val wasRemoved = !isLocalPending   // Если был PARENT/CHILD/ADMIN - это удаление участника
                    
                    if (alreadyCleared) {
                        val message = if (wasRemoved) {
                            context.getString(R.string.settings_member_removed_notice)
                        } else {
                            context.getString(R.string.settings_member_rejected_notice)
                        }
                        Timber.d("User not in remote (%s), but data already cleared (members=%d, tasks=%d, events=%d, shoppingLists=%d). Showing message and pausing sync.", 
                            if (wasRemoved) "was removed" else "was rejected", memberCount, taskCount, eventCount, shoppingListCount)
                        syncSettingsDataStore.setLastError(message)
                        pauseSyncLocked()
                        _state.update { it.copy(isSyncing = false, isEnabled = false) }
                        return@withLock Result.success(Unit)
                    }
                    
                    Timber.w("User not in remote (%s). Clearing family data and setting PENDING status. Current data: members=%d, tasks=%d, events=%d, shoppingLists=%d, isLocalPending=%s, hasData=%s", 
                        if (wasRemoved) "was removed" else "was rejected", memberCount, taskCount, eventCount, shoppingListCount, isLocalPending, hasData)
                    
                    // КРИТИЧНО: Добавляем пользователя в список удаленных, чтобы предотвратить автоматическую повторную отправку заявки
                    // Это делается как при отклонении админом, так и при обнаружении отсутствия в remote
                    val deletedMembers = syncSettingsDataStore.getDeletedMembers(accountId)
                    if (currentUserId !in deletedMembers) {
                        syncSettingsDataStore.addDeletedMember(accountId, currentUserId)
                        Timber.d("Added user %s to deleted members list (was rejected/removed from remote)", currentUserId)
                    }
                    
                    // Помечаем пользователя как удаленного, чтобы предотвратить автоповторы
                    syncSettingsDataStore.addDeletedMember(accountId, currentUserId)
                    
                    // Очищаем данные семьи (кроме приватных: awesomness и личных целей)
                    // ВАЖНО: Очищаем ВСЕГДА при отклонении/удалении, даже если пользователь был PENDING
                    // (могли быть локальные данные до присоединения, которые нужно очистить)
                    clearFamilyDataForRemovedMember(currentUserId)
                    
                    // Устанавливаем роль PENDING (но НЕ отправляем заявку сразу)
                    val selfMember = currentUser ?: localData.members.firstOrNull { it.id == currentUserId }
                    if (selfMember != null && selfMember.role != MemberRole.PENDING.name) {
                        val pendingSelf = selfMember.copy(role = MemberRole.PENDING.name)
                        memberRepository.upsertMember(
                            com.familyhub.domain.model.FamilyMember(
                                id = pendingSelf.id,
                                displayName = pendingSelf.displayName,
                                roleLabel = pendingSelf.roleLabel,
                                avatarColor = pendingSelf.avatarColor,
                                role = MemberRole.PENDING,
                                age = null,
                                photoUrl = null
                            )
                        )
                        Timber.d("Set role to PENDING for rejected member. User can manually resubmit request if needed.")
                    }
                    
                    // НЕ отправляем заявку автоматически - это предотвратит цикл повторных отправок
                    // Пользователь может вручную повторно подать заявку, если админ передумает
                    // Отключаем синхронизацию, чтобы не оставаться привязанным к удалённой семье
                    val message = if (wasRemoved) {
                        context.getString(R.string.settings_member_removed_notice)
                    } else {
                        context.getString(R.string.settings_member_rejected_notice)
                    }
                    syncSettingsDataStore.setLastError(message)
                    pauseSyncLocked()
                    _state.update { it.copy(isSyncing = false, isEnabled = false) }
                    return@withLock Result.success(Unit)
                }

                // 3. Обработка PENDING статуса
                // КРИТИЧНО: Если локально PENDING, а в remote уже PARENT/CHILD - заявка одобрена!
                // Нужно принять роль из remote и начать импорт данных
                if (isLocalPending && isInRemote && !isRemotePending) {
                    // Заявка одобрена - принимаем роль из remote
                    val approvedRole = remoteMember?.role ?: MemberRole.PENDING.name
                    Timber.d("User was PENDING but now approved in remote with role %s. Accepting role and starting data import.", approvedRole)
                    
                    // Обновляем локальную роль на одобренную
                    val selfMember = currentUser ?: localData.members.firstOrNull { it.id == currentUserId }
                    if (selfMember != null) {
                        val approvedMember = selfMember.copy(role = approvedRole)
                        memberRepository.upsertMember(
                            com.familyhub.domain.model.FamilyMember(
                                id = approvedMember.id,
                                displayName = approvedMember.displayName,
                                roleLabel = approvedMember.roleLabel,
                                avatarColor = approvedMember.avatarColor,
                                role = MemberRole.valueOf(approvedRole),
                                age = null,
                                photoUrl = null
                            )
                        )
                        Timber.d("Updated local role from PENDING to %s", approvedRole)
                    }
                    
                    // Удаляем из deletedMembers (если был там)
                    val deletedMembers = syncSettingsDataStore.getDeletedMembers(accountId)
                    if (currentUserId in deletedMembers) {
                        syncSettingsDataStore.removeDeletedMember(accountId, currentUserId)
                        Timber.d("Removed user %s from deleted members list (was approved)", currentUserId)
                    }
                    
                    // Очищаем ошибку - заявка одобрена
                    syncSettingsDataStore.setLastError(null)
                    
                    // Продолжаем синхронизацию - данные будут импортированы ниже
                } else if (isRemotePending || (isLocalPending && isInRemote && isRemotePending)) {
                    // Мы PENDING и в remote тоже PENDING (ожидаем одобрения) - не импортируем данные семьи
                    Timber.d("User is PENDING in remote. Skipping data import.")
                    
                    // Обновляем ошибку, чтобы показать статус "Ожидание"
                    syncSettingsDataStore.setLastError(
                        context.getString(R.string.settings_member_pending_notice)
                    )
                    _state.update { it.copy(isSyncing = false) }
                    return@withLock Result.success(Unit)
                }
                
                // Missing PENDING is ambiguous without an explicit server-side rejection status.
                if (isLocalPending && !isInRemote && remoteData != null) {
                    Timber.d("User is PENDING locally but missing remotely; waiting for an explicit server status.")
                    syncSettingsDataStore.setLastError(
                        context.getString(R.string.settings_member_pending_notice)
                    )
                    _state.update { it.copy(isSyncing = false) }
                    return@withLock Result.success(Unit)
                }

                // 4. Подготовка данных для слияния (только если мы НЕ PENDING)
                
                // Если текущего пользователя нет в облаке (но мы не PENDING и не удалены?), это странно.
                // Но если мы Admin и remoteData == null, то мы инициализируем облако.
                
                val selfMember = currentUser ?: localData.members.firstOrNull { it.id == currentUserId }
                
                // Очистка локальных данных перед слиянием (если мы не админ)
                // Чтобы не отправить "мусор" в облако и не смешать чужие данные
                val sanitizedLocal = when {
                    isMemberWriteAllowed -> localData
                    else -> {
                        // Для обычных участников: 
                        // 1. Members: берем только себя (с ролью из облака).
                        // 2. Остальные коллекции (Tasks, Shopping, etc.): отправляем всё как есть.
                        //    Мы доверяем mergeSnapshots объединить их с облаком.
                        //    Если мы что-то изменили, мы хотим это отправить.
                        
                        // Важно: members содержит ТОЛЬКО selfMember. 
                        // mergeSnapshots объединит это с remote members.
                        
                        // Если мы не админ, мы не имеем права менять свою роль.
                        // Если в облаке у нас другая роль, мы должны принять её, даже если локально мы "назначили" себя админом.
                        val adjustedSelf = if (remoteMember != null && selfMember != null && selfMember.role != remoteMember.role) {
                            selfMember.copy(
                                role = remoteMember.role,
                                roleLabel = remoteMember.roleLabel
                            )
                        } else {
                            selfMember
                        }
                        
                        val selfList = listOfNotNull(adjustedSelf)
                        
                        localData.copy(
                            members = selfList
                            // Остальные поля оставляем как есть (tasks, shopping, etc.)
                        )
                    }
                }

                // Если есть изменения, обновляем дату экспорта, чтобы наши изменения считались новыми.
                // Даже если мы не админ, мы можем менять задачи/покупки, и наши правки должны побеждать старые данные из облака.
                val datedLocal = if (hasPendingChanges) {
                    sanitizedLocal.copy(
                        exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    )
                } else {
                    sanitizedLocal
                }

                // Получаем tombstones один раз для merge и последующей верификации
                val deletedMembers = syncSettingsDataStore.getDeletedMembers(accountId)
                val deletedRecipeIds = syncSettingsDataStore.getDeletedRecipes(accountId)
                val deletedDocuments = accountId?.let { syncSettingsDataStore.getDeletedDocuments(it) } ?: emptySet()

                val documentsToReconcile = deletedCloudDocumentsToReconcile(datedLocal, deletedDocuments)
                if (documentsToReconcile.isNotEmpty()) {
                    syncSettingsDataStore.removeDeletedDocuments(documentsToReconcile)
                    documentsToReconcile.forEach { document ->
                        Timber.d(
                            "Removed %s/%s from deleted documents (recreated or failed delete locally)",
                            document.collection,
                            document.documentId
                        )
                    }
                }
                val activeDeletedDocuments = accountId?.let { syncSettingsDataStore.getDeletedDocuments(it) } ?: emptySet()

                if (remoteData != null && (
                        deletedMembers.isNotEmpty() ||
                            deletedRecipeIds.isNotEmpty() ||
                            activeDeletedDocuments.isNotEmpty()
                    )
                ) {
                    confirmTombstonesFromRemote(
                        accountId,
                        remoteData,
                        deletedMembers,
                        deletedRecipeIds,
                        filterPreMergeConfirmableDeletedDocuments(activeDeletedDocuments)
                    )
                }
                val deletedDocumentsForMerge = accountId?.let { syncSettingsDataStore.getDeletedDocuments(it) } ?: emptySet()
                val deletedDocumentIdsByCollection = remoteActiveDeletedDocumentIdsByCollection(deletedDocumentsForMerge)
                val menuTombstones = menuTombstonesFrom(
                    deletedDocumentsForMerge,
                    syncCheckpoint.pendingDeletedEntityIdsByCollection,
                )
                val stagedMenuTombstoneDocuments = stagedMenuTombstones(deletedDocumentsForMerge)

                val nonMenuLocal = datedLocal.withoutMenuFields()
                val nonMenuRemote = remoteData?.withoutMenuFields()

                var nonMenuMerged = syncMerger.mergeSnapshots(
                    nonMenuLocal,
                    nonMenuRemote,
                    currentUserId,
                    isMemberWriteAllowed,
                    hasPendingChanges,
                    deletedMembers,
                    deletedRecipeIds,
                    deletedDocumentIdsByCollection,
                    pendingByCollection = syncCheckpoint.pendingByCollection,
                    pendingEntityIdsByCollection = syncCheckpoint.pendingEntityIdsByCollection,
                    pendingDeletedEntityIdsByCollection = syncCheckpoint.pendingDeletedEntityIdsByCollection,
                )

                val menuResult = mergeMenuCloud(
                    localSnapshot = datedLocal.toMenuCloudSnapshot(),
                    remoteSnapshot = remoteData?.toMenuCloudSnapshot(),
                    tombstones = menuTombstones,
                    currentUserId = currentUserId,
                    remoteSelfMember = remoteMember?.toMenuCloudMemberRef(),
                    localSelfMember = selfMember?.toMenuCloudMemberRef(),
                )
                val menuUploadContext = buildMenuCloudUploadContext(menuResult)

                val mergedForLocal = nonMenuMerged.withMenuSnapshot(menuResult.localProjection)
                val targetForCloud = nonMenuMerged.withMenuSnapshot(menuResult.cloudTarget)
                var merged = mergedForLocal
                
                Timber.d(
                    "Sync merge: localMembers=%d remoteMembers=%d mergedMembers=%d writeAllowed=%s",
                    datedLocal.members.size,
                    remoteData?.members?.size ?: 0,
                    merged.members.size,
                    isMemberWriteAllowed
                )
                
                // КРИТИЧНО: Логируем количество задач для диагностики
                Timber.d("Sync merge: local tasks=%d, remote tasks=%d, merged tasks=%d, isMemberWriteAllowed=%s", 
                    datedLocal.tasks.size, remoteData?.tasks?.size ?: 0, merged.tasks.size, isMemberWriteAllowed)
                
                // 5. Проверка на изменения
                val syncDecisions = evaluateMenuCloudSyncDecisions(
                    datedLocal = datedLocal,
                    remoteData = remoteData,
                    mergedForLocal = mergedForLocal,
                    menuUploadContext = menuUploadContext,
                    hasPendingChanges = hasPendingChanges,
                    remoteAvailable = remoteAvailable,
                    isCreatingFamily = remoteMissing && isMemberWriteAllowed && localData.members.size <= 1,
                    stagedMenuTombstonesPresent = stagedMenuTombstoneDocuments.isNotEmpty(),
                    normalizedForComparison = syncMerger::normalizedForComparison,
                )
                val noDiff = syncDecisions.nonMenuNoDiff
                
                // ВАЖНО: Проверяем, есть ли в merged участники, которых нет локально
                // Это нужно для случая, когда новый PENDING участник появился на сервере
                // НО: Не считаем новыми участников, которые были удалены админом локально
                // (merged уже не содержит их благодаря mergeMembers, но проверяем для надежности)
                val localMemberIds = datedLocal.members.map { it.id }.toSet()
                val mergedMemberIds = merged.members.map { it.id }.toSet()
                val newMergedMembers = mergedMemberIds - localMemberIds
                
                // Исключаем участников, которые были удалены админом локально
                // (merged уже не содержит их, но проверяем для надежности)
                // ВАЖНО: Для админа новые участники из remote (например, PENDING) должны импортироваться,
                // даже если их нет в local (это новые заявки на присоединение)
                val newMembers = if (isMemberWriteAllowed) {
                    // Если мы админ, ВСЕ новые участники из merged считаются новыми
                    // (включая PENDING участников, которые подали заявку)
                    // mergeMembers уже исключил участников, удаленных админом локально
                    newMergedMembers
                } else {
                    // Если мы не админ, все новые участники из merged считаются новыми
                    newMergedMembers
                }
                
                val hasNewMembers = newMembers.isNotEmpty()

                val localDiffersFromMerged = syncDecisions.localDiffersFromMerged

                // Пропускаем импорт ТОЛЬКО если:
                // 1. Нет локальных изменений (hasPendingChanges = false)
                // 2. Non-menu merged идентичен remote (noDiff = true)
                // 3. Нет новых участников (hasNewMembers = false)
                // 4. И локальные данные идентичны merged (localDiffersFromMerged = false)
                // 5. Нет menu cloud writes и writable menu tombstone verification
                if (
                    !hasPendingChanges &&
                    noDiff &&
                    !hasNewMembers &&
                    !localDiffersFromMerged &&
                    !syncDecisions.menuCloudNeedsWrite &&
                    !syncDecisions.writableMenuTombstoneNeedsVerification
                ) {
                    Timber.d("Sync skipped (no diff, no new members, local data matches merged).")
                    syncChangeTracker.markSynchronized(syncCheckpoint)
                    syncSettingsDataStore.updateLastSync(System.currentTimeMillis())
                    _state.update { it.copy(isSyncing = false) }
                    return@withLock Result.success(Unit)
                }
                
                if (localDiffersFromMerged) {
                    Timber.d("Sync: Local data differs from merged. Will import merged data to sync local database.")
                }
                
                if (hasNewMembers) {
                    Timber.d("Sync: Found new members in merged: %s. Will import data.", newMembers)
                } else if (newMergedMembers.isNotEmpty()) {
                    Timber.d("Sync: Found members in merged that were deleted by admin locally: %s. Not importing.", newMergedMembers)
                }
                
                // 6. Загрузка в облако
                // Пишем только если мы Admin (isMemberWriteAllowed) ИЛИ если это инициализация (remoteData == null)
                // Обычные пользователи (PARENT/CHILD) могут писать изменения (tasks, shopping), но НЕ members (кроме себя?)
                // В текущей реализации backendApi.uploadSnapshot перезаписывает весь документ.
                // Поэтому обычным пользователям опасно давать uploadSnapshot, если они не имеют полной картины.
                // НО: mergeSnapshots объединяет данные. Если мы PARENT, мы скачали remote, объединили с local, и отправляем обратно.
                // Это нормально, ЕСЛИ мы не трогаем members.
                
                // Однако, user request: "SyncRepository при currentMember.role != ADMIN вообще не трогает коллекцию members"
                // `uploadSnapshot` в `FirestoreBackendApi` пишет `members` в подколлекцию.
                // Если мы не Admin, мы не должны вызывать `uploadSnapshot` с `members`.
                
                // Решение: Передаем флаг `updateMembers` в `uploadSnapshot`.
                // Если мы не Admin, `updateMembers` = false.
                
                // Если remoteData == null (семья не существует или удалена), то писать может только Админ (создание семьи).
                // Если мы не админ и remoteData == null, мы не должны ничего писать (иначе "воскресим" семью без участников).
                // Доп. проверка: создание семьи возможно только если локально мы одни (чтобы не восстановить бэкап удаленной семьи)
                val isCreatingFamily = remoteMissing && isMemberWriteAllowed && localData.members.size <= 1

                val canUpload = syncDecisions.uploadRequired

                if (canUpload) {
                    val uploadMerged = if (remoteData != null) {
                        var finalMerged = targetForCloud.copy(
                            version = remoteData.version,
                            versionNanos = remoteData.versionNanos
                        )
                        
                        // КРИТИЧНО: Проверка на единственного админа перед загрузкой
                        val adminCount = finalMerged.members.count { it.role == MemberRole.ADMIN.name }
                        if (adminCount > 1) {
                            Timber.e("uploadSnapshot: CRITICAL - Multiple admins in merged data (%d)! Rejecting upload to prevent data corruption.", adminCount)
                            syncSettingsDataStore.setLastError(
                                context.getString(R.string.settings_error_multiple_admins)
                            )
                            _state.update { it.copy(isSyncing = false) }
                            return@withLock Result.failure(
                                SyncFamilyInvariantException.MultipleAdmins()
                            )
                        } else if (adminCount == 0 && remoteData.members.isNotEmpty()) {
                            // Нет админа, но семья существует - критическая ошибка
                            Timber.e("uploadSnapshot: CRITICAL - No admin in merged data but family exists! Rejecting upload.")
                            syncSettingsDataStore.setLastError(
                                context.getString(R.string.settings_error_no_admin)
                            )
                            _state.update { it.copy(isSyncing = false) }
                            return@withLock Result.failure(
                                SyncFamilyInvariantException.MissingAdmin()
                            )
                        }
                        
                        // Дополнительная защита: сохраняем участников из remote
                        // НО: Не восстанавливаем участников, которые были удалены админом локально
                        // (это защита от багов в mergeMembers, но не от намеренного удаления админом)
                        val mergedMemberIds = finalMerged.members.map { it.id }.toSet()
                        val remoteMemberIds = remoteData.members.map { it.id }.toSet()
                        val localMemberIds = datedLocal.members.map { it.id }.toSet()
                        val missingMembers = remoteMemberIds - mergedMemberIds
                        
                        // Восстанавливаем только тех участников, которые:
                        // 1. Есть в remote, но нет в merged
                        // 2. НЕ были удалены админом локально (есть в local ИЛИ это ADMIN из remote)
                        if (missingMembers.isNotEmpty()) {
                            val membersToRestore = missingMembers.filter { memberId ->
                                // Восстанавливаем если:
                                // - Участник есть в local (не был удален админом) ИЛИ
                                // - Это ADMIN из remote (защита от случайного удаления админа)
                                val remoteMember = remoteData.members.find { it.id == memberId }
                                val shouldRestore = memberId in localMemberIds || 
                                    (remoteMember?.role == MemberRole.ADMIN.name && isMemberWriteAllowed)
                                
                                if (!shouldRestore) {
                                    Timber.d("Upload preparation: NOT restoring member %s - was deleted by admin locally", memberId)
                                }
                                shouldRestore
                            }
                            
                            if (membersToRestore.isNotEmpty()) {
                                Timber.w("Upload preparation: Restoring %d members from remote: %s. isMemberWriteAllowed=%s", 
                                    membersToRestore.size, membersToRestore, isMemberWriteAllowed)
                                val allMembers = finalMerged.members.toMutableList()
                                remoteData.members.forEach { remoteMember ->
                                    if (remoteMember.id in membersToRestore) {
                                        Timber.d("Upload preparation: Adding missing member: %s (role=%s)", remoteMember.id, remoteMember.role)
                                        allMembers.add(remoteMember)
                                    }
                                }
                                finalMerged = finalMerged.copy(members = allMembers)
                                
                                // Повторная проверка после восстановления
                                val finalAdminCount = finalMerged.members.count { it.role == MemberRole.ADMIN.name }
                                if (finalAdminCount > 1) {
                                    Timber.e("uploadSnapshot: CRITICAL - Multiple admins after restore (%d)! Rejecting upload.", finalAdminCount)
                                    syncSettingsDataStore.setLastError(
                                        context.getString(R.string.settings_error_multiple_admins)
                                    )
                                    _state.update { it.copy(isSyncing = false) }
                                    return@withLock Result.failure(
                                        SyncFamilyInvariantException.MultipleAdmins()
                                    )
                                }
                                
                                Timber.d("Upload preparation: Fixed members count: %d -> %d", 
                                    finalMerged.members.size - membersToRestore.size, finalMerged.members.size)
                            } else {
                                Timber.d("Upload preparation: All missing members were deleted by admin, not restoring")
                            }
                        }
                        
                        finalMerged
                    } else {
                        // Создание новой семьи - проверяем что есть ровно один ADMIN
                        val adminCount = merged.members.count { it.role == MemberRole.ADMIN.name }
                        if (adminCount != 1) {
                            Timber.e("uploadSnapshot: CRITICAL - Creating new family but admin count is %d (expected 1)! Rejecting upload.", adminCount)
                            syncSettingsDataStore.setLastError(
                                context.getString(R.string.settings_error_invalid_admin_count)
                            )
                            _state.update { it.copy(isSyncing = false) }
                            return@withLock Result.failure(
                                SyncFamilyInvariantException.InvalidAdminCount()
                            )
                        }
                        merged
                    }

                    val shouldRunUploadTransaction = syncDecisions.shouldRunUploadTransaction || isCreatingFamily
                    val verificationOnly = syncDecisions.verificationOnly

                    if (shouldRunUploadTransaction) {
                        val selfMemberForUpload = selfMemberForUpload(selfMember, remoteMember)
                        val writerRole = remoteMember?.role?.let { roleName ->
                            runCatching { MemberRole.valueOf(roleName) }.getOrNull()
                        } ?: selfMemberForUpload?.role?.let { roleName ->
                            runCatching { MemberRole.valueOf(roleName) }.getOrNull()
                        }
                        Timber.d(
                            "Sync upload: isMemberWriteAllowed=%s, members count=%d, menuOps=%d, selfRole=%s, writerRole=%s",
                            isMemberWriteAllowed,
                            uploadMerged.members.size,
                            menuUploadContext.operations.size,
                            selfMemberForUpload?.role,
                            writerRole,
                        )
                        val uploadPayload = SyncExportSanitizer.sanitize(uploadMerged)
                        withTimeout(15_000) {
                            backendApi.uploadSnapshot(
                                accountId,
                                uploadPayload,
                                isMemberWriteAllowed = isMemberWriteAllowed,
                                selfMember = selfMemberForUpload,
                                baseSnapshot = remoteData,
                                menuUploadContext = menuUploadContext,
                                writerRole = writerRole,
                            )
                        }
                    } else if (verificationOnly) {
                        Timber.d("Sync verificationOnly: writable menu tombstone verification without upload transaction")
                    }

                    val deletedDocumentsForVerification = accountId?.let {
                        syncSettingsDataStore.getDeletedDocuments(it)
                    } ?: emptySet()
                    val preMergeDocumentsForVerification = filterPreMergeConfirmableDeletedDocuments(
                        deletedDocumentsForVerification
                    )
                    val menuDocumentsForVerification = filterMenuDeletedDocuments(deletedDocumentsForVerification)
                    if (
                        syncDecisions.verificationDownloadRequired &&
                            (
                                deletedMembers.isNotEmpty() ||
                                    deletedRecipeIds.isNotEmpty() ||
                                    preMergeDocumentsForVerification.isNotEmpty() ||
                                    menuDocumentsForVerification.isNotEmpty()
                                )
                    ) {
                        val verifyResult = runCatching {
                            withTimeout(15_000) { backendApi.downloadSnapshot(accountId) }
                        }
                        val remoteAfterUpload = verifyResult.getOrNull()
                        if (verifyResult.isSuccess && remoteAfterUpload != null) {
                            confirmTombstonesFromRemote(
                                accountId,
                                remoteAfterUpload,
                                deletedMembers,
                                deletedRecipeIds,
                                preMergeDocumentsForVerification
                            )
                            confirmMenuTombstonesFromVerifiedRemote(
                                accountId = accountId,
                                verifiedRemote = remoteAfterUpload,
                                menuTombstones = menuDocumentsForVerification,
                                remoteMember = remoteAfterUpload.members.find { it.id == currentUserId },
                                capability = menuUploadContext.capability,
                            )
                        } else {
                            Timber.w(
                                verifyResult.exceptionOrNull(),
                                "Tombstone verification download failed; keeping tombstones"
                            )
                        }
                    }
                }

                Timber.d("Sync: Importing merged data. Tasks count: %d, isMemberWriteAllowed=%s", merged.tasks.size, isMemberWriteAllowed)
                syncChangeTracker.runWithoutTracking {
                    importService.importData(
                        data = merged,
                        expectedOutboxMaxSequence = syncCheckpoint.outboxMaxSequence,
                    )
                }
                Timber.d("Sync: Data imported successfully. Tasks count: %d", merged.tasks.size)
                syncChangeTracker.markSynchronized(syncCheckpoint)
                syncSettingsDataStore.updateLastSync(System.currentTimeMillis())
                syncSettingsDataStore.setLastError(null)

                return@withLock Result.success(Unit)
            } catch (e: FirebaseFirestoreException) {
                syncSettingsDataStore.setLastError(
                    UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.Sync,
                        tag = "SyncRepository",
                    ),
                )
                return@withLock Result.failure(e)
            } catch (e: Exception) {
                syncSettingsDataStore.setLastError(
                    UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.Sync,
                        tag = "SyncRepository",
                    ),
                )
                return@withLock Result.failure(e)
            } finally {
                _state.update { it.copy(isSyncing = false) }
            }
        }
    }

    private suspend fun confirmTombstonesFromRemote(
        familyId: String,
        remoteSnapshot: ExportData,
        deletedMembers: Set<String>,
        deletedRecipeIds: Set<String>,
        deletedDocuments: Set<DeletedCloudDocument> = emptySet()
    ) {
        if (deletedMembers.isEmpty() && deletedRecipeIds.isEmpty() && deletedDocuments.isEmpty()) return

        val remoteMemberIds = remoteSnapshot.members.map { it.id }.toSet()
        val remoteRecipeIds = remoteSnapshot.recipes.map { it.id }.toSet()

        deletedMembers.forEach { deletedMemberId ->
            if (deletedMemberId !in remoteMemberIds) {
                syncSettingsDataStore.removeDeletedMember(familyId, deletedMemberId)
                Timber.d(
                    "Removed member %s from deleted members list (confirmed deleted on server)",
                    deletedMemberId
                )
            } else {
                Timber.d(
                    "Member %s still present on server; keeping deleted-member tombstone",
                    deletedMemberId
                )
            }
        }
        deletedRecipeIds.forEach { deletedRecipeId ->
            if (deletedRecipeId !in remoteRecipeIds) {
                syncSettingsDataStore.removeDeletedRecipe(familyId, deletedRecipeId)
                Timber.d(
                    "Removed recipe %s from deleted recipes list (confirmed deleted on server)",
                    deletedRecipeId
                )
            } else {
                Timber.d(
                    "Recipe %s still present on server; keeping deleted-recipe tombstone",
                    deletedRecipeId
                )
            }
        }
        val confirmedAbsentDocuments = deletedCloudDocumentsConfirmedAbsentOnRemote(remoteSnapshot, deletedDocuments)
        if (confirmedAbsentDocuments.isNotEmpty()) {
            syncSettingsDataStore.removeDeletedDocuments(confirmedAbsentDocuments)
            confirmedAbsentDocuments.forEach { document ->
                Timber.d(
                    "Removed %s/%s from deleted documents (confirmed deleted on server)",
                    document.collection,
                    document.documentId
                )
            }
        }
        deletedDocuments.filter { it !in confirmedAbsentDocuments }.forEach { document ->
            Timber.d(
                "%s/%s still present on server; keeping deleted-document tombstone",
                document.collection,
                document.documentId
            )
        }
    }

    private suspend fun confirmMenuTombstonesFromVerifiedRemote(
        accountId: String,
        verifiedRemote: ExportData,
        menuTombstones: Set<DeletedCloudDocument>,
        remoteMember: ExportMember?,
        capability: com.familyhub.data.menu.cloud.MenuCloudWriteCapabilityResult,
    ) {
        if (menuTombstones.isEmpty()) return
        if (!capability.canUploadMenu || !isAuthoritativeMenuWriterRole(remoteMember)) {
            menuTombstones.forEach { document ->
                Timber.d(
                    "%s/%s menu tombstone kept (read-only capability or role)",
                    document.collection,
                    document.documentId,
                )
            }
            return
        }
        val confirmedAbsent = menuTombstonesConfirmedAbsentOnRawRemote(
            rawRemote = verifiedRemote.toMenuCloudSnapshot(),
            tombstones = menuTombstones,
        )
        if (confirmedAbsent.isNotEmpty()) {
            syncSettingsDataStore.removeDeletedDocuments(confirmedAbsent)
            confirmedAbsent.forEach { document ->
                Timber.d(
                    "Removed %s/%s from deleted documents (menu tombstone confirmed absent on server)",
                    document.collection,
                    document.documentId,
                )
            }
        }
        menuTombstones.filter { it !in confirmedAbsent }.forEach { document ->
            Timber.d(
                "%s/%s still present on server; keeping menu tombstone",
                document.collection,
                document.documentId,
            )
        }
    }

    suspend fun clearFamilyDataForRemovedMember(keepUserId: String) {
        Timber.d("Clearing family data, keeping private data for user: %s", keepUserId)

        val localData = exportService.getExportData(keepUserId)
        if (localData.members.none { it.id == keepUserId }) {
            Timber.w("Self member not found in local data; clearing shared family data without keeping a member row")
        }

        val clearedData = buildClearedFamilySnapshot(localData, keepUserId)
        importService.importData(clearedData)
        syncSettingsDataStore.updateLastSync(0L)

        Timber.d(
            "Family data cleared. Kept: %d members, %d personal goals for user %s.",
            clearedData.members.size,
            clearedData.familyGoals.size,
            keepUserId
        )
    }

    companion object {
        @VisibleForTesting
        internal fun buildClearedFamilySnapshot(localData: ExportData, keepUserId: String): ExportData {
            val selfMember = localData.members.find { it.id == keepUserId }
            val members = if (selfMember == null) {
                emptyList()
            } else if (selfMember.role != MemberRole.PENDING.name) {
                listOf(selfMember.copy(role = MemberRole.PENDING.name))
            } else {
                listOf(selfMember)
            }

            val personalGoals = localData.familyGoals.filter { goal ->
                goal.personal && goal.createdByUserId == keepUserId
            }
            val personalGoalIds = personalGoals.map { it.id }.toSet()
            val personalGoalStages = localData.familyGoalStages.filter { it.goalId in personalGoalIds }
            val personalGoalSteps = localData.familyGoalSteps.filter { it.goalId in personalGoalIds }

            return ExportData(
                members = members,
                familyGoals = personalGoals,
                familyGoalStages = personalGoalStages,
                familyGoalSteps = personalGoalSteps,
                exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            )
        }
    }
}
