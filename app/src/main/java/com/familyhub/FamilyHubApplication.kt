package com.familyhub

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.familyhub.data.notification.EventNotificationManager
import com.familyhub.data.notification.MealPlanNotificationManager
import com.familyhub.data.notification.ShoppingNotificationManager
import com.familyhub.data.notification.TaskNotificationManager
import com.familyhub.data.notification.TaskRotationScheduler
import com.familyhub.data.notification.TaskNotificationScheduler
import com.familyhub.data.sync.CloudSyncScheduler
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.TaskRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.familyhub.di.ApplicationScope
import com.familyhub.data.repository.InitializationRepository
import timber.log.Timber

@HiltAndroidApp
class FamilyHubApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var initializationRepository: InitializationRepository
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var taskRotationScheduler: TaskRotationScheduler

    @Inject
    lateinit var cloudSyncScheduler: CloudSyncScheduler

    @Inject
    lateinit var taskNotificationScheduler: TaskNotificationScheduler

    @Inject
    lateinit var appSettingsDataStore: AppSettingsDataStore

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var syncChangeTracker: com.familyhub.data.sync.SyncChangeTracker

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        // Инициализируем Timber (для release при необходимости можно заменить на no-op)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Создаем канал уведомлений
        TaskNotificationManager.createNotificationChannel(this)
        EventNotificationManager.createNotificationChannel(this)
        MealPlanNotificationManager.createNotificationChannel(this)
        ShoppingNotificationManager.createNotificationChannel(this)
        
        // Выполняем безопасную одноразовую инициализацию локальных данных.
        // Hilt инжектит зависимости после super.onCreate(), поэтому delay не нужен.
        applicationScope.launch {
            if (::initializationRepository.isInitialized) {
                initializationRepository.initializeIfNeeded()
            }
            if (::taskRotationScheduler.isInitialized) {
                taskRotationScheduler.schedulePeriodicRotationCheck()
            }

            if (
                ::taskNotificationScheduler.isInitialized &&
                ::appSettingsDataStore.isInitialized &&
                ::taskRepository.isInitialized
            ) {
                val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
                if (currentUserId.isNotBlank()) {
                    taskRepository.getAllTasks().first().forEach { task ->
                        taskNotificationScheduler.scheduleTaskDeadlineNotification(task, currentUserId)
                    }
                }
            }

            if (::taskNotificationScheduler.isInitialized) {
                taskNotificationScheduler.schedulePeriodicCheck()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() {
            val resolvedFactory = if (::workerFactory.isInitialized) {
                workerFactory
            } else {
                EntryPointAccessors.fromApplication(this, WorkerFactoryEntryPoint::class.java)
                    .workerFactory()
            }
            return Configuration.Builder()
                .setWorkerFactory(resolvedFactory)
                .build()
        }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }
}
