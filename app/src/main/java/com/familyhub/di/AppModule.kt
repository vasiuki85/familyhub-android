package com.familyhub.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.familyhub.data.export.MealPlanShareService
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.datastore.MealPlanPreferencesDataStore
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.datastore.AwesomenessDataStore
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.FamilyEventDao
import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.db.dao.ShoppingItemDao
import com.familyhub.data.local.db.dao.RewardDao
import com.familyhub.data.local.db.dao.RewardRequestDao
import com.familyhub.data.local.db.dao.TaskDao
import com.familyhub.data.local.db.dao.UserScoreDao
import com.familyhub.data.local.db.dao.TaskHistoryDao
import com.familyhub.data.local.db.dao.TaskCompletionDao
import com.familyhub.data.local.db.dao.UserAchievementDao
import com.familyhub.data.local.db.dao.UserWalletDao
import com.familyhub.data.local.db.dao.TaskRotationDao
import com.familyhub.data.local.db.dao.TaskTemplateDao
import com.familyhub.data.local.db.dao.TaskCategoryDao
import com.familyhub.data.local.db.dao.AppreciationDao
import com.familyhub.data.local.db.dao.HouseRuleDao
import com.familyhub.data.local.db.dao.FamilyGoalDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.RecipeDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.db.dao.EmergencyContactDao
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.notification.ShoppingNotificationManager
import com.familyhub.data.notification.TaskNotificationManager
import com.familyhub.data.notification.TaskNotificationScheduler
import com.familyhub.data.notification.EventNotificationManager
import com.familyhub.data.notification.EventReminderScheduler
import com.familyhub.data.notification.TaskRotationScheduler
import com.familyhub.data.notification.MealPlanNotificationManager
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.AuthenticatedMemberBootstrapCoordinator
import com.familyhub.data.repository.BuiltInRecipeSeeder
import com.familyhub.data.repository.InitializationRepository
import com.familyhub.data.repository.LocalDataWiper
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.data.repository.RewardRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.TaskHistoryRepository
import com.familyhub.data.repository.TaskRotationRepository
import com.familyhub.data.repository.TaskTemplateRepository
import com.familyhub.data.repository.TaskCategoryRepository
import com.familyhub.data.repository.AppreciationRepository
import com.familyhub.data.repository.HouseRulesRepository
import com.familyhub.data.repository.FamilyGoalsRepository
import com.familyhub.data.repository.RecipeRepository
import com.familyhub.data.repository.MenuMutationCoordinator
import com.familyhub.data.repository.WeeklyMenuRepository
import com.familyhub.domain.utils.DefaultMenuMutationClock
import com.familyhub.domain.utils.MenuMutationClock
import com.familyhub.data.repository.DailyAssignmentRepository
import com.familyhub.data.repository.AwesomenessRepository
import com.familyhub.data.repository.EmergencyContactRepository
import com.familyhub.data.repository.PenaltyRepository
import com.familyhub.data.repository.PenaltyTransitionCoordinator
import com.familyhub.data.history.AndroidTaskCompletionFeedbackProvider
import com.familyhub.data.history.AndroidTaskHistoryTextProvider
import com.familyhub.data.sync.CloudBackendApi
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.data.sync.CloudDeletionLedgerPort
import com.familyhub.data.sync.FirestoreBackendApi
import com.familyhub.data.sync.GoogleCalendarService
import com.familyhub.data.sync.CalendarService
import com.familyhub.data.sync.SyncExclusiveLock
import com.familyhub.data.sync.SYNC_OUTBOX_DATABASE_CALLBACK
import com.familyhub.data.sync.installSyncOutboxInfrastructure
import com.familyhub.domain.usecase.TaskCompletionFeedbackProvider
import com.familyhub.domain.usecase.TaskHistoryTextProvider
import com.familyhub.domain.utils.IdGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton
import com.familyhub.data.local.db.dao.PenaltyDao

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): FamilyHubDatabase {
        return Room.databaseBuilder(
            context,
            FamilyHubDatabase::class.java,
            "familyhub.db"
        ).addMigrations(
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36,
            MIGRATION_36_37,
            MIGRATION_37_38,
            MIGRATION_38_39,
        )
        .addCallback(SYNC_OUTBOX_DATABASE_CALLBACK)
        .build()
    }

    @Provides
    @Singleton
    fun provideCloudDeletionLedgerPort(ledger: CloudDeletionLedger): CloudDeletionLedgerPort = ledger

    @Provides
    @Singleton
    fun provideMenuTemplateDao(database: FamilyHubDatabase): MenuTemplateDao =
        database.menuTemplateDao()

    @Provides
    @Singleton
    fun provideDailyAssignmentDao(database: FamilyHubDatabase): DailyAssignmentDao =
        database.dailyAssignmentDao()

    @Provides
    @Singleton
    fun providePenaltyDao(database: FamilyHubDatabase): PenaltyDao =
        database.penaltyDao()

    @Provides fun provideFamilyMemberDao(db: FamilyHubDatabase): FamilyMemberDao = db.familyMemberDao()
    @Provides fun provideTaskDao(db: FamilyHubDatabase): TaskDao = db.taskDao()
    @Provides fun provideShoppingItemDao(db: FamilyHubDatabase): ShoppingItemDao = db.shoppingItemDao()
    @Provides fun provideFamilyEventDao(db: FamilyHubDatabase): FamilyEventDao = db.familyEventDao()
    @Provides fun provideUserScoreDao(db: FamilyHubDatabase): UserScoreDao = db.userScoreDao()
    @Provides fun provideTaskHistoryDao(db: FamilyHubDatabase): TaskHistoryDao = db.taskHistoryDao()
    @Provides fun provideTaskCompletionDao(db: FamilyHubDatabase): TaskCompletionDao = db.taskCompletionDao()
    @Provides fun provideUserAchievementDao(db: FamilyHubDatabase): UserAchievementDao = db.userAchievementDao()
    @Provides fun provideUserWalletDao(db: FamilyHubDatabase): UserWalletDao = db.userWalletDao()
    @Provides fun provideRewardDao(db: FamilyHubDatabase): RewardDao = db.rewardDao()
    @Provides fun provideRewardRequestDao(db: FamilyHubDatabase): RewardRequestDao = db.rewardRequestDao()
    @Provides fun provideTaskRotationDao(db: FamilyHubDatabase): TaskRotationDao = db.taskRotationDao()
    @Provides fun provideHouseRuleDao(db: FamilyHubDatabase): HouseRuleDao = db.houseRuleDao()
    @Provides fun provideTaskTemplateDao(db: FamilyHubDatabase): TaskTemplateDao = db.taskTemplateDao()
    @Provides fun provideTaskCategoryDao(db: FamilyHubDatabase): TaskCategoryDao = db.taskCategoryDao()
    @Provides fun provideAppreciationDao(db: FamilyHubDatabase): AppreciationDao = db.appreciationDao()
    @Provides fun provideFamilyGoalDao(db: FamilyHubDatabase): FamilyGoalDao = db.familyGoalDao()
    @Provides fun provideRecipeDao(db: FamilyHubDatabase): RecipeDao = db.recipeDao()
    @Provides fun provideWeeklyMenuDao(db: FamilyHubDatabase): WeeklyMenuDao = db.weeklyMenuDao()
    @Provides fun provideEmergencyContactDao(db: FamilyHubDatabase): EmergencyContactDao = db.emergencyContactDao()
    @Provides
    @Singleton
    fun provideAppSettingsDataStore(@ApplicationContext context: Context): AppSettingsDataStore {
        return AppSettingsDataStore(context)
    }

    @Provides
    fun provideMealPlanPreferencesDataStore(
        @ApplicationContext context: Context
    ): MealPlanPreferencesDataStore {
        return MealPlanPreferencesDataStore(context)
    }

    @Provides
    @Singleton
    fun provideSyncSettingsDataStore(@ApplicationContext context: Context): SyncSettingsDataStore {
        return SyncSettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideAwesomenessDataStore(@ApplicationContext context: Context): AwesomenessDataStore {
        return AwesomenessDataStore(context)
    }

    @Provides
    @Singleton
    fun provideTaskRepository(
        taskDao: TaskDao,
        cloudDeletionLedger: CloudDeletionLedger,
        database: FamilyHubDatabase,
    ): TaskRepository {
        return TaskRepository(taskDao, cloudDeletionLedger, database)
    }

    @Provides
    @Singleton
    fun provideFamilyMemberRepository(memberDao: FamilyMemberDao): FamilyMemberRepository {
        return FamilyMemberRepository(memberDao)
    }

    @Provides
    @Singleton
    fun provideInitializationRepository(
        memberRepository: FamilyMemberRepository,
        localDataWiper: LocalDataWiper,
        appSettingsDataStore: AppSettingsDataStore,
        syncSettingsDataStore: SyncSettingsDataStore,
        syncExclusiveLock: SyncExclusiveLock,
        authenticatedMemberBootstrapCoordinator: AuthenticatedMemberBootstrapCoordinator,
        builtInRecipeSeeder: BuiltInRecipeSeeder,
        firebaseAuth: FirebaseAuth,
        @ApplicationContext context: Context
    ): InitializationRepository {
        return InitializationRepository(
            memberRepository = memberRepository,
            localDataWiper = localDataWiper,
            appSettingsDataStore = appSettingsDataStore,
            syncSettingsDataStore = syncSettingsDataStore,
            syncExclusiveLock = syncExclusiveLock,
            authenticatedMemberBootstrapCoordinator = authenticatedMemberBootstrapCoordinator,
            builtInRecipeSeeder = builtInRecipeSeeder,
            firebaseAuth = firebaseAuth,
            context = context,
        )
    }
    
    @Provides
    @Singleton
    fun provideTaskNotificationManager(
        @ApplicationContext context: Context
    ): TaskNotificationManager {
        return TaskNotificationManager(context)
    }

    @Provides
    @Singleton
    fun provideTaskNotificationScheduler(
        @ApplicationContext context: Context
    ): TaskNotificationScheduler {
        return TaskNotificationScheduler(context)
    }


    @Provides
    @Singleton
    fun provideMealPlanNotificationManager(
        @ApplicationContext context: Context
    ): MealPlanNotificationManager {
        return MealPlanNotificationManager(context)
    }

    @Provides
    @Singleton
    fun provideTaskRotationScheduler(
        @ApplicationContext context: Context
    ): TaskRotationScheduler {
        return TaskRotationScheduler(context)
    }

    @Provides
    @Singleton
    fun provideEventNotificationManager(
        @ApplicationContext context: Context
    ): EventNotificationManager {
        return EventNotificationManager(context)
    }

    @Provides
    @Singleton
    fun provideShoppingNotificationManager(
        @ApplicationContext context: Context
    ): ShoppingNotificationManager {
        return ShoppingNotificationManager(context)
    }

    @Provides
    @Singleton
    fun provideEventReminderScheduler(
        @ApplicationContext context: Context
    ): EventReminderScheduler {
        return EventReminderScheduler(context)
    }
    
    @Provides
    @Singleton
    fun provideDataExportService(
        @ApplicationContext context: Context,
        memberRepository: FamilyMemberRepository,
        taskRepository: TaskRepository,
        shoppingRepository: ShoppingRepository,
        eventRepository: FamilyEventRepository,
        scoreRepository: ScoreRepository,
        rewardRepository: RewardRepository,
        taskRotationRepository: TaskRotationRepository,
        houseRulesRepository: HouseRulesRepository,
        taskTemplateRepository: TaskTemplateRepository,
        taskCategoryRepository: TaskCategoryRepository,
        appreciationRepository: AppreciationRepository,
        familyGoalsRepository: FamilyGoalsRepository,
        emergencyContactRepository: EmergencyContactRepository,
        penaltyRepository: PenaltyRepository,
        recipeRepository: com.familyhub.data.repository.RecipeRepository,
        weeklyMenuDao: WeeklyMenuDao,
        menuTemplateDao: MenuTemplateDao,
        taskHistoryDao: TaskHistoryDao,
        dailyAssignmentDao: DailyAssignmentDao
    ): com.familyhub.data.export.DataExportService {
        return com.familyhub.data.export.DataExportService(
            context,
            memberRepository,
            taskRepository,
            shoppingRepository,
            eventRepository,
            scoreRepository,
            rewardRepository,
            taskRotationRepository,
            houseRulesRepository,
            taskTemplateRepository,
            taskCategoryRepository,
            appreciationRepository,
            familyGoalsRepository,
            emergencyContactRepository,
            penaltyRepository,
            recipeRepository,
            weeklyMenuDao,
            menuTemplateDao,
            taskHistoryDao,
            dailyAssignmentDao
        )
    }

    @Provides
    @Singleton
    fun provideMealPlanShareService(
        @ApplicationContext context: Context
    ): MealPlanShareService {
        return MealPlanShareService(context)
    }
    
    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator {
        return IdGenerator
    }
    
    @Provides
    @Singleton
    fun provideTaskHistoryRepository(
        historyDao: TaskHistoryDao
    ): TaskHistoryRepository {
        return TaskHistoryRepository(historyDao)
    }

    @Provides
    @Singleton
    fun provideTaskRotationRepository(
        rotationDao: TaskRotationDao,
        cloudDeletionLedger: CloudDeletionLedger
    ): TaskRotationRepository {
        return TaskRotationRepository(rotationDao, cloudDeletionLedger)
    }

    @Provides
    @Singleton
    fun provideHouseRulesRepository(
        houseRuleDao: HouseRuleDao,
        cloudDeletionLedger: CloudDeletionLedger
    ): HouseRulesRepository {
        return HouseRulesRepository(houseRuleDao, cloudDeletionLedger)
    }

    @Provides
    @Singleton
    fun provideTaskTemplateRepository(
        taskTemplateDao: TaskTemplateDao,
        cloudDeletionLedger: CloudDeletionLedger
    ): TaskTemplateRepository {
        return TaskTemplateRepository(taskTemplateDao, cloudDeletionLedger)
    }

    @Provides
    @Singleton
    fun provideTaskCategoryRepository(
        taskCategoryDao: TaskCategoryDao,
        cloudDeletionLedger: CloudDeletionLedger
    ): TaskCategoryRepository {
        return TaskCategoryRepository(taskCategoryDao, cloudDeletionLedger)
    }

    @Provides
    @Singleton
    fun provideAppreciationRepository(
        appreciationDao: AppreciationDao,
        cloudDeletionLedger: CloudDeletionLedger
    ): AppreciationRepository {
        return AppreciationRepository(appreciationDao, cloudDeletionLedger)
    }

    @Provides
    @Singleton
    fun provideFamilyGoalsRepository(
        familyGoalDao: FamilyGoalDao,
        cloudDeletionLedger: CloudDeletionLedger
    ): FamilyGoalsRepository {
        return FamilyGoalsRepository(familyGoalDao, cloudDeletionLedger)
    }

    @Provides
    @Singleton
    fun provideRecipeRepository(
        recipeDao: RecipeDao,
        syncSettingsDataStore: SyncSettingsDataStore
    ): RecipeRepository {
        return RecipeRepository(recipeDao, syncSettingsDataStore)
    }

    @Provides
    @Singleton
    fun provideMenuMutationClock(): MenuMutationClock = DefaultMenuMutationClock()

    @Provides
    @Singleton
    fun provideWeeklyMenuRepository(
        weeklyMenuDao: WeeklyMenuDao,
        menuTemplateDao: MenuTemplateDao,
        menuMutationCoordinator: MenuMutationCoordinator,
    ): WeeklyMenuRepository {
        return WeeklyMenuRepository(weeklyMenuDao, menuTemplateDao, menuMutationCoordinator)
    }

    @Provides
    @Singleton
    fun provideDailyAssignmentRepository(
        dailyAssignmentDao: DailyAssignmentDao,
        menuMutationCoordinator: MenuMutationCoordinator,
    ): DailyAssignmentRepository {
        return DailyAssignmentRepository(dailyAssignmentDao, menuMutationCoordinator)
    }

    @Provides
    @Singleton
    fun provideAwesomenessRepository(
        dataStore: AwesomenessDataStore
    ): AwesomenessRepository {
        return AwesomenessRepository(dataStore)
    }

    @Provides
    @Singleton
    fun provideEmergencyContactRepository(
        dao: EmergencyContactDao,
        cloudDeletionLedger: CloudDeletionLedger
    ): EmergencyContactRepository {
        return EmergencyContactRepository(dao, cloudDeletionLedger)
    }

    @Provides
    @Singleton
    fun providePenaltyRepository(
        penaltyDao: PenaltyDao,
        coordinator: PenaltyTransitionCoordinator
    ): PenaltyRepository {
        return PenaltyRepository(penaltyDao, coordinator)
    }

    @Provides
    @Singleton
    fun provideTaskHistoryTextProvider(
        androidProvider: AndroidTaskHistoryTextProvider
    ): TaskHistoryTextProvider {
        return androidProvider
    }

    @Provides
    @Singleton
    fun provideTaskCompletionFeedbackProvider(
        androidProvider: AndroidTaskCompletionFeedbackProvider
    ): TaskCompletionFeedbackProvider {
        return androidProvider
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }

    @Provides
    @Singleton
    fun provideCloudBackendApi(firestoreBackend: FirestoreBackendApi): CloudBackendApi {
        return firestoreBackend
    }

    @Provides
    @Singleton
    fun provideGoogleCalendarService(@ApplicationContext context: Context): GoogleCalendarService {
        return GoogleCalendarService(context)
    }

    @Provides
    @Singleton
    fun provideCalendarService(impl: GoogleCalendarService): CalendarService {
        return impl
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}


val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE family_events ADD COLUMN photoUrl TEXT")
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE family_events ADD COLUMN googleCalendarEventId TEXT")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE family_events ADD COLUMN description TEXT")
        database.execSQL("ALTER TABLE family_members ADD COLUMN age INTEGER")
        database.execSQL("ALTER TABLE family_members ADD COLUMN photoUrl TEXT")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS family_events_new (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                startDateTime INTEGER NOT NULL,
                createdByUserId TEXT NOT NULL,
                durationMinutes INTEGER NOT NULL,
                recurrence TEXT NOT NULL,
                reminderMinutesBefore INTEGER,
                googleCalendarEventId TEXT
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO family_events_new (
                id, title, description, startDateTime, createdByUserId, durationMinutes, recurrence, reminderMinutesBefore, googleCalendarEventId
            )
            SELECT id, title, description, startDateTime, createdByUserId, durationMinutes, recurrence, reminderMinutesBefore, googleCalendarEventId
            FROM family_events
            """.trimIndent()
        )
        database.execSQL("DROP TABLE family_events")
        database.execSQL("ALTER TABLE family_events_new RENAME TO family_events")
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        if (!hasColumn(database, "family_events", "description")) {
            database.execSQL("ALTER TABLE family_events ADD COLUMN description TEXT")
        }
        if (!hasColumn(database, "family_events", "googleCalendarEventId")) {
            database.execSQL("ALTER TABLE family_events ADD COLUMN googleCalendarEventId TEXT")
        }
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_lists (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                colorHex TEXT NOT NULL,
                iconName TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                isArchived INTEGER NOT NULL DEFAULT 0,
                archivedAt INTEGER,
                templateKey TEXT,
                participantIds TEXT NOT NULL DEFAULT '[]'
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_categories (
                id TEXT NOT NULL PRIMARY KEY,
                listId TEXT NOT NULL,
                title TEXT NOT NULL,
                colorHex TEXT NOT NULL,
                iconName TEXT NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_items_new (
                id TEXT NOT NULL PRIMARY KEY,
                listId TEXT NOT NULL,
                title TEXT NOT NULL,
                createdByUserId TEXT NOT NULL,
                isBought INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                categoryId TEXT,
                quantity REAL,
                quantityUnit TEXT,
                note TEXT,
                boughtAt INTEGER
            )
            """.trimIndent()
        )
        val defaultListId = "default_list"
        val now = System.currentTimeMillis()
        database.execSQL(
            """
            INSERT INTO shopping_items_new (
                id, listId, title, createdByUserId, isBought, createdAt, categoryId, quantity, quantityUnit, note, boughtAt
            )
            SELECT id, ?, title, createdByUserId, isBought, createdAt, NULL, NULL, NULL, NULL, NULL
            FROM shopping_items
            """.trimIndent(),
            arrayOf(defaultListId)
        )
        database.execSQL("DROP TABLE shopping_items")
        database.execSQL("ALTER TABLE shopping_items_new RENAME TO shopping_items")
        database.execSQL(
            """
            INSERT OR IGNORE INTO shopping_lists (id, title, colorHex, iconName, createdAt)
            VALUES (?, 'Основной', '#4CAF50', 'cart', ?)
            """.trimIndent(),
            arrayOf(defaultListId, now)
        )
        val defaultCategories = listOf(
            arrayOf("cat_produce", defaultListId, "Овощи и фрукты", "#4CAF50", "fruits", 0),
            arrayOf("cat_dairy", defaultListId, "Молочные", "#FFB300", "dairy", 1),
            arrayOf("cat_household", defaultListId, "Быт", "#607D8B", "household", 2)
        )
        defaultCategories.forEach { values ->
            database.execSQL(
                """
                INSERT OR IGNORE INTO shopping_categories (id, listId, title, colorHex, iconName, sortOrder)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                values
            )
        }
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_activity (
                id TEXT NOT NULL PRIMARY KEY,
                listId TEXT NOT NULL,
                itemId TEXT,
                actorId TEXT NOT NULL,
                actionType TEXT NOT NULL,
                payload TEXT,
                occurredAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        if (!hasColumn(database, "shopping_lists", "isArchived")) {
            database.execSQL("ALTER TABLE shopping_lists ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
        }
        if (!hasColumn(database, "shopping_lists", "archivedAt")) {
            database.execSQL("ALTER TABLE shopping_lists ADD COLUMN archivedAt INTEGER")
        }
        if (!hasColumn(database, "shopping_lists", "templateKey")) {
            database.execSQL("ALTER TABLE shopping_lists ADD COLUMN templateKey TEXT")
        }
        if (!hasColumn(database, "shopping_lists", "participantIds")) {
            database.execSQL("ALTER TABLE shopping_lists ADD COLUMN participantIds TEXT NOT NULL DEFAULT '[]'")
        }
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_activity (
                id TEXT NOT NULL PRIMARY KEY,
                listId TEXT NOT NULL,
                itemId TEXT,
                actorId TEXT NOT NULL,
                actionType TEXT NOT NULL,
                payload TEXT,
                occurredAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goal_stages (
                id TEXT NOT NULL PRIMARY KEY,
                goalId TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                position INTEGER NOT NULL,
                FOREIGN KEY(goalId) REFERENCES family_goals(id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_goal_stages_goalId ON goal_stages(goalId)")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goal_steps (
                id TEXT NOT NULL PRIMARY KEY,
                goalId TEXT NOT NULL,
                stageId TEXT NOT NULL,
                title TEXT NOT NULL,
                isCompleted INTEGER NOT NULL DEFAULT 0,
                position INTEGER NOT NULL,
                FOREIGN KEY(goalId) REFERENCES family_goals(id) ON DELETE CASCADE ON UPDATE CASCADE,
                FOREIGN KEY(stageId) REFERENCES goal_stages(id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_goal_steps_goalId ON goal_steps(goalId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_goal_steps_stageId ON goal_steps(stageId)")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create emergency_contacts table if missing
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS emergency_contacts (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                phoneNumber TEXT NOT NULL,
                description TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}


val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(database: SupportSQLiteDatabase) {
        if (!hasColumn(database, "tasks", "rewardPoints")) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN rewardPoints INTEGER DEFAULT NULL")
        }
        if (!hasColumn(database, "recipes", "category")) {
            database.execSQL("ALTER TABLE recipes ADD COLUMN category TEXT NOT NULL DEFAULT ''")
        }
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS menu_templates (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS menu_template_entries (
                id TEXT NOT NULL PRIMARY KEY,
                templateId TEXT NOT NULL,
                dayOfWeek INTEGER NOT NULL,
                mealType TEXT NOT NULL,
                recipeId TEXT,
                customDish TEXT,
                servings INTEGER NOT NULL,
                assignedMemberId TEXT,
                cookingTaskId TEXT,
                prepTaskId TEXT,
                FOREIGN KEY(templateId) REFERENCES menu_templates(id) ON DELETE CASCADE ON UPDATE NO ACTION,
                FOREIGN KEY(recipeId) REFERENCES recipes(id) ON DELETE SET NULL ON UPDATE NO ACTION
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_menu_template_entries_templateId ON menu_template_entries(templateId)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_menu_template_entries_recipeId ON menu_template_entries(recipeId)"
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_menu_template_entries_templateId_dayOfWeek_mealType
            ON menu_template_entries(templateId, dayOfWeek, mealType)
            """.trimIndent()
        )
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(database: SupportSQLiteDatabase) {
        if (!hasColumn(database, "tasks", "requiresApproval")) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN requiresApproval INTEGER NOT NULL DEFAULT 0")
        }
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_assignments (
                id TEXT NOT NULL PRIMARY KEY,
                weekStartMillis INTEGER NOT NULL,
                dayOfWeek INTEGER NOT NULL,
                assignedMemberId TEXT,
                rewardPoints INTEGER,
                isRated INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_daily_assignments_weekStartMillis_dayOfWeek
            ON daily_assignments(weekStartMillis, dayOfWeek)
            """.trimIndent()
        )
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        if (!hasColumn(database, "family_goals", "personal")) {
            database.execSQL("ALTER TABLE family_goals ADD COLUMN personal INTEGER NOT NULL DEFAULT 0")
        }
    }
}


val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Placeholder migration.
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(database: SupportSQLiteDatabase) {
        if (!hasColumn(database, "family_members", "birthDate")) {
            database.execSQL("ALTER TABLE family_members ADD COLUMN birthDate TEXT")
        }
        if (!hasColumn(database, "house_rules", "category")) {
            database.execSQL("ALTER TABLE house_rules ADD COLUMN category TEXT")
        }
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS house_rule_blocks (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS penalties (
                id TEXT NOT NULL PRIMARY KEY,
                userId TEXT NOT NULL,
                issuedByUserId TEXT NOT NULL,
                amount INTEGER NOT NULL,
                comment TEXT,
                createdAt INTEGER NOT NULL,
                canceled INTEGER NOT NULL,
                canceledAt INTEGER,
                originType TEXT,
                originId TEXT
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_status ON tasks(status)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_assignedToUserId ON tasks(assignedToUserId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_dueDate ON tasks(dueDate)")
    }
}

val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE task_completions ADD COLUMN reversedAt INTEGER")
        database.execSQL("ALTER TABLE task_completions ADD COLUMN reversedByUserId TEXT")
        database.execSQL("ALTER TABLE task_completions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE task_completions ADD COLUMN generatedRepeatTaskId TEXT")
        database.execSQL("ALTER TABLE user_scores ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE user_wallets ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tasks ADD COLUMN completionCycle INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE task_completions ADD COLUMN completionCycle INTEGER")
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_task_completions_taskId_completionCycle
            ON task_completions(taskId, completionCycle)
            """.trimIndent()
        )
    }
}

val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE tasks ADD COLUMN dueDateHasTime INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE tasks ADD COLUMN reminderMode TEXT NOT NULL DEFAULT 'NONE'"
        )
        database.execSQL(
            "ALTER TABLE tasks ADD COLUMN reminderMinutesOfDay INTEGER DEFAULT NULL"
        )
    }
}

val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(database: SupportSQLiteDatabase) {
        installSyncOutboxInfrastructure(database)
    }
}

/**
 * Menu sync foundation: add updatedAt, canonical IDs, UNIQUE(startDateMillis).
 * Rebuild order avoids UPDATE PK under FK and FK-through-rename hazards.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val legacy = readLegacyMenuSnapshot(database)
        val planned = com.familyhub.data.local.db.migration.Migration36To37Planner.plan(legacy)

        migrateWeeklyMenus(database, planned)
        migrateMenuTemplates(database, planned)
        migrateDailyAssignments(database, planned)
    }
}

private fun readLegacyMenuSnapshot(
    database: SupportSQLiteDatabase
): com.familyhub.data.local.db.migration.LegacyMenuSnapshotV36 {
    val weeklyMenus = mutableListOf<com.familyhub.data.local.db.migration.LegacyWeeklyMenuRow>()
    database.query("SELECT id, startDateMillis, isAutoGenerated, createdAt FROM weekly_menus").use { c ->
        val id = c.getColumnIndexOrThrow("id")
        val start = c.getColumnIndexOrThrow("startDateMillis")
        val auto = c.getColumnIndexOrThrow("isAutoGenerated")
        val created = c.getColumnIndexOrThrow("createdAt")
        while (c.moveToNext()) {
            weeklyMenus += com.familyhub.data.local.db.migration.LegacyWeeklyMenuRow(
                id = c.getString(id),
                startDateMillis = c.getLong(start),
                isAutoGenerated = c.getInt(auto) != 0,
                createdAt = c.getLong(created)
            )
        }
    }

    val menuEntries = mutableListOf<com.familyhub.data.local.db.migration.LegacyMenuEntryRow>()
    database.query(
        """
        SELECT id, menuId, dayOfWeek, mealType, recipeId, customDish, servings,
               assignedMemberId, cookingTaskId, prepTaskId
        FROM menu_entries
        """.trimIndent()
    ).use { c ->
        while (c.moveToNext()) {
            menuEntries += com.familyhub.data.local.db.migration.LegacyMenuEntryRow(
                id = c.getString(c.getColumnIndexOrThrow("id")),
                menuId = c.getString(c.getColumnIndexOrThrow("menuId")),
                dayOfWeek = c.getInt(c.getColumnIndexOrThrow("dayOfWeek")),
                mealType = c.getString(c.getColumnIndexOrThrow("mealType")),
                recipeId = c.getStringOrNull("recipeId"),
                customDish = c.getStringOrNull("customDish"),
                servings = c.getInt(c.getColumnIndexOrThrow("servings")),
                assignedMemberId = c.getStringOrNull("assignedMemberId"),
                cookingTaskId = c.getStringOrNull("cookingTaskId"),
                prepTaskId = c.getStringOrNull("prepTaskId")
            )
        }
    }

    val templates = mutableListOf<com.familyhub.data.local.db.migration.LegacyMenuTemplateRow>()
    database.query("SELECT id, name, createdAt FROM menu_templates").use { c ->
        while (c.moveToNext()) {
            templates += com.familyhub.data.local.db.migration.LegacyMenuTemplateRow(
                id = c.getString(c.getColumnIndexOrThrow("id")),
                name = c.getString(c.getColumnIndexOrThrow("name")),
                createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
            )
        }
    }

    val templateEntries = mutableListOf<com.familyhub.data.local.db.migration.LegacyMenuTemplateEntryRow>()
    database.query(
        """
        SELECT id, templateId, dayOfWeek, mealType, recipeId, customDish, servings,
               assignedMemberId, cookingTaskId, prepTaskId
        FROM menu_template_entries
        """.trimIndent()
    ).use { c ->
        while (c.moveToNext()) {
            templateEntries += com.familyhub.data.local.db.migration.LegacyMenuTemplateEntryRow(
                id = c.getString(c.getColumnIndexOrThrow("id")),
                templateId = c.getString(c.getColumnIndexOrThrow("templateId")),
                dayOfWeek = c.getInt(c.getColumnIndexOrThrow("dayOfWeek")),
                mealType = c.getString(c.getColumnIndexOrThrow("mealType")),
                recipeId = c.getStringOrNull("recipeId"),
                customDish = c.getStringOrNull("customDish"),
                servings = c.getInt(c.getColumnIndexOrThrow("servings")),
                assignedMemberId = c.getStringOrNull("assignedMemberId"),
                cookingTaskId = c.getStringOrNull("cookingTaskId"),
                prepTaskId = c.getStringOrNull("prepTaskId")
            )
        }
    }

    val assignments = mutableListOf<com.familyhub.data.local.db.migration.LegacyDailyAssignmentRow>()
    database.query(
        """
        SELECT id, weekStartMillis, dayOfWeek, assignedMemberId, rewardPoints, isRated
        FROM daily_assignments
        """.trimIndent()
    ).use { c ->
        while (c.moveToNext()) {
            val rewardIdx = c.getColumnIndexOrThrow("rewardPoints")
            assignments += com.familyhub.data.local.db.migration.LegacyDailyAssignmentRow(
                id = c.getString(c.getColumnIndexOrThrow("id")),
                weekStartMillis = c.getLong(c.getColumnIndexOrThrow("weekStartMillis")),
                dayOfWeek = c.getInt(c.getColumnIndexOrThrow("dayOfWeek")),
                assignedMemberId = c.getStringOrNull("assignedMemberId"),
                rewardPoints = if (c.isNull(rewardIdx)) null else c.getInt(rewardIdx),
                isRated = c.getInt(c.getColumnIndexOrThrow("isRated")) != 0
            )
        }
    }

    return com.familyhub.data.local.db.migration.LegacyMenuSnapshotV36(
        weeklyMenus = weeklyMenus,
        menuEntries = menuEntries,
        menuTemplates = templates,
        menuTemplateEntries = templateEntries,
        dailyAssignments = assignments
    )
}

private fun android.database.Cursor.getStringOrNull(column: String): String? {
    val idx = getColumnIndexOrThrow(column)
    return if (isNull(idx)) null else getString(idx)
}

private fun migrateWeeklyMenus(
    database: SupportSQLiteDatabase,
    planned: com.familyhub.data.local.db.migration.PlannedMenuV37
) {
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS weekly_menus_new (
            id TEXT NOT NULL,
            startDateMillis INTEGER NOT NULL,
            isAutoGenerated INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            PRIMARY KEY(id)
        )
        """.trimIndent()
    )
    planned.weeklyMenus.forEach { menu ->
        database.execSQL(
            """
            INSERT INTO weekly_menus_new (id, startDateMillis, isAutoGenerated, createdAt, updatedAt)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                menu.id,
                menu.startDateMillis,
                if (menu.isAutoGenerated) 1 else 0,
                menu.createdAt,
                menu.updatedAt
            )
        )
    }

    database.execSQL("DROP TABLE IF EXISTS menu_entries")
    database.execSQL("DROP TABLE IF EXISTS weekly_menus")
    database.execSQL("ALTER TABLE weekly_menus_new RENAME TO weekly_menus")
    database.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS index_weekly_menus_startDateMillis
        ON weekly_menus(startDateMillis)
        """.trimIndent()
    )

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS menu_entries (
            id TEXT NOT NULL,
            menuId TEXT NOT NULL,
            dayOfWeek INTEGER NOT NULL,
            mealType TEXT NOT NULL,
            recipeId TEXT,
            customDish TEXT,
            servings INTEGER NOT NULL,
            assignedMemberId TEXT,
            cookingTaskId TEXT,
            prepTaskId TEXT,
            updatedAt INTEGER NOT NULL,
            PRIMARY KEY(id),
            FOREIGN KEY(menuId) REFERENCES weekly_menus(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent()
    )
    planned.menuEntries.forEach { entry ->
        database.execSQL(
            """
            INSERT INTO menu_entries (
                id, menuId, dayOfWeek, mealType, recipeId, customDish, servings,
                assignedMemberId, cookingTaskId, prepTaskId, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                entry.id,
                entry.menuId,
                entry.dayOfWeek,
                entry.mealType,
                entry.recipeId,
                entry.customDish,
                entry.servings,
                entry.assignedMemberId,
                entry.cookingTaskId,
                entry.prepTaskId,
                entry.updatedAt
            )
        )
    }
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_menu_entries_menuId ON menu_entries(menuId)"
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_menu_entries_recipeId ON menu_entries(recipeId)"
    )
    database.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS index_menu_entries_menuId_dayOfWeek_mealType
        ON menu_entries(menuId, dayOfWeek, mealType)
        """.trimIndent()
    )
}

private fun migrateMenuTemplates(
    database: SupportSQLiteDatabase,
    planned: com.familyhub.data.local.db.migration.PlannedMenuV37
) {
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS menu_templates_new (
            id TEXT NOT NULL,
            name TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            PRIMARY KEY(id)
        )
        """.trimIndent()
    )
    planned.menuTemplates.forEach { template ->
        database.execSQL(
            """
            INSERT INTO menu_templates_new (id, name, createdAt, updatedAt)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(template.id, template.name, template.createdAt, template.updatedAt)
        )
    }

    database.execSQL("DROP TABLE IF EXISTS menu_template_entries")
    database.execSQL("DROP TABLE IF EXISTS menu_templates")
    database.execSQL("ALTER TABLE menu_templates_new RENAME TO menu_templates")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS menu_template_entries (
            id TEXT NOT NULL,
            templateId TEXT NOT NULL,
            dayOfWeek INTEGER NOT NULL,
            mealType TEXT NOT NULL,
            recipeId TEXT,
            customDish TEXT,
            servings INTEGER NOT NULL,
            assignedMemberId TEXT,
            cookingTaskId TEXT,
            prepTaskId TEXT,
            updatedAt INTEGER NOT NULL,
            PRIMARY KEY(id),
            FOREIGN KEY(templateId) REFERENCES menu_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent()
    )
    planned.menuTemplateEntries.forEach { entry ->
        database.execSQL(
            """
            INSERT INTO menu_template_entries (
                id, templateId, dayOfWeek, mealType, recipeId, customDish, servings,
                assignedMemberId, cookingTaskId, prepTaskId, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                entry.id,
                entry.templateId,
                entry.dayOfWeek,
                entry.mealType,
                entry.recipeId,
                entry.customDish,
                entry.servings,
                entry.assignedMemberId,
                entry.cookingTaskId,
                entry.prepTaskId,
                entry.updatedAt
            )
        )
    }
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_menu_template_entries_templateId ON menu_template_entries(templateId)"
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_menu_template_entries_recipeId ON menu_template_entries(recipeId)"
    )
    database.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS index_menu_template_entries_templateId_dayOfWeek_mealType
        ON menu_template_entries(templateId, dayOfWeek, mealType)
        """.trimIndent()
    )
}

private fun migrateDailyAssignments(
    database: SupportSQLiteDatabase,
    planned: com.familyhub.data.local.db.migration.PlannedMenuV37
) {
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS daily_assignments_new (
            id TEXT NOT NULL,
            weekStartMillis INTEGER NOT NULL,
            dayOfWeek INTEGER NOT NULL,
            assignedMemberId TEXT,
            rewardPoints INTEGER,
            isRated INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            PRIMARY KEY(id)
        )
        """.trimIndent()
    )
    planned.dailyAssignments.forEach { assignment ->
        database.execSQL(
            """
            INSERT INTO daily_assignments_new (
                id, weekStartMillis, dayOfWeek, assignedMemberId, rewardPoints, isRated, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                assignment.id,
                assignment.weekStartMillis,
                assignment.dayOfWeek,
                assignment.assignedMemberId,
                assignment.rewardPoints,
                if (assignment.isRated) 1 else 0,
                assignment.updatedAt
            )
        )
    }
    database.execSQL("DROP TABLE IF EXISTS daily_assignments")
    database.execSQL("ALTER TABLE daily_assignments_new RENAME TO daily_assignments")
    database.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS index_daily_assignments_weekStartMillis_dayOfWeek
        ON daily_assignments(weekStartMillis, dayOfWeek)
        """.trimIndent()
    )
}

private fun hasColumn(database: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
    val cursor = database.query("PRAGMA table_info($tableName)")
    cursor.use {
        val nameIndex = it.getColumnIndex("name")
        while (it.moveToNext()) {
            if (nameIndex >= 0 && it.getString(nameIndex) == columnName) {
                return true
            }
        }
    }
    return false
}
