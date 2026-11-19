package com.familyhub.ui.screens.profile

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.storage.FirebaseStorageUploader
import com.familyhub.data.sync.SyncRepository
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.utils.BundledFamilyAvatar
import com.familyhub.util.ExternalImageUrlUtils
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

data class EditProfileUiState(
    val member: FamilyMember? = null,
    val displayName: String = "",
    val birthDate: String = "",
    val age: String = "",
    val email: String = "",
    val password: String = "",
    val photoUrl: String? = null,
    val loadStatus: EditProfileLoadStatus = EditProfileLoadStatus.LOADING,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val memberRepository: FamilyMemberRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth,
    private val syncRepository: SyncRepository,
    private val storageUploader: FirebaseStorageUploader
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val memberId: String? = savedStateHandle.get<String>("memberId")

    init {
        if (memberId.isNullOrBlank()) {
            _uiState.value = EditProfileUiState(
                loadStatus = EditProfileLoadStatus.UNAVAILABLE,
            )
        } else {
            loadMember(memberId)
        }
    }

    private fun loadMember(targetId: String) {
        viewModelScope.launch {
            try {
                val member = memberRepository.getMemberById(targetId)
                if (member != null) {
                    val computedAge = computeAge(member.birthDate.orEmpty()) ?: member.age
                    _uiState.value = EditProfileUiState(
                        member = member,
                        displayName = member.displayName,
                        birthDate = member.birthDate.orEmpty(),
                        age = computedAge?.toString() ?: "",
                        email = firebaseAuth.currentUser?.email.orEmpty(),
                        photoUrl = member.photoUrl,
                        loadStatus = EditProfileLoadStatus.READY,
                    )
                } else {
                    _uiState.value = EditProfileUiState(
                        loadStatus = EditProfileLoadStatus.UNAVAILABLE,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Timber.w(e, "EditProfileViewModel: failed to load member")
                _uiState.value = EditProfileUiState(
                    loadStatus = EditProfileLoadStatus.FAILED,
                )
            }
        }
    }

    fun retryLoad() {
        val targetId = memberId
        if (targetId.isNullOrBlank()) {
            _uiState.value = EditProfileUiState(
                loadStatus = EditProfileLoadStatus.UNAVAILABLE,
            )
            return
        }
        if (_uiState.value.loadStatus == EditProfileLoadStatus.LOADING) return

        _uiState.value = EditProfileUiState(
            loadStatus = EditProfileLoadStatus.LOADING,
        )
        loadMember(targetId)
    }

    fun onDisplayNameChange(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name)
    }

    fun onBirthDateChange(value: String) {
        val computedAge = computeAge(value)
        _uiState.value = _uiState.value.copy(
            birthDate = value,
            age = computedAge?.toString() ?: ""
        )
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun onPhotoUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(photoUrl = url.trim().takeIf { it.isNotEmpty() })
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun saveProfile() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val member = currentState.member ?: run {
                _uiState.value = currentState.copy(
                    error = context.getString(R.string.edit_profile_error_member_not_found)
                )
                return@launch
            }

            val computedAge = computeAge(currentState.birthDate)
            val normalizedPhoto = if (BundledFamilyAvatar.isToken(currentState.photoUrl)) {
                currentState.photoUrl
            } else {
                ExternalImageUrlUtils.normalizeForStorage(currentState.photoUrl)
            }
            if (currentState.photoUrl?.isNotBlank() == true && normalizedPhoto == null) {
                _uiState.value = currentState.copy(
                    error = context.getString(R.string.edit_profile_error_invalid_photo_url)
                )
                return@launch
            }

            val resolvedPhotoUrl = when {
                normalizedPhoto == null -> null
                ExternalImageUrlUtils.isLocalContentUri(normalizedPhoto) ->
                    storageUploader.uploadMemberAvatarIfNeeded(normalizedPhoto) ?: normalizedPhoto
                else -> normalizedPhoto
            }
            val updatedMember = member.copy(
                displayName = currentState.displayName,
                age = computedAge,
                birthDate = currentState.birthDate.ifBlank { null },
                photoUrl = resolvedPhotoUrl
            )

            runCatching {
                firebaseAuth.currentUser?.let { user ->
                    currentState.email.takeIf { it.isNotBlank() && it != user.email }?.let { newEmail ->
                        user.updateEmail(newEmail).await()
                    }
                    currentState.password.takeIf { it.isNotBlank() }?.let { newPassword ->
                        user.updatePassword(newPassword).await()
                    }
                }
                memberRepository.upsertMember(updatedMember)
                syncRepository.syncNow(force = true)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSaved = true, password = "")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = UserFacingErrorMapper.message(
                        context,
                        error,
                        UserFacingErrorContext.Profile,
                        tag = "EditProfileViewModel",
                    ),
                )
            }
        }
    }

    private fun computeAge(birthDate: String): Int? {
        return runCatching {
            val parts = birthDate.split("-")
            if (parts.size != 3) return null
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            val today = java.time.LocalDate.now()
            val birth = java.time.LocalDate.of(year, month, day)
            val age = java.time.Period.between(birth, today).years
            age.coerceAtLeast(0)
        }.getOrNull()
    }
}
