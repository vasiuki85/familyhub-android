package com.familyhub.ui.screens.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.familyhub.R
import com.familyhub.data.security.BiometricAuthService
import com.familyhub.ui.theme.FhTheme
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    activity: FragmentActivity,
    onUnlock: () -> Unit,
    onBiometricUnavailable: () -> Unit,
) {
    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val biometricService = remember(activity) { BiometricAuthService(activity) }
    val canAuthenticate = remember(activity) { biometricService.canAuthenticate() }
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f

    suspend fun runAuthentication() {
        if (!canAuthenticate) return
        isAuthenticating = true
        errorMessage = null
        val success = biometricService.authenticate()
        isAuthenticating = false
        if (success) {
            onUnlock()
        } else {
            errorMessage = activity.getString(R.string.lock_authentication_failed)
        }
    }

    LaunchedEffect(canAuthenticate) {
        if (!canAuthenticate) {
            onBiometricUnavailable()
        } else {
            runAuthentication()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        if (isLightTheme) {
            Image(
                painter = painterResource(R.drawable.fh_background_warm_v2),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.14f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(FhTheme.spacing.xxl),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.size(FhTheme.spacing.lg))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.lock_use_biometrics),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = FhTheme.spacing.sm),
            )
            Spacer(modifier = Modifier.size(FhTheme.spacing.xl))

            if (isAuthenticating) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.lock_waiting_authentication),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = FhTheme.spacing.md),
                )
            } else if (canAuthenticate) {
                Button(
                    onClick = { scope.launch { runAuthentication() } },
                    shape = RoundedCornerShape(FhTheme.radius.button),
                ) {
                    Text(stringResource(R.string.lock_action_sign_in))
                }
            }

            errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = FhTheme.spacing.lg),
                )
            }
        }
    }
}
