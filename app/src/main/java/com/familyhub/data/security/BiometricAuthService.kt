package com.familyhub.data.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.familyhub.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BiometricAuthService(private val activity: FragmentActivity) {

    fun canAuthenticate(): Boolean = BiometricCapability.canAuthenticate(activity)

    suspend fun authenticate(): Boolean = suspendCancellableCoroutine { continuation ->
        if (!canAuthenticate()) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        val allowedAuthenticators = BiometricCapability.allowedAuthenticators(activity)
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }

                override fun onAuthenticationFailed() {
                    // Keep the system prompt open; the user can retry.
                }
            }
        )

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(allowedAuthenticators)

        if (allowedAuthenticators != BiometricManager.Authenticators.DEVICE_CREDENTIAL) {
            promptBuilder.setNegativeButtonText(activity.getString(R.string.biometric_prompt_cancel))
        }

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }

        biometricPrompt.authenticate(promptBuilder.build())
    }
}
