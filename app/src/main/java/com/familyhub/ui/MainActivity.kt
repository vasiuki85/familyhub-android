package com.familyhub.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.notification.TaskNotificationScheduler
import com.familyhub.data.repository.TaskRepository
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.ThemeMode
import com.familyhub.domain.usecase.PerformSyncNowUseCase
import com.familyhub.ui.navigation.AuthenticatedDeepLinkGate
import com.familyhub.ui.navigation.BottomBarVisibilityPolicy
import com.familyhub.ui.navigation.BottomNavigationBar
import com.familyhub.ui.navigation.FamilyHubNavHost
import com.familyhub.ui.navigation.JoinDeepLinkPolicy
import com.familyhub.ui.navigation.NavRoutes
import com.familyhub.ui.screens.lock.LockScreen
import com.familyhub.ui.theme.FamilyHubTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainActivity"
private const val KEY_INITIAL_DEEP_LINK_CONSUMED = "initial_deep_link_consumed"
private const val KEY_PENDING_DEEP_LINK_ACTIONS = "pending_deep_link_actions"
private const val KEY_PENDING_DEEP_LINK_URIS = "pending_deep_link_uris"
private const val KEY_PENDING_DEEP_LINK_INITIAL_FLAGS = "pending_deep_link_initial_flags"

private data class PendingJoinDeepLink(
    val intent: Intent,
    val isInitialIntent: Boolean,
)

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var appSettingsDataStore: AppSettingsDataStore

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var taskNotificationScheduler: TaskNotificationScheduler

    @Inject
    lateinit var performSyncNowUseCase: PerformSyncNowUseCase

    private var navControllerRef: NavHostController? = null
    private val joinDeepLinkGate = AuthenticatedDeepLinkGate<PendingJoinDeepLink>()
    private var initialDeepLinkConsumed: Boolean = false
    private var startupDeepLinksOffered: Boolean = false
    private var startupPendingDeepLinks: List<PendingJoinDeepLink> = emptyList()
    private val pendingDeepLinksForRecreation = mutableListOf<PendingJoinDeepLink>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialDeepLinkConsumed =
            savedInstanceState?.getBoolean(KEY_INITIAL_DEEP_LINK_CONSUMED) == true
        val restoredPending = restorePendingJoinDeepLinks(savedInstanceState)
        val startIntent = Intent(intent)
        val capturedInitial = if (
            !initialDeepLinkConsumed &&
            JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                startIntent.action,
                startIntent.dataString,
            ) &&
            restoredPending.isEmpty()
        ) {
            PendingJoinDeepLink(
                intent = Intent(startIntent),
                isInitialIntent = true,
            )
        } else {
            null
        }
        startupPendingDeepLinks = buildList {
            addAll(restoredPending)
            capturedInitial?.let(::add)
        }
        pendingDeepLinksForRecreation.addAll(startupPendingDeepLinks)
        protectJoinDeepLinkFromAutomaticNavigation(startIntent)

        setContent {
            val settings by appSettingsDataStore.settingsFlow.collectAsState(
                initial = AppSettings(
                    currentUserId = "",
                    themeMode = ThemeMode.SYSTEM,
                    biometricEnabled = false,
                    activeShoppingListId = "",
                )
            )

            FamilyHubTheme(
                themeMode = settings.themeMode,
                customJson = settings.themeCustomJson,
            ) {
                var isUnlocked by remember(settings.biometricEnabled) {
                    mutableStateOf(!settings.biometricEnabled)
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, settings.biometricEnabled) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP && settings.biometricEnabled) {
                            isUnlocked = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val requestExactAlarm = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    rescheduleTaskReminders()
                }
                val openExactAlarmSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                        if (!alarmManager.canScheduleExactAlarms()) {
                            requestExactAlarm.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:$packageName"),
                                )
                            )
                        } else {
                            rescheduleTaskReminders()
                        }
                    }
                }
                val requestNotifications = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {
                    openExactAlarmSettings()
                }
                LaunchedEffect(Unit) {
                    val notificationGranted =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                    if (!notificationGranted) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openExactAlarmSettings()
                    }
                }

                if (!isUnlocked && settings.biometricEnabled) {
                    LockScreen(
                        activity = this,
                        onUnlock = { isUnlocked = true },
                        onBiometricUnavailable = {
                            lifecycleScope.launch {
                                appSettingsDataStore.setBiometricEnabled(false)
                                isUnlocked = true
                            }
                        },
                    )
                } else {
                    val navController = rememberNavController()
                    val startDestination = if (firebaseAuth.currentUser != null) {
                        NavRoutes.HOME
                    } else {
                        NavRoutes.AUTH
                    }

                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route
                    val showBottomBar = BottomBarVisibilityPolicy.shouldShow(currentRoute)

                    DisposableEffect(navController) {
                        navControllerRef = navController
                        processApprovedDeepLinks(
                            joinDeepLinkGate.updateNavigatorAttached(true)
                        )
                        onDispose {
                            joinDeepLinkGate.updateNavigatorAttached(false)
                            if (navControllerRef === navController) {
                                navControllerRef = null
                            }
                        }
                    }

                    SideEffect {
                        processApprovedDeepLinks(
                            joinDeepLinkGate.updateDestination(
                                authenticatedDestinationVisible =
                                    currentRoute != null && currentRoute != NavRoutes.AUTH
                            )
                        )
                    }

                    DisposableEffect(firebaseAuth, navController) {
                        val listener = FirebaseAuth.AuthStateListener { auth ->
                            val authenticated = auth.currentUser != null
                            processApprovedDeepLinks(
                                joinDeepLinkGate.updateAuthenticated(authenticated)
                            )
                            if (
                                !authenticated &&
                                navController.currentDestination?.route != NavRoutes.AUTH
                            ) {
                                navController.navigate(NavRoutes.AUTH) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                        firebaseAuth.addAuthStateListener(listener)
                        onDispose { firebaseAuth.removeAuthStateListener(listener) }
                    }

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar) {
                                BottomNavigationBar(
                                    navController = navController,
                                    selectedRoutes = settings.bottomNavRoutes,
                                )
                            }
                        },
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        ) {
                            FamilyHubNavHost(
                                navController = navController,
                                startDestination = startDestination,
                            )

                            LaunchedEffect(navController) {
                                offerStartupPendingDeepLinksOnce()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            performSyncNowUseCase()
        }
    }

    private fun rescheduleTaskReminders() {
        lifecycleScope.launch {
            val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
            if (currentUserId.isBlank()) return@launch
            taskRepository.getAllTasks().first().forEach { task ->
                taskNotificationScheduler.scheduleTaskDeadlineNotification(task, currentUserId)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_INITIAL_DEEP_LINK_CONSUMED, initialDeepLinkConsumed)
        outState.putStringArrayList(
            KEY_PENDING_DEEP_LINK_ACTIONS,
            ArrayList(pendingDeepLinksForRecreation.map { it.intent.action.orEmpty() }),
        )
        outState.putStringArrayList(
            KEY_PENDING_DEEP_LINK_URIS,
            ArrayList(pendingDeepLinksForRecreation.map { it.intent.dataString.orEmpty() }),
        )
        outState.putBooleanArray(
            KEY_PENDING_DEEP_LINK_INITIAL_FLAGS,
            pendingDeepLinksForRecreation.map { it.isInitialIntent }.toBooleanArray(),
        )
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { newIntent ->
            val capturedIntent = Intent(newIntent)
            if (protectJoinDeepLinkFromAutomaticNavigation(capturedIntent)) {
                offerJoinDeepLink(
                    PendingJoinDeepLink(
                        intent = capturedIntent,
                        isInitialIntent = false,
                    )
                )
            } else {
                setIntent(capturedIntent)
                navControllerRef?.processDeepLinkSafely(capturedIntent)
            }
        }
    }

    /**
     * Compose Navigation inspects the Activity intent while installing its graph. Protected join
     * data must therefore be removed before [setContent], not only deferred at our explicit
     * `handleDeepLink` call.
     */
    private fun protectJoinDeepLinkFromAutomaticNavigation(intent: Intent): Boolean {
        if (!JoinDeepLinkPolicy.isSupportedJoinDeepLink(intent.action, intent.dataString)) {
            return false
        }
        setIntent(
            Intent(intent).apply {
                action = null
                data = null
                replaceExtras(Bundle())
            }
        )
        return true
    }

    private fun offerStartupPendingDeepLinksOnce() {
        if (startupDeepLinksOffered) return
        startupDeepLinksOffered = true
        startupPendingDeepLinks.forEach { pending ->
            offerJoinDeepLink(pending, trackForRecreation = false)
        }
        startupPendingDeepLinks = emptyList()
    }

    private fun offerJoinDeepLink(
        pending: PendingJoinDeepLink,
        trackForRecreation: Boolean = true,
    ) {
        if (
            !JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                pending.intent.action,
                pending.intent.dataString,
            )
        ) {
            return
        }
        if (trackForRecreation) {
            pendingDeepLinksForRecreation.add(pending)
        }
        processApprovedDeepLinks(joinDeepLinkGate.offer(pending))
    }

    private fun processApprovedDeepLinks(pendingIntents: List<PendingJoinDeepLink>) {
        if (pendingIntents.isEmpty()) return
        val navController = navControllerRef ?: return
        pendingIntents.forEach { pending ->
            navController.processDeepLinkSafely(pending.intent)
            pendingDeepLinksForRecreation.remove(pending)
            if (pending.isInitialIntent) {
                initialDeepLinkConsumed = true
            }
        }
    }

    private fun restorePendingJoinDeepLinks(state: Bundle?): List<PendingJoinDeepLink> {
        val actions = state?.getStringArrayList(KEY_PENDING_DEEP_LINK_ACTIONS).orEmpty()
        val uris = state?.getStringArrayList(KEY_PENDING_DEEP_LINK_URIS).orEmpty()
        val initialFlags =
            state?.getBooleanArray(KEY_PENDING_DEEP_LINK_INITIAL_FLAGS) ?: booleanArrayOf()
        val size = minOf(actions.size, uris.size, initialFlags.size)

        return buildList(size) {
            repeat(size) { index ->
                val restoredIntent = Intent().apply {
                    action = actions[index].takeIf { it.isNotBlank() }
                    data = Uri.parse(uris[index])
                }
                if (
                    JoinDeepLinkPolicy.isSupportedJoinDeepLink(
                        restoredIntent.action,
                        restoredIntent.dataString,
                    )
                ) {
                    add(
                        PendingJoinDeepLink(
                            intent = restoredIntent,
                            isInitialIntent = initialFlags[index],
                        )
                    )
                }
            }
        }
    }
}

private fun NavHostController.processDeepLinkSafely(intent: Intent) {
    runCatching { handleDeepLink(intent) }
        .onFailure { Log.w(TAG, "Deep link handling failed", it) }
}
