package com.familyhub.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.familyhub.R
import com.familyhub.ui.navigation.NavRoutes
import com.familyhub.ui.theme.FhTheme

@Composable
fun AuthScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val userMessage = state.userMessageRes?.let { stringResource(it) }

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            navController.navigate(NavRoutes.HOME) {
                popUpTo(NavRoutes.AUTH) { inclusive = true }
            }
        }
    }

    LaunchedEffect(state.userMessageRes) {
        userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FhTheme.spacing.xl, vertical = FhTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AuthBrand()
                Spacer(modifier = Modifier.height(FhTheme.spacing.xl))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FhTheme.radius.card),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    AuthTabs(
                        viewModel = viewModel,
                        isLoading = state.isLoading,
                        modifier = Modifier.padding(FhTheme.spacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthBrand() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.FamilyRestroom,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
        }
    }
    Spacer(modifier = Modifier.height(FhTheme.spacing.md))
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.auth_v2_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AuthTabs(
    viewModel: AuthViewModel,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.auth_tab_signin),
        stringResource(R.string.auth_tab_signup),
    )

    Column(modifier = modifier) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, style = MaterialTheme.typography.titleSmall) },
                    enabled = !isLoading,
                )
            }
        }
        Spacer(modifier = Modifier.height(FhTheme.spacing.lg))

        when (selectedTabIndex) {
            0 -> AuthForm(
                actionLabel = stringResource(R.string.auth_tab_signin),
                isLoading = isLoading,
                enforceMinimumPasswordLength = false,
                onSubmit = viewModel::signIn,
                onForgotPassword = viewModel::sendPasswordReset,
            )
            else -> AuthForm(
                actionLabel = stringResource(R.string.auth_tab_signup),
                isLoading = isLoading,
                enforceMinimumPasswordLength = true,
                onSubmit = viewModel::signUp,
            )
        }
    }
}

@Composable
private fun AuthForm(
    actionLabel: String,
    isLoading: Boolean,
    enforceMinimumPasswordLength: Boolean,
    onSubmit: (String, String) -> Unit,
    onForgotPassword: ((String) -> Unit)? = null,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var emailErrorRes by remember { mutableStateOf<Int?>(null) }
    var passwordErrorRes by remember { mutableStateOf<Int?>(null) }
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val submit = {
        val emailErr = AuthInputValidator.emailError(email)
        val passwordErr = AuthInputValidator.passwordError(password, enforceMinimumPasswordLength)
        emailErrorRes = emailErr
        passwordErrorRes = passwordErr
        when {
            emailErr != null -> emailFocusRequester.requestFocus()
            passwordErr != null -> passwordFocusRequester.requestFocus()
            else -> {
                focusManager.clearFocus()
                onSubmit(email.trim(), password)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailErrorRes = null
            },
            label = { Text(stringResource(R.string.auth_email_label)) },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Email, contentDescription = null)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            enabled = !isLoading,
            isError = emailErrorRes != null,
            supportingText = emailErrorRes?.let { res -> { Text(stringResource(res)) } },
            shape = RoundedCornerShape(FhTheme.radius.field),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocusRequester),
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordErrorRes = null
            },
            label = { Text(stringResource(R.string.auth_password_label)) },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Rounded.VisibilityOff
                        } else {
                            Icons.Rounded.Visibility
                        },
                        contentDescription = stringResource(
                            if (passwordVisible) {
                                R.string.auth_v2_hide_password_cd
                            } else {
                                R.string.auth_v2_show_password_cd
                            },
                        ),
                    )
                }
            },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (!isLoading) submit() },
            ),
            singleLine = true,
            enabled = !isLoading,
            isError = passwordErrorRes != null,
            supportingText = passwordErrorRes?.let { res -> { Text(stringResource(res)) } },
            shape = RoundedCornerShape(FhTheme.radius.field),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester),
        )
        if (onForgotPassword != null) {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onForgotPassword(email)
                },
                enabled = !isLoading,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.auth_forgot_password))
            }
        }
        Button(
            onClick = submit,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(FhTheme.radius.button),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
