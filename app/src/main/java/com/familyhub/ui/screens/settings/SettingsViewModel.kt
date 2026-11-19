package com.familyhub.ui.screens.settings

import android.content.Context
import android.content.Intent
import com.familyhub.data.security.BiometricCapability
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.analytics.ActivationAnalytics
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.export.PlaintextExportCoordinator
import com.familyhub.data.export.PlaintextExportConfirmResult
import com.familyhub.data.export.PlaintextExportPendingRequest
import com.familyhub.data.repository.AuthenticatedMemberBootstrapCoordinator
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.FamilyBindingMode
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.ThemeMode
import com.familyhub.domain.model.SyncState
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.domain.utils.SyncJoinPolicy
import com.familyhub.domain.usecase.DisableSyncUseCase
import com.familyhub.domain.usecase.EnableSyncUseCase
import com.familyhub.ui.navigation.normalizeBottomNavRoutes
import com.familyhub.domain.usecase.ObserveSyncStateUseCase
import com.familyhub.domain.usecase.PauseSyncUseCase
import com.familyhub.domain.usecase.PerformSyncNowUseCase
import com.familyhub.domain.usecase.SignOutUseCase
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import com.familyhub.data.sync.SyncRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.time.LocalDate
import java.time.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import timber.log.Timber

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeCustomJson: String? = null,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val members: List<FamilyMember> = emptyList(),
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING,
    val isExporting: Boolean = false,
    val exportError: String? = null,
    val syncState: SyncState = SyncState(),
    val syncActionError: String? = null,
    val backupError: String? = null,
    val isBackupInProgress: Boolean = false,
    val pendingPlaintextExport: PlaintextExportPendingRequest? = null,
    val bottomNavRoutes: List<String> = emptyList()
)

internal data class SettingsContentSnapshot(
    val themeMode: ThemeMode,
    val themeCustomJson: String?,
    val biometricEnabled: Boolean,
    val biometricAvailable: Boolean,
    val members: List<FamilyMember>,
    val currentUserId: String,
    val currentUserRole: MemberRole,
    val bottomNavRoutes: List<String>
)

internal fun buildSettingsContentSnapshot(
    settings: AppSettings,
    members: List<FamilyMember>,
    resolvedCurrentUserId: String,
    biometricAvailable: Boolean
): SettingsContentSnapshot {
    val currentUser = members.find { it.id == resolvedCurrentUserId }
    return SettingsContentSnapshot(
        themeMode = settings.themeMode,
        themeCustomJson = settings.themeCustomJson,
        biometricEnabled = settings.biometricEnabled,
        biometricAvailable = biometricAvailable,
        members = members,
        currentUserId = resolvedCurrentUserId,
        currentUserRole = currentUser?.role ?: MemberRole.PENDING,
        bottomNavRoutes = settings.bottomNavRoutes
    )
}

internal fun SettingsUiState.applyContentSnapshot(content: SettingsContentSnapshot): SettingsUiState =
    copy(
        themeMode = content.themeMode,
        themeCustomJson = content.themeCustomJson,
        biometricEnabled = content.biometricEnabled,
        biometricAvailable = content.biometricAvailable,
        members = content.members,
        currentUserId = content.currentUserId,
        currentUserRole = content.currentUserRole,
        bottomNavRoutes = content.bottomNavRoutes
    )

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsDataStore: AppSettingsDataStore,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val memberRepository: FamilyMemberRepository,
    private val plaintextExportCoordinator: PlaintextExportCoordinator,
    private val observeSyncStateUseCase: ObserveSyncStateUseCase,
    private val enableSyncUseCase: EnableSyncUseCase,
    private val disableSyncUseCase: DisableSyncUseCase,
    private val pauseSyncUseCase: PauseSyncUseCase,
    private val performSyncNowUseCase: PerformSyncNowUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val syncRepository: SyncRepository,
    private val authenticatedMemberBootstrapCoordinator: AuthenticatedMemberBootstrapCoordinator,
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val activationAnalytics: ActivationAnalytics,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        syncAuthenticatedMember()
        loadData()
        observeSyncState()
    }

    private fun syncAuthenticatedMember() {
        viewModelScope.launch {
            ensureAuthenticatedMemberExists()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                appSettingsDataStore.settingsFlow,
                memberRepository.getAllMembers()
            ) { settings, members ->
                val authUser = firebaseAuth.currentUser
                val resolvedCurrentUserId = authUser?.uid?.takeIf { it.isNotBlank() } ?: settings.currentUserId
                val biometricAvailable = BiometricCapability.canAuthenticate(context)
                buildSettingsContentSnapshot(
                    settings = settings,
                    members = members,
                    resolvedCurrentUserId = resolvedCurrentUserId,
                    biometricAvailable = biometricAvailable
                )
            }.collect { content ->
                _state.update { it.applyContentSnapshot(content) }
            }
        }
    }

    private suspend fun ensureAuthenticatedMemberExists() {
        authenticatedMemberBootstrapCoordinator.ensureAuthenticatedMemberExists()
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            observeSyncStateUseCase().collect { syncState ->
                _state.update { it.copy(syncState = syncState) }
            }
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            appSettingsDataStore.setThemeMode(themeMode)
        }
    }

    fun applyCustomTheme(primaryHex: String?, secondaryHex: String?, tertiaryHex: String?) {
        viewModelScope.launch {
            val map = mutableMapOf<String, String>()
            fun norm(hex: String?): String? = hex?.trim()?.let { if (it.startsWith("#")) it else "#${it}" }
            norm(primaryHex)?.let { map["primary"] = it }
            norm(secondaryHex)?.let { map["secondary"] = it }
            norm(tertiaryHex)?.let { map["tertiary"] = it }
            val json = if (map.isEmpty()) null else map.entries.joinToString(prefix = "{", postfix = "}") { (k,v) -> "\"$k\":\"$v\"" }
            appSettingsDataStore.setCustomThemeJson(json)
        }
    }

    fun resetCustomTheme() {
        viewModelScope.launch {
            appSettingsDataStore.setCustomThemeJson(null)
        }
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsDataStore.setBiometricEnabled(enabled)
        }
    }

    fun updateMember(
        member: FamilyMember,
        displayName: String,
        avatarColor: String,
        role: MemberRole,
        birthDate: String? = null,
        email: String? = null,
        password: String? = null,
        photoUrl: String? = member.photoUrl,
    ) {
        viewModelScope.launch {
            if (_state.value.currentUserRole != MemberRole.ADMIN) return@launch
            val updatedMember = member.copy(
                displayName = displayName,
                roleLabel = displayName,
                avatarColor = avatarColor,
                role = role,
                birthDate = birthDate?.ifBlank { null },
                age = computeAge(birthDate),
                photoUrl = photoUrl,
            )
            memberRepository.upsertMember(updatedMember)

            // Если редактируем себя — обновляем почту/пароль в FirebaseAuth
            val currentUser = firebaseAuth.currentUser
            if (member.id == _state.value.currentUserId && currentUser != null) {
                email?.takeIf { it.isNotBlank() && it != currentUser.email }?.let {
                    try {
                        currentUser.updateEmail(it).await()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to update email")
                        _state.value = _state.value.copy(syncActionError = syncErrorMessage(e))
                    }
                }
                password?.takeIf { it.isNotBlank() }?.let {
                    try {
                        currentUser.updatePassword(it).await()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to update password")
                        _state.value = _state.value.copy(syncActionError = syncErrorMessage(e))
                    }
                }
            }

            // Запускаем синхронизацию, чтобы изменения участника применились на сервере
            performSyncNowUseCase()
        }
    }

    fun addMember(
        displayName: String,
        roleLabel: String,
        avatarColor: String,
        role: MemberRole = MemberRole.CHILD,
        birthDate: String? = null,
        photoUrl: String? = null,
    ) {
        viewModelScope.launch {
            val currentMembers = memberRepository.getAllMembers().first()
            val isFirstMember = currentMembers.isEmpty()
            if (!isFirstMember && _state.value.currentUserRole != MemberRole.ADMIN) return@launch
            
            val finalRole = if (isFirstMember) MemberRole.ADMIN else role

            val newMember = FamilyMember(
                id = IdGenerator.newId(),
                displayName = displayName,
                roleLabel = roleLabel,
                avatarColor = avatarColor,
                role = finalRole,
                age = computeAge(birthDate),
                birthDate = birthDate?.ifBlank { null },
                photoUrl = photoUrl
            )
            memberRepository.upsertMember(newMember)
            // Если текущий пользователь не задан, выбираем только что добавленного
            val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
            if (currentUserId.isBlank()) {
                appSettingsDataStore.setCurrentUser(newMember.id)
            }
        }
    }

    fun deleteMember(member: FamilyMember) {
        viewModelScope.launch {
            if (_state.value.currentUserRole != MemberRole.ADMIN) return@launch
            
            // Если удаляем текущего пользователя, выходим из семьи
            if (member.id == _state.value.currentUserId) {
                handleMemberRemoved()
                return@launch
            }
            
            // Удаляем участника с сервера
            val accountId = _state.value.syncState.accountId
            if (!accountId.isNullOrBlank()) {
                try {
                    syncRepository.deleteMemberFromServer(accountId, member.id)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        syncActionError = syncErrorMessage(e)
                    )
                    return@launch
                }
            }
            
            // Удаляем участника локально
            memberRepository.deleteMember(member)
            
            // Запускаем синхронизацию, чтобы изменения применились
            performSyncNowUseCase()
        }
    }
    
    fun approveMember(member: FamilyMember, newRole: MemberRole = MemberRole.CHILD) {
        viewModelScope.launch {
            if (_state.value.currentUserRole != MemberRole.ADMIN) return@launch
            if (member.role != MemberRole.PENDING) return@launch
            
            // КРИТИЧНО: Проверяем лимиты - ADMIN может быть только один
            // Если пытаемся назначить ADMIN, проверяем что текущий админ передает права
            // (т.е. текущий админ должен сам стать не-админом, что не реализовано в UI)
            val finalRole = if (newRole == MemberRole.ADMIN) {
                // Проверяем, есть ли уже ADMIN в семье
                val currentMembers = memberRepository.getAllMembers().first()
                val hasAdmin = currentMembers.any { it.role == MemberRole.ADMIN && it.id != member.id }
                
                if (hasAdmin) {
                    // Уже есть админ - запрещаем создание второго
                    Timber.w("approveMember: Cannot approve as ADMIN - admin already exists. Approving as CHILD instead.")
                    _state.value = _state.value.copy(
                        syncActionError = context.getString(R.string.settings_error_only_one_admin)
                    )
                    MemberRole.CHILD // Откатываем на CHILD
                } else {
                    // Нет админа - разрешаем (но это не должно происходить, т.к. текущий пользователь - админ)
                    Timber.w("approveMember: Approving as ADMIN, but current user is also ADMIN. This should not happen.")
                    MemberRole.ADMIN
                }
            } else {
                // Ограничиваем доступные роли: только PARENT или CHILD
                when (newRole) {
                    MemberRole.PARENT, MemberRole.CHILD -> newRole
                    else -> MemberRole.CHILD // По умолчанию CHILD
                }
            }
            
            val updatedMember = member.copy(role = finalRole)
            memberRepository.upsertMember(updatedMember)

            // Запускаем синхронизацию, чтобы одобренный участник сразу получил доступ
            // (без фонового синка ребёнок ждёт следующего периодического запуска)
            performSyncNowUseCase()
        }
    }
    
    fun rejectMember(member: FamilyMember) {
        viewModelScope.launch {
            if (_state.value.currentUserRole != MemberRole.ADMIN) return@launch
            if (member.role != MemberRole.PENDING) return@launch
            
            // Если отклоняем текущего пользователя, выходим из семьи
            if (member.id == _state.value.currentUserId) {
                handleMemberRemoved()
                return@launch
            }
            
            // Удаляем участника с сервера
            val accountId = _state.value.syncState.accountId
            if (!accountId.isNullOrBlank()) {
                try {
                    syncRepository.deleteMemberFromServer(accountId, member.id)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        syncActionError = syncErrorMessage(e)
                    )
                    return@launch
                }
            }
            
            // Удаляем участника локально
            memberRepository.deleteMember(member)
            
            // ВАЖНО: После удаления участника локально, при следующей синхронизации
            // он не должен появиться снова из remote (логика в mergeMembers предотвращает это)
            // Но чтобы гарантировать, что диалог не появится снова, нужно убедиться,
            // что участник действительно удален с сервера перед синхронизацией
            
            // Запускаем синхронизацию, чтобы изменения применились
            // (удаление с сервера уже выполнено, синхронизация нужна для обновления локального состояния)
            performSyncNowUseCase()
            
            // После синхронизации участник не должен появиться снова, так как:
            // 1. Он удален с сервера (deleteMemberFromServer)
            // 2. Он удален локально (deleteMember)
            // 3. Логика mergeMembers не добавит его обратно, если админ удалил его локально
            
            // Принудительно обновляем состояние, чтобы диалог закрылся
            // (состояние обновится через observeMembers, но это гарантирует немедленное обновление)
        }
    }
    
    private suspend fun handleMemberRemoved() {
        // Отключаем синхронизацию
        disableSyncUseCase()
        
        // Очищаем данные семьи (кроме приватных данных: awesomness и личных целей)
        val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
        syncRepository.clearFamilyDataForRemovedMember(currentUserId)
        
        // Показываем уведомление пользователю (будет обработано в UI)
    }

    fun setCurrentUser(userId: String) {
        viewModelScope.launch {
            val authId = firebaseAuth.currentUser?.uid ?: return@launch
            if (userId != authId) return@launch
            appSettingsDataStore.setCurrentUser(authId)
        }
    }

    fun clearSyncError() {
        _state.value = _state.value.copy(syncActionError = null)
    }

    fun clearBackupError() {
        _state.value = _state.value.copy(backupError = null)
    }
    
    fun requestPlaintextExport(request: PlaintextExportPendingRequest) {
        _state.update { it.copy(pendingPlaintextExport = request) }
    }

    fun cancelPlaintextExport() {
        _state.update { state ->
            if (state.isExporting || state.isBackupInProgress) {
                state
            } else {
                state.copy(pendingPlaintextExport = null)
            }
        }
    }

    suspend fun confirmPlaintextExport(): Intent? {
        val request = _state.value.pendingPlaintextExport ?: return null

        return try {
            when (
                val result = plaintextExportCoordinator.confirmAndShare(request) {
                    _state.update { state ->
                        when (request) {
                            is PlaintextExportPendingRequest.Format -> {
                                state.copy(isExporting = true, exportError = null)
                            }
                            PlaintextExportPendingRequest.Backup -> {
                                state.copy(isBackupInProgress = true, backupError = null)
                            }
                        }
                    }
                }
            ) {
                PlaintextExportConfirmResult.AlreadyInFlight -> null
                is PlaintextExportConfirmResult.Success -> {
                    _state.update {
                        it.copy(
                            isExporting = false,
                            isBackupInProgress = false,
                            pendingPlaintextExport = null
                        )
                    }
                    result.intent
                }
            }
        } catch (e: CancellationException) {
            _state.update { state ->
                when (request) {
                    is PlaintextExportPendingRequest.Format -> {
                        state.copy(isExporting = false)
                    }
                    PlaintextExportPendingRequest.Backup -> {
                        state.copy(isBackupInProgress = false)
                    }
                }
            }
            throw e
        } catch (e: Exception) {
            _state.update { state ->
                when (request) {
                    is PlaintextExportPendingRequest.Format -> {
                        state.copy(
                            isExporting = false,
                            isBackupInProgress = false,
                            pendingPlaintextExport = null,
                            exportError = UserFacingErrorMapper.message(
                                context,
                                e,
                                UserFacingErrorContext.Export,
                                tag = "SettingsViewModel",
                            ),
                        )
                    }
                    PlaintextExportPendingRequest.Backup -> {
                        state.copy(
                            isExporting = false,
                            isBackupInProgress = false,
                            pendingPlaintextExport = null,
                            backupError = UserFacingErrorMapper.message(
                                context,
                                e,
                                UserFacingErrorContext.Backup,
                                tag = "SettingsViewModel",
                            ),
                        )
                    }
                }
            }
            null
        }
    }
    
    fun clearExportError() {
        _state.value = _state.value.copy(exportError = null)
    }

    fun enableSync(
        accountId: String,
        deviceId: String = "",
        isJoining: Boolean = false,
        memberDisplayName: String? = null
    ) {
        viewModelScope.launch {
            if (accountId.isBlank()) {
                _state.value = _state.value.copy(
                    syncActionError = context.getString(R.string.settings_sync_error_empty_fields)
                )
                return@launch
            }

            try {
                val normalizedAccountId = accountId.trim()
                val normalizedDeviceId = deviceId.trim().ifBlank { syncSettingsDataStore.ensureDeviceId() }
                if (isJoining) {
                    syncRepository.prepareFamilyBinding(
                        normalizedAccountId,
                        FamilyBindingMode.JOIN_EXISTING
                    )
                    ensureSelfMemberExists(
                        isJoining = true,
                        isCreatingFamily = false,
                        displayNameOverride = memberDisplayName
                    )
                    syncRepository.clearDeletedFlagForCurrentUser(normalizedAccountId)
                    syncRepository.publishSelfMember(normalizedAccountId, forceResubmit = true)
                    enableSyncUseCase(normalizedAccountId, normalizedDeviceId)
                    appSettingsDataStore.setFamilyOnboardingDismissed(true)
                    activationAnalytics.familyConnected(ActivationAnalytics.FamilyMode.JOIN)
                    return@launch
                }

                syncRepository.prepareFamilyBinding(
                    normalizedAccountId,
                    FamilyBindingMode.CREATE_NEW
                )
                ensureSelfMemberExists(
                    isJoining = false,
                    isCreatingFamily = true,
                    displayNameOverride = memberDisplayName
                )
                syncRepository.createFamily(normalizedAccountId)
                enableSyncUseCase(normalizedAccountId, normalizedDeviceId)
                appSettingsDataStore.setFamilyOnboardingDismissed(true)
                activationAnalytics.familyConnected(ActivationAnalytics.FamilyMode.CREATE)
                val result = performSyncNowUseCase()
                if (result.isFailure) {
                    _state.value = _state.value.copy(
                        syncActionError = syncErrorMessage(result.exceptionOrNull())
                    )
                }
            } catch (throwable: Exception) {
                _state.value = _state.value.copy(
                    syncActionError = syncErrorMessage(throwable)
                )
            }
        }
    }

    private suspend fun ensureSelfMemberExists(
        isJoining: Boolean,
        isCreatingFamily: Boolean,
        displayNameOverride: String?
    ) {
        val currentMembers = memberRepository.getAllMembers().first()
        val currentSettings = appSettingsDataStore.settingsFlow.first()
        val storedId = currentSettings.currentUserId.takeIf { it.isNotBlank() }
        val firebaseUid = firebaseAuth.currentUser?.uid
        val uid = firebaseUid ?: storedId ?: IdGenerator.newId()

        val existing = currentMembers.firstOrNull { it.id == uid }
        val role = SyncJoinPolicy.resolveLocalRole(existing?.role, isCreatingFamily, isJoining)
        if (existing != null) {
            if (storedId != uid) {
                appSettingsDataStore.setCurrentUser(uid)
            }
            if (existing.role != role) {
                memberRepository.upsertMember(existing.copy(role = role))
            }
            return
        }

        val fallbackName = firebaseAuth.currentUser?.email
            ?: context.getString(R.string.settings_member_dialog_name_label)
        val display = displayNameOverride?.trim()?.takeIf { it.isNotBlank() } ?: fallbackName
        val member = FamilyMember(
            id = uid,
            displayName = display,
            roleLabel = display,
            avatarColor = "#2196F3",
            role = role,
            age = null,
            photoUrl = null
        )
        memberRepository.upsertMember(member)
        appSettingsDataStore.setCurrentUser(uid)
    }

    fun disableSync() {
        viewModelScope.launch {
            runCatching { disableSyncUseCase() }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    syncActionError = syncErrorMessage(throwable)
                )
            }
        }
    }

    fun pauseSync() {
        viewModelScope.launch {
            runCatching { pauseSyncUseCase() }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    syncActionError = syncErrorMessage(throwable)
                )
            }
        }
    }

    fun resumeSyncFromStored() {
        viewModelScope.launch {
            val accId = _state.value.syncState.accountId
            val devId = _state.value.syncState.deviceId
            if (accId.isNullOrBlank() || devId.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    syncActionError = context.getString(R.string.settings_sync_error_empty_fields)
                )
                return@launch
            }
            try {
                syncRepository.prepareFamilyBinding(accId, FamilyBindingMode.RESUME_STORED)
                enableSyncUseCase(accId, devId)
                syncNow()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    syncActionError = syncErrorMessage(e)
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching {
                signOutUseCase()
            }.onFailure { throwable ->
                Timber.e(throwable, "signOut: failed to clean local state before sign out")
                _state.value = _state.value.copy(
                    syncActionError = syncErrorMessage(throwable)
                )
            }
        }
    }

    suspend fun syncNow(): Result<Unit> {
        _state.value = _state.value.copy(syncActionError = null)
        val result = performSyncNowUseCase()
        if (result.isFailure) {
            _state.value = _state.value.copy(
                syncActionError = syncErrorMessage(result.exceptionOrNull())
            )
        }
        return result
    }

    fun setBottomNavRoutes(routes: List<String>) {
        viewModelScope.launch {
            appSettingsDataStore.setBottomNavRoutes(normalizeBottomNavRoutes(routes))
        }
    }

    private fun syncErrorMessage(throwable: Throwable?): String {
        val error = throwable ?: return context.getString(UserFacingErrorContext.Sync.fallbackRes)
        return UserFacingErrorMapper.message(
            context,
            error,
            UserFacingErrorContext.Sync,
            tag = "SettingsViewModel",
        )
    }

    private fun computeAge(birthDate: String?): Int? {
        if (birthDate.isNullOrBlank()) return null
        return runCatching {
            val parts = birthDate.split("-")
            if (parts.size != 3) return null
            val birth = LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            Period.between(birth, LocalDate.now()).years.coerceAtLeast(0)
        }.getOrNull()
    }
}
