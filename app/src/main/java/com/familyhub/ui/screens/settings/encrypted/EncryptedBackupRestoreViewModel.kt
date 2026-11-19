package com.familyhub.ui.screens.settings.encrypted
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.backup.EncryptedBackupFormat
import com.familyhub.data.backup.EncryptedBackupWriter
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.export.DataExportService
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.restore.EncryptedRestoreCoordinator
import com.familyhub.data.restore.NeedsLocalProfilePolicy
import com.familyhub.data.restore.RestorePreviewAuthPolicy
import com.familyhub.data.restore.RestoreResult
import com.familyhub.data.restore.RestoreStagingStore
import com.familyhub.data.restore.RestoreUidPresencePolicy
import com.familyhub.data.sync.SyncChangeTracker
import com.familyhub.data.sync.SyncExclusiveLock
import com.familyhub.di.IoDispatcher
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@HiltViewModel
class EncryptedBackupRestoreViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val encryptedBackupWriter: EncryptedBackupWriter,
    private val restoreCoordinator: EncryptedRestoreCoordinator,
    private val stagingStore: RestoreStagingStore,
    private val exportService: DataExportService,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val memberRepository: FamilyMemberRepository,
    private val syncExclusiveLock: SyncExclusiveLock,
    private val syncChangeTracker: SyncChangeTracker,
    private val firebaseAuth: FirebaseAuth,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val createInFlight = AtomicBoolean(false)
    private val inspectInFlight = AtomicBoolean(false)
    private val restoreInFlight = AtomicBoolean(false)
    private val stageInFlight = AtomicBoolean(false)
    private val addProfileInFlight = AtomicBoolean(false)
    private val userOperationGeneration = AtomicLong(0)
    private val _state = MutableStateFlow(EncryptedBackupRestoreUiState())
    val state: StateFlow<EncryptedBackupRestoreUiState> = _state.asStateFlow()
    private val _events = Channel<EncryptedBackupRestoreUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    init {
        observeNeedsLocalProfileRecovery()
        viewModelScope.launch {
            hydrateStagingPathAfterProcessDeath()
        }
    }
    private fun observeNeedsLocalProfileRecovery() {
        viewModelScope.launch {
            combine(
                syncSettingsDataStore.settingsFlow,
                memberRepository.getAllMembers()
            ) { syncSettings, members ->
                val authUid = firebaseAuth.currentUser?.uid
                NeedsLocalProfilePolicy.isRecoveryNeeded(
                    suppressAuthMemberBootstrap = syncSettings.suppressAuthMemberBootstrap,
                    authenticatedUid = authUid,
                    membersContainUid = authUid != null && members.any { it.id == authUid }
                )
            }.collect { recoveryNeeded ->
                _state.update { it.copy(needsLocalProfileRecovery = recoveryNeeded) }
            }
        }
    }
    fun onCreateBackupClicked() {
        if (!_state.value.allowsPrimaryActions || _state.value.blocksDismiss) return
        bumpUserOperation()
        _state.update { it.copy(phase = EncryptedBackupRestorePhase.CreatePassword()) }
    }
    fun dismissCreatePasswordDialog() {
        if (_state.value.phase is EncryptedBackupRestorePhase.CreatingBackup) return
        bumpUserOperation()
        _state.update { it.copy(phase = EncryptedBackupRestorePhase.Idle) }
    }
    fun confirmCreateBackup(password: CharArray, passwordConfirm: CharArray) {
        if (!createInFlight.compareAndSet(false, true)) {
            wipe(password)
            wipe(passwordConfirm)
            return
        }
        if (!password.contentEquals(passwordConfirm)) {
            wipe(password)
            wipe(passwordConfirm)
            createInFlight.set(false)
            _state.update {
                it.copy(
                    phase = EncryptedBackupRestorePhase.CreatePassword(
                        error = EncryptedBackupRestoreError.PASSWORD_MISMATCH
                    )
                )
            }
            return
        }
        if (password.size < EncryptedBackupFormat.MIN_PASSWORD_CHARS ||
            password.size > EncryptedBackupFormat.MAX_PASSWORD_CHARS
        ) {
            wipe(password)
            wipe(passwordConfirm)
            createInFlight.set(false)
            _state.update {
                it.copy(
                    phase = EncryptedBackupRestorePhase.CreatePassword(
                        error = EncryptedBackupRestoreError.PASSWORD_POLICY
                    )
                )
            }
            return
        }
        wipe(passwordConfirm)
        bumpUserOperation()
        _state.update { it.copy(phase = EncryptedBackupRestorePhase.CreatingBackup) }
        viewModelScope.launch {
            try {
                val intent = withContext(ioDispatcher) {
                    val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
                    val exportData = exportService.getExportData(currentUserId)
                    val sourceFamilyId = syncSettingsDataStore.settingsFlow.first().localDataFamilyId
                    val file = encryptedBackupWriter.write(
                        exportData = exportData,
                        sourceFamilyId = sourceFamilyId,
                        password = password
                    )
                    exportService.shareFile(file)
                }
                applyPhaseIfCurrentGeneration(EncryptedBackupRestorePhase.Idle)
                _events.send(EncryptedBackupRestoreUiEvent.ShareBackup(intent))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                applyPhaseIfCurrentGeneration(
                    EncryptedBackupRestorePhase.FatalError(
                        EncryptedBackupRestoreErrorMapper.map(error)
                    )
                )
            } finally {
                wipe(password)
                createInFlight.set(false)
            }
        }
    }
    fun onRestoreDocumentSelected(uri: Uri?) {
        if (uri == null) return
        if (!_state.value.allowsPrimaryActions || _state.value.blocksDismiss) return
        if (!stageInFlight.compareAndSet(false, true)) return
        bumpUserOperation()
        val operationGen = userOperationGeneration.get()
        _state.update { it.copy(phase = EncryptedBackupRestorePhase.Staging) }
        viewModelScope.launch {
            try {
                clearStagingOnIo()
                val staged = withContext(ioDispatcher) { stagingStore.stageFromUri(uri) }
                if (!isOperationCurrent(operationGen)) return@launch
                persistStagingPath(staged.absolutePath)
                applyPhaseIfGeneration(
                    operationGen,
                    EncryptedBackupRestorePhase.AwaitingInspectPassword(
                        stagingPath = staged.absolutePath
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isOperationCurrent(operationGen)) return@launch
                persistStagingPath(null)
                applyPhaseIfGeneration(
                    operationGen,
                    EncryptedBackupRestorePhase.FatalError(
                        EncryptedBackupRestoreErrorMapper.map(error)
                    )
                )
            } finally {
                stageInFlight.set(false)
            }
        }
    }
    fun dismissInspectPasswordDialog() {
        if (_state.value.phase is EncryptedBackupRestorePhase.Inspecting) return
        val stagingPath = stagingPathFromPhase(_state.value.phase)
        bumpUserOperation()
        viewModelScope.launch {
            clearStagingOnIo(stagingPath)
            persistStagingPath(null)
            _state.update { it.copy(phase = EncryptedBackupRestorePhase.Idle) }
        }
    }
    fun confirmInspectPassword(password: CharArray) {
        if (!inspectInFlight.compareAndSet(false, true)) {
            wipe(password)
            return
        }
        val stagingPath = stagingPathFromPhase(_state.value.phase)
            ?: savedStateHandle.get<String>(KEY_STAGING_PATH)
        if (stagingPath.isNullOrBlank()) {
            wipe(password)
            inspectInFlight.set(false)
            _state.update { it.copy(phase = EncryptedBackupRestorePhase.Idle) }
            return
        }
        bumpUserOperation()
        val operationGen = userOperationGeneration.get()
        _state.update {
            it.copy(phase = EncryptedBackupRestorePhase.Inspecting(stagingPath = stagingPath))
        }
        viewModelScope.launch {
            try {
                val file = withContext(ioDispatcher) {
                    stagingStore.validateStagedPath(stagingPath)
                        ?: throw IllegalStateException("staged missing")
                }
                val inspectAuthUid = firebaseAuth.currentUser?.uid
                val preview = restoreCoordinator.inspect(
                    file = file,
                    password = password,
                    authenticatedUserId = inspectAuthUid
                )
                if (!isOperationCurrent(operationGen)) return@launch
                applyPhaseIfGeneration(
                    operationGen,
                    EncryptedBackupRestorePhase.Preview(
                        stagingPath = stagingPath,
                        fingerprintSha256 = preview.fingerprintSha256,
                        createdAt = preview.createdAt,
                        sourceFamilyId = preview.sourceFamilyId,
                        counts = preview.counts,
                        inspectAuthUid = inspectAuthUid,
                        containsAuthenticatedUser = preview.containsAuthenticatedUser
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isOperationCurrent(operationGen)) return@launch
                val mapped = EncryptedBackupRestoreErrorMapper.map(error)
                if (mapped == EncryptedBackupRestoreError.WRONG_PASSWORD) {
                    applyPhaseIfGeneration(
                        operationGen,
                        EncryptedBackupRestorePhase.AwaitingInspectPassword(
                            stagingPath = stagingPath,
                            error = mapped
                        )
                    )
                } else {
                    clearStagingOnIo(stagingPath)
                    persistStagingPath(null)
                    applyPhaseIfGeneration(
                        operationGen,
                        EncryptedBackupRestorePhase.FatalError(mapped)
                    )
                }
            } finally {
                wipe(password)
                inspectInFlight.set(false)
            }
        }
    }
    fun cancelPreview() {
        val preview = previewFromPhase(_state.value.phase) ?: return
        bumpUserOperation()
        viewModelScope.launch {
            clearStagingOnIo(preview.stagingPath)
            persistStagingPath(null)
            _state.update { it.copy(phase = EncryptedBackupRestorePhase.Idle) }
        }
    }
    fun onRestoreClicked() {
        val preview = _state.value.phase as? EncryptedBackupRestorePhase.Preview ?: return
        bumpUserOperation()
        _state.update {
            it.copy(
                phase = EncryptedBackupRestorePhase.ConfirmRestore(
                    preview = preview,
                    requiresUidConfirmation = requiresUidConfirmationForPreview(preview)
                )
            )
        }
    }
    fun dismissRestoreConfirmDialog() {
        if (_state.value.phase is EncryptedBackupRestorePhase.Restoring) return
        val confirm = _state.value.phase as? EncryptedBackupRestorePhase.ConfirmRestore ?: return
        bumpUserOperation()
        _state.update { it.copy(phase = confirm.preview) }
    }
    fun confirmRestore(
        password: CharArray,
        destructiveConfirmed: Boolean,
        uidAbsenceConfirmed: Boolean
    ) {
        if (!restoreInFlight.compareAndSet(false, true)) {
            wipe(password)
            return
        }
        val preview = when (val phase = _state.value.phase) {
            is EncryptedBackupRestorePhase.ConfirmRestore -> phase.preview
            is EncryptedBackupRestorePhase.Preview -> phase
            else -> null
        }
        val requiresUidConfirmation = when (val phase = _state.value.phase) {
            is EncryptedBackupRestorePhase.ConfirmRestore -> phase.requiresUidConfirmation
            is EncryptedBackupRestorePhase.Preview -> requiresUidConfirmationForPreview(phase)
            else -> preview?.let(::requiresUidConfirmationForPreview) ?: false
        }
        if (preview == null || !destructiveConfirmed) {
            wipe(password)
            restoreInFlight.set(false)
            return
        }
        val currentAuthUid = firebaseAuth.currentUser?.uid
        if (!RestorePreviewAuthPolicy.authSessionMatches(preview.inspectAuthUid, currentAuthUid)) {
            wipe(password)
            restoreInFlight.set(false)
            bumpUserOperation()
            _state.update {
                it.copy(
                    phase = EncryptedBackupRestorePhase.AwaitingInspectPassword(
                        stagingPath = preview.stagingPath,
                        error = EncryptedBackupRestoreError.AUTH_SESSION_CHANGED
                    )
                )
            }
            return
        }
        val needsUidConfirm = RestoreUidPresencePolicy.requiresExtraUidConfirmation(
            containsAuthenticatedUser = preview.containsAuthenticatedUser,
            authenticatedUid = currentAuthUid
        )
        if (needsUidConfirm && !uidAbsenceConfirmed) {
            wipe(password)
            restoreInFlight.set(false)
            return
        }
        bumpUserOperation()
        val operationGen = userOperationGeneration.get()
        _state.update { it.copy(phase = EncryptedBackupRestorePhase.Restoring(preview)) }
        viewModelScope.launch {
            try {
                val file = withContext(ioDispatcher) {
                    stagingStore.validateStagedPath(preview.stagingPath)
                        ?: throw IllegalStateException("staged missing")
                }
                val result = restoreCoordinator.restore(
                    file = file,
                    password = password,
                    expectedFingerprint = preview.fingerprintSha256,
                    authenticatedUserId = currentAuthUid
                ) as RestoreResult.Success
                if (!isOperationCurrent(operationGen)) return@launch
                clearStagingOnIo(preview.stagingPath)
                persistStagingPath(null)
                applyPhaseIfGeneration(
                    operationGen,
                    EncryptedBackupRestorePhase.Success(
                        needsLocalProfile = result.needsLocalProfile
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isOperationCurrent(operationGen)) return@launch
                val mapped = EncryptedBackupRestoreErrorMapper.map(error)
                when (mapped) {
                    EncryptedBackupRestoreError.WRONG_PASSWORD -> {
                        applyPhaseIfGeneration(
                            operationGen,
                            EncryptedBackupRestorePhase.ConfirmRestore(
                                preview = preview,
                                requiresUidConfirmation = requiresUidConfirmation,
                                error = mapped
                            )
                        )
                    }
                    EncryptedBackupRestoreError.FILE_CHANGED,
                    EncryptedBackupRestoreError.AUTH_SESSION_CHANGED -> {
                        applyPhaseIfGeneration(
                            operationGen,
                            EncryptedBackupRestorePhase.AwaitingInspectPassword(
                                stagingPath = preview.stagingPath,
                                error = mapped
                            )
                        )
                    }
                    else -> {
                        applyPhaseIfGeneration(
                            operationGen,
                            EncryptedBackupRestorePhase.FatalError(mapped)
                        )
                    }
                }
            } finally {
                wipe(password)
                restoreInFlight.set(false)
            }
        }
    }
    fun acknowledgeSuccess() {
        if (_state.value.phase is EncryptedBackupRestorePhase.AddingLocalProfile) return
        bumpUserOperation()
        _state.update { it.copy(phase = EncryptedBackupRestorePhase.Idle) }
    }
    fun acknowledgeError() {
        bumpUserOperation()
        viewModelScope.launch {
            val stagingPath = stagingPathFromPhase(_state.value.phase)
                ?: savedStateHandle.get<String>(KEY_STAGING_PATH)
            clearStagingOnIo(stagingPath)
            persistStagingPath(null)
            _state.update { it.copy(phase = EncryptedBackupRestorePhase.Idle) }
        }
    }
    fun addLocalProfile() {
        if (!addProfileInFlight.compareAndSet(false, true)) return
        val fromSuccess = _state.value.phase is EncryptedBackupRestorePhase.Success
        val fromRecovery = _state.value.needsLocalProfileRecovery
        if (!fromSuccess && !fromRecovery) {
            addProfileInFlight.set(false)
            return
        }
        bumpUserOperation()
        _state.update {
            it.copy(
                phase = EncryptedBackupRestorePhase.AddingLocalProfile,
                addLocalProfileError = null
            )
        }
        viewModelScope.launch {
            val authUser = firebaseAuth.currentUser
            if (authUser == null) {
                addProfileInFlight.set(false)
                _state.update {
                    it.copy(
                        phase = EncryptedBackupRestorePhase.Idle,
                        addLocalProfileError = EncryptedBackupRestoreError.GENERIC
                    )
                }
                return@launch
            }
            try {
                syncExclusiveLock.withLock {
                    syncChangeTracker.runInTransactionWithoutTracking {
                        val displayName = authUser.displayName
                            ?.takeIf { it.isNotBlank() }
                            ?: authUser.email
                                ?.substringBefore('@')
                                ?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.home_unknown_member)
                        memberRepository.upsertMember(
                            FamilyMember(
                                id = authUser.uid,
                                displayName = displayName,
                                roleLabel = displayName,
                                avatarColor = "#2196F3",
                                role = MemberRole.PENDING,
                                age = null,
                                birthDate = null,
                                photoUrl = null
                            )
                        )
                    }
                    appSettingsDataStore.setCurrentUser(authUser.uid)
                    syncSettingsDataStore.setSuppressAuthMemberBootstrap(false)
                }
                _state.update {
                    it.copy(
                        phase = if (fromSuccess) {
                            EncryptedBackupRestorePhase.Success(needsLocalProfile = false)
                        } else {
                            EncryptedBackupRestorePhase.Idle
                        },
                        addLocalProfileError = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        phase = if (fromSuccess) {
                            EncryptedBackupRestorePhase.Success(needsLocalProfile = true)
                        } else {
                            EncryptedBackupRestorePhase.Idle
                        },
                        addLocalProfileError = EncryptedBackupRestoreError.GENERIC
                    )
                }
            } finally {
                addProfileInFlight.set(false)
            }
        }
    }
    private suspend fun hydrateStagingPathAfterProcessDeath() {
        val hydrationGen = userOperationGeneration.get()
        val path = savedStateHandle.get<String>(KEY_STAGING_PATH)
        val validated = withContext(ioDispatcher) {
            stagingStore.validateStagedPath(path)
        }
        if (!isOperationCurrent(hydrationGen)) return
        if (_state.value.phase !is EncryptedBackupRestorePhase.Hydrating) return
        if (validated == null) {
            if (!path.isNullOrBlank()) {
                withContext(ioDispatcher) { stagingStore.deleteStagedPath(path) }
            }
            persistStagingPath(null)
            _state.update { it.copy(phase = EncryptedBackupRestorePhase.Idle) }
            return
        }
        _state.update {
            it.copy(
                phase = EncryptedBackupRestorePhase.AwaitingInspectPassword(
                    stagingPath = validated.absolutePath
                )
            )
        }
    }
    private fun requiresUidConfirmationForPreview(
        preview: EncryptedBackupRestorePhase.Preview
    ): Boolean = RestoreUidPresencePolicy.requiresExtraUidConfirmation(
        containsAuthenticatedUser = preview.containsAuthenticatedUser,
        authenticatedUid = firebaseAuth.currentUser?.uid
    )

    private fun bumpUserOperation() {
        userOperationGeneration.incrementAndGet()
    }
    private fun isOperationCurrent(generation: Long): Boolean =
        userOperationGeneration.get() == generation
    private fun applyPhaseIfCurrentGeneration(phase: EncryptedBackupRestorePhase) {
        applyPhaseIfGeneration(userOperationGeneration.get(), phase)
    }
    private fun applyPhaseIfGeneration(generation: Long, phase: EncryptedBackupRestorePhase) {
        if (!isOperationCurrent(generation)) return
        _state.update { it.copy(phase = phase) }
    }
    private fun stagingPathFromPhase(phase: EncryptedBackupRestorePhase): String? = when (phase) {
        is EncryptedBackupRestorePhase.AwaitingInspectPassword -> phase.stagingPath
        is EncryptedBackupRestorePhase.Inspecting -> phase.stagingPath
        is EncryptedBackupRestorePhase.Preview -> phase.stagingPath
        is EncryptedBackupRestorePhase.ConfirmRestore -> phase.preview.stagingPath
        is EncryptedBackupRestorePhase.Restoring -> phase.preview.stagingPath
        else -> null
    }
    private fun previewFromPhase(phase: EncryptedBackupRestorePhase): EncryptedBackupRestorePhase.Preview? =
        when (phase) {
            is EncryptedBackupRestorePhase.Preview -> phase
            is EncryptedBackupRestorePhase.ConfirmRestore -> phase.preview
            is EncryptedBackupRestorePhase.Restoring -> phase.preview
            else -> null
        }
    private suspend fun clearStagingOnIo(path: String? = stagingPathFromPhase(_state.value.phase)) {
        withContext(ioDispatcher) {
            stagingStore.deleteStagedPath(path)
        }
    }
    private fun persistStagingPath(path: String?) {
        if (path.isNullOrBlank()) {
            savedStateHandle.remove<String>(KEY_STAGING_PATH)
        } else {
            savedStateHandle[KEY_STAGING_PATH] = path
        }
    }
    private fun wipe(password: CharArray) {
        Arrays.fill(password, '\u0000')
    }
    companion object {
        const val KEY_STAGING_PATH = "encrypted_restore_staging_path"
    }
}
