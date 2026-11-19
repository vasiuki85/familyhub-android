package com.familyhub.data.messaging

import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.notification.ShoppingNotificationManager
import com.familyhub.data.notification.ShoppingNotificationResult
import com.familyhub.di.ApplicationScope
import com.familyhub.di.IoDispatcher
import com.familyhub.domain.usecase.PerformSyncNowUseCase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles FCM data messages when a backend fan-out is deployed.
 * FamilyHub does not deploy Cloud Functions on the free Spark plan, so these handlers
 * are inactive in production until Blaze + Functions are enabled.
 */
@AndroidEntryPoint
class FamilyHubMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var performSyncNowUseCase: PerformSyncNowUseCase

    @Inject
    lateinit var shoppingNotificationManager: ShoppingNotificationManager

    @Inject
    lateinit var syncSettingsDataStore: SyncSettingsDataStore

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM token refreshed (no server upload on Spark plan)")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val type = message.data["type"]
        when (type) {
            TYPE_SYNC -> triggerSync(message)
            TYPE_SHOPPING_ACTIVITY -> handleShoppingActivity(message)
        }
    }

    private fun triggerSync(message: RemoteMessage) {
        try {
            val originDeviceId = message.data[KEY_DEVICE_ID]
            applicationScope.launch(ioDispatcher) {
                val localDeviceId = runCatching {
                    syncSettingsDataStore.settingsFlow.first().deviceId
                }.getOrNull()

                if (!originDeviceId.isNullOrBlank() && originDeviceId == localDeviceId) {
                    return@launch
                }

                performSyncNowUseCase()
            }
        } catch (t: Throwable) {
            Timber.w(t, "Failed to trigger background sync from FCM")
        }
    }

    private fun handleShoppingActivity(message: RemoteMessage) {
        val title = message.data[KEY_TITLE].orEmpty()
        val body = message.data[KEY_MESSAGE].orEmpty()
        if (title.isBlank() || body.isBlank()) return

        val originDeviceId = message.data[KEY_DEVICE_ID]

        applicationScope.launch(ioDispatcher) {
            val localDeviceId = runCatching {
                syncSettingsDataStore.settingsFlow.first().deviceId
            }.getOrNull()

            if (!originDeviceId.isNullOrBlank() && originDeviceId == localDeviceId) {
                return@launch
            }

            when (shoppingNotificationManager.showActivityNotification(title, body)) {
                ShoppingNotificationResult.SHOWN -> Unit
                ShoppingNotificationResult.PERMISSION_REQUIRED -> Timber.d("Shopping activity notification requires permission")
                ShoppingNotificationResult.NOTIFICATIONS_DISABLED -> Timber.d("Shopping activity notifications disabled")
            }
        }
    }

    companion object {
        private const val TYPE_SYNC = "sync"
        private const val TYPE_SHOPPING_ACTIVITY = "shopping_activity"
        private const val KEY_TITLE = "title"
        private const val KEY_MESSAGE = "message"
        private const val KEY_DEVICE_ID = "deviceId"
    }
}
