package com.familyhub.data.security

import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricCapabilityLogicTest {

    @Test
    fun allowedAuthenticators_prefersStrongBiometricWithDeviceCredentialFallback() {
        assertEquals(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            BiometricCapability.allowedAuthenticatorsFor(AuthenticatorStatus.STRONG_BIOMETRIC)
        )
    }

    @Test
    fun allowedAuthenticators_usesDeviceCredentialOnlyWhenNoBiometrics() {
        assertEquals(
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            BiometricCapability.allowedAuthenticatorsFor(AuthenticatorStatus.DEVICE_CREDENTIAL_ONLY)
        )
    }
}
