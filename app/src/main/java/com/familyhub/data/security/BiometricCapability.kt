package com.familyhub.data.security

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.biometric.BiometricManager

object BiometricCapability {
    fun canAuthenticate(context: Context): Boolean {
        return authenticatorStatus(context) != AuthenticatorStatus.NONE
    }

    fun allowedAuthenticators(context: Context): Int {
        return allowedAuthenticatorsFor(authenticatorStatus(context))
    }

    @VisibleForTesting
    internal fun allowedAuthenticatorsFor(status: AuthenticatorStatus): Int {
        return when (status) {
            AuthenticatorStatus.STRONG_BIOMETRIC ->
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            AuthenticatorStatus.WEAK_BIOMETRIC ->
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            AuthenticatorStatus.DEVICE_CREDENTIAL_ONLY ->
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            AuthenticatorStatus.NONE ->
                BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
    }

    fun isDeviceCredentialOnly(context: Context): Boolean {
        return authenticatorStatus(context) == AuthenticatorStatus.DEVICE_CREDENTIAL_ONLY
    }

    internal fun authenticatorStatus(context: Context): AuthenticatorStatus {
        val biometricManager = BiometricManager.from(context)
        return when {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS ->
                AuthenticatorStatus.STRONG_BIOMETRIC
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS ->
                AuthenticatorStatus.WEAK_BIOMETRIC
            biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS ->
                AuthenticatorStatus.DEVICE_CREDENTIAL_ONLY
            else -> AuthenticatorStatus.NONE
        }
    }
}

internal enum class AuthenticatorStatus {
    STRONG_BIOMETRIC,
    WEAK_BIOMETRIC,
    DEVICE_CREDENTIAL_ONLY,
    NONE
}
