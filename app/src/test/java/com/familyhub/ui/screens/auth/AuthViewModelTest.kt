package com.familyhub.ui.screens.auth

import com.familyhub.R
import com.familyhub.data.analytics.ActivationAnalytics
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val auth: FirebaseAuth = mockk()
    private val activationAnalytics: ActivationAnalytics = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun normalizeEmail_trimsAndUsesLocaleIndependentLowercase() {
        assertEquals(
            "user@example.com",
            AuthInputValidator.normalizeEmail("  USER@EXAMPLE.COM  "),
        )
    }

    @Test
    fun credentials_rejectInvalidEmailBeforeFirebaseCall() = runTest {
        val viewModel = AuthViewModel(auth, activationAnalytics)

        viewModel.signIn("not-an-email", "password")
        advanceUntilIdle()

        assertEquals(R.string.auth_error_email_invalid, viewModel.uiState.value.userMessageRes)
        verify(exactly = 0) { auth.signInWithEmailAndPassword(any(), any()) }
    }

    @Test
    fun credentials_requireSixCharactersOnlyForRegistration() {
        val (signIn, signInError) = AuthInputValidator.credentials(
            email = "user@example.com",
            password = "123",
            enforceMinimumPasswordLength = false,
        )
        val (signUp, signUpError) = AuthInputValidator.credentials(
            email = "user@example.com",
            password = "123",
            enforceMinimumPasswordLength = true,
        )

        assertEquals("user@example.com", signIn?.email)
        assertNull(signInError)
        assertNull(signUp)
        assertEquals(R.string.auth_error_password_too_short, signUpError)
    }

    @Test
    fun sendPasswordReset_normalizesEmailAndPublishesLocalizedSuccess() = runTest {
        every {
            auth.sendPasswordResetEmail("user@example.com")
        } returns Tasks.forResult(null)
        val viewModel = AuthViewModel(auth, activationAnalytics)

        viewModel.sendPasswordReset("  USER@EXAMPLE.COM ")
        advanceUntilIdle()

        verify(exactly = 1) { auth.sendPasswordResetEmail("user@example.com") }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(R.string.auth_password_reset_sent, viewModel.uiState.value.userMessageRes)
    }

    @Test
    fun authErrors_mapCredentialFailuresWithoutAccountEnumeration() {
        val codes = listOf(
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND",
        )

        assertTrue(
            codes.all {
                FirebaseAuthErrorMapper.messageResForCode(it) ==
                    R.string.auth_error_invalid_credentials
            },
        )
    }

    @Test
    fun authErrors_unknownCodeUsesSafeLocalizedFallback() {
        assertEquals(
            R.string.auth_error_unknown,
            FirebaseAuthErrorMapper.messageResForCode("ERROR_FUTURE_SERVER_CODE"),
        )
    }

    @Test
    fun passwordReset_userNotFoundKeepsNeutralSuccessMessage() {
        assertEquals(
            R.string.auth_password_reset_sent,
            FirebaseAuthErrorMapper.passwordResetMessageResForCode("ERROR_USER_NOT_FOUND"),
        )
    }

    @Test
    fun authViewModel_doesNotExposeThrowableMessages() {
        val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
        val source = File(
            repoRoot,
            "app/src/main/java/com/familyhub/ui/screens/auth/AuthViewModel.kt",
        ).readText(StandardCharsets.UTF_8)

        assertFalse(source.contains("localizedMessage"))
        assertFalse(source.contains("error.message"))
    }
}
