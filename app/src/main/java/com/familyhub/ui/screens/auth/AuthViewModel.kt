package com.familyhub.ui.screens.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.analytics.ActivationAnalytics
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    @StringRes val userMessageRes: Int? = null,
)

internal data class NormalizedAuthCredentials(
    val email: String,
    val password: String,
)

internal object AuthInputValidator {
    private const val MIN_PASSWORD_LENGTH = 6
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    @StringRes
    fun emailError(email: String): Int? {
        val normalized = normalizeEmail(email)
        return when {
            normalized.isEmpty() -> R.string.auth_error_email_required
            !emailPattern.matches(normalized) -> R.string.auth_error_email_invalid
            else -> null
        }
    }

    @StringRes
    fun passwordError(password: String, enforceMinimumLength: Boolean): Int? = when {
        password.isEmpty() -> R.string.auth_error_password_required
        enforceMinimumLength && password.length < MIN_PASSWORD_LENGTH ->
            R.string.auth_error_password_too_short
        else -> null
    }

    fun credentials(
        email: String,
        password: String,
        enforceMinimumPasswordLength: Boolean,
    ): Pair<NormalizedAuthCredentials?, Int?> {
        val emailError = emailError(email)
        if (emailError != null) return null to emailError

        val passwordError = passwordError(password, enforceMinimumPasswordLength)
        if (passwordError != null) return null to passwordError

        return NormalizedAuthCredentials(
            email = normalizeEmail(email),
            password = password,
        ) to null
    }
}

internal object FirebaseAuthErrorMapper {
    @StringRes
    fun messageRes(throwable: Throwable): Int = when (throwable) {
        is FirebaseNetworkException -> R.string.auth_error_network
        is FirebaseAuthException -> messageResForCode(throwable.errorCode)
        else -> R.string.auth_error_unknown
    }

    @StringRes
    fun passwordResetMessageRes(throwable: Throwable): Int = when (throwable) {
        is FirebaseAuthException -> passwordResetMessageResForCode(throwable.errorCode)
        else -> messageRes(throwable)
    }

    @StringRes
    fun passwordResetMessageResForCode(errorCode: String?): Int =
        if (errorCode == "ERROR_USER_NOT_FOUND") {
            R.string.auth_password_reset_sent
        } else {
            messageResForCode(errorCode)
        }

    @StringRes
    fun messageResForCode(errorCode: String?): Int = when (errorCode) {
        "ERROR_INVALID_EMAIL",
        "ERROR_MISSING_EMAIL" -> R.string.auth_error_email_invalid

        "ERROR_MISSING_PASSWORD" -> R.string.auth_error_password_required
        "ERROR_WEAK_PASSWORD" -> R.string.auth_error_password_too_short

        "ERROR_INVALID_CREDENTIAL",
        "ERROR_WRONG_PASSWORD",
        "ERROR_USER_NOT_FOUND",
        "ERROR_INVALID_USER_TOKEN",
        "ERROR_USER_TOKEN_EXPIRED" -> R.string.auth_error_invalid_credentials

        "ERROR_EMAIL_ALREADY_IN_USE",
        "ERROR_CREDENTIAL_ALREADY_IN_USE",
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> R.string.auth_error_email_in_use

        "ERROR_USER_DISABLED" -> R.string.auth_error_user_disabled
        "ERROR_TOO_MANY_REQUESTS" -> R.string.auth_error_too_many_requests
        "ERROR_NETWORK_REQUEST_FAILED" -> R.string.auth_error_network
        "ERROR_OPERATION_NOT_ALLOWED" -> R.string.auth_error_operation_not_allowed
        else -> R.string.auth_error_unknown
    }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val activationAnalytics: ActivationAnalytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthState())
    val uiState = _uiState.asStateFlow()

    fun signUp(email: String, password: String) {
        activationAnalytics.signupStarted()
        val (credentials, validationError) = AuthInputValidator.credentials(
            email = email,
            password = password,
            enforceMinimumPasswordLength = true,
        )
        if (credentials == null) {
            showMessage(validationError ?: R.string.auth_error_unknown)
            return
        }

        launchAuthOperation(authenticatedOnSuccess = true) {
            auth.createUserWithEmailAndPassword(credentials.email, credentials.password).await()
            activationAnalytics.signupCompleted()
        }
    }

    fun signIn(email: String, password: String) {
        val (credentials, validationError) = AuthInputValidator.credentials(
            email = email,
            password = password,
            enforceMinimumPasswordLength = false,
        )
        if (credentials == null) {
            showMessage(validationError ?: R.string.auth_error_unknown)
            return
        }

        launchAuthOperation(authenticatedOnSuccess = true) {
            auth.signInWithEmailAndPassword(credentials.email, credentials.password).await()
            activationAnalytics.signinCompleted()
        }
    }

    fun sendPasswordReset(email: String) {
        val validationError = AuthInputValidator.emailError(email)
        if (validationError != null) {
            showMessage(validationError)
            return
        }

        val normalizedEmail = AuthInputValidator.normalizeEmail(email)
        launchAuthOperation(
            successMessageRes = R.string.auth_password_reset_sent,
            errorMessageRes = FirebaseAuthErrorMapper::passwordResetMessageRes,
        ) {
            auth.sendPasswordResetEmail(normalizedEmail).await()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessageRes = null) }
    }

    private fun launchAuthOperation(
        authenticatedOnSuccess: Boolean = false,
        @StringRes successMessageRes: Int? = null,
        errorMessageRes: (Throwable) -> Int = FirebaseAuthErrorMapper::messageRes,
        operation: suspend () -> Unit,
    ) {
        if (_uiState.value.isLoading) return
        _uiState.update {
            it.copy(
                isLoading = true,
                userMessageRes = null,
            )
        }
        viewModelScope.launch {
            try {
                operation()
                _uiState.value = AuthState(
                    isAuthenticated = authenticatedOnSuccess,
                    userMessageRes = successMessageRes,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.value = AuthState(
                    userMessageRes = errorMessageRes(error),
                )
            }
        }
    }

    private fun showMessage(@StringRes messageRes: Int) {
        _uiState.update {
            it.copy(
                isLoading = false,
                userMessageRes = messageRes,
            )
        }
    }
}
