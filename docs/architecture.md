# Архитектура проекта FamilyHub (MVP)

## Общая идея

Мы идём по стандартной для современного Compose-приложения схеме:

* `data/` — источники данных (Room, DataStore), модели хранения (Entity), DAO, репозитории.
* `domain/` — бизнес-логика и модели домена (обычно data class без аннотаций Room и без UI-логики). Тут же можно держать use case'ы, если нужно.
* `ui/` — экраны, стейты экранов, ViewModel.

Navigation Compose связывает экраны через bottom bar.

---

## Пакеты (директории)

```text
com.familyhub
├─ data
│  ├─ local
│  │  ├─ db
│  │  │  ├─ FamilyHubDatabase.kt
│  │  │  ├─ dao
│  │  │  │  ├─ FamilyMemberDao.kt
│  │  │  │  ├─ TaskDao.kt
│  │  │  │  ├─ ShoppingItemDao.kt
│  │  │  │  ├─ FamilyEventDao.kt
│  │  │  │  └─ UserScoreDao.kt
│  │  ├─ entity
│  │  │  ├─ FamilyMemberEntity.kt
│  │  │  ├─ TaskEntity.kt
│  │  │  ├─ ShoppingItemEntity.kt
│  │  │  ├─ FamilyEventEntity.kt
│  │  │  └─ UserScoreEntity.kt
│  ├─ datastore
│  │  ├─ AppSettingsDataStore.kt
│  │  └─ AppSettingsSerializer.kt
│  ├─ repository
│     ├─ FamilyMemberRepository.kt
│     ├─ TaskRepository.kt
│     ├─ ShoppingRepository.kt
│     ├─ FamilyEventRepository.kt
│     └─ ScoreRepository.kt
│
├─ domain
│  ├─ model
│  │  ├─ FamilyMember.kt
│  │  ├─ Task.kt
│  │  ├─ RepeatRule.kt
│  │  ├─ TaskStatus.kt
│  │  ├─ ShoppingItem.kt
│  │  ├─ FamilyEvent.kt
│  │  ├─ UserScore.kt
│  │  └─ AppSettings.kt
│  ├─ utils
│     ├─ WeekUtils.kt         // расчёт начала недели (понедельник 00:00)
│     └─ IdGenerator.kt       // UUID
│  ├─ usecase
│     ├─ CompleteTaskUseCase.kt
│     ├─ CreateRepeatingTaskUseCase.kt
│     └─ GetCurrentWeekScoresUseCase.kt
│
├─ ui
│  ├─ navigation
│  │  ├─ BottomNavItem.kt
│  │  ├─ NavRoutes.kt
│  │  └─ FamilyHubNavHost.kt
│  ├─ components
│  │  ├─ TaskItemCard.kt
│  │  ├─ ShoppingItemRow.kt
│  │  ├─ ScoreRow.kt
│  │  ├─ SectionHeader.kt
│  │  └─ AddFloatingButton.kt
│  ├─ screens
│  │  ├─ home
│  │  │  ├─ HomeScreen.kt
│  │  │  └─ HomeViewModel.kt
│  │  ├─ tasks
│  │  │  ├─ TasksScreen.kt
│  │  │  ├─ TaskEditorDialog.kt
│  │  │  └─ TasksViewModel.kt
│  │  ├─ shopping
│  │  │  ├─ ShoppingScreen.kt
│  │  │  ├─ ShoppingEditorDialog.kt
│  │  │  └─ ShoppingViewModel.kt
│  │  ├─ rating
│  │  │  ├─ RatingScreen.kt
│  │  │  └─ RatingViewModel.kt
│  │  └─ common
│  │     ├─ EventMiniList.kt    // блок "ближайшие события"
│  │     └─ CurrentUserPicker.kt // выбор кто сейчас "я"
│  ├─ theme
│  │  ├─ Color.kt
│  │  ├─ Typography.kt
│  │  ├─ Shape.kt
│  │  └─ Theme.kt
│  └─ MainActivity.kt
│
└─ util
   └─ TimeUtils.kt             // форматирование дат для UI
```

---

## Потоки данных (кто с кем разговаривает)

* UI (Screen + ViewModel) → Repository → DAO / DataStore.
* UseCase — это "бизнес-операция", которую ViewModel вызывает. Например: пользователь отметил задачу как DONE → дергаем CompleteTaskUseCase.

Модели **Entity** (Room) ≠ модели **domain/model** (чистые).
Репозиторий преобразует Entity ↔ Domain model.

Почему так:

* Domain остаётся чистым (без аннотаций @Entity).
* Позже будет проще добавить синхронизацию через сеть.

---

## Детализация ключевых частей

### 1. data/local/db/FamilyHubDatabase.kt

RoomDatabase с таблицами.

```kotlin
@Database(
    entities = [
        FamilyMemberEntity::class,
        TaskEntity::class,
        ShoppingItemEntity::class,
        FamilyEventEntity::class,
        UserScoreEntity::class
    ],
    version = 1
)
abstract class FamilyHubDatabase : RoomDatabase() {
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun taskDao(): TaskDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun familyEventDao(): FamilyEventDao
    abstract fun userScoreDao(): UserScoreDao
}
```

### 2. data/local/entity/*.kt

Пример TaskEntity:

```kotlin
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val createdByUserId: String,
    val assignedToUserId: String,
    val dueDate: Long?,           // millis
    val repeatRule: String,       // "NONE" / "DAILY" / "WEEKLY"
    val status: String,           // "TODO" / "DONE"
    val createdAt: Long
)
```

Каждая Entity — это плоский сериализуемый вид.

ShoppingItemEntity аналогично:

```kotlin
@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdByUserId: String,
    val isBought: Boolean,
    val createdAt: Long
)
```

И так далее для остальных.

UserScoreEntity:

```kotlin
@Entity(
    tableName = "user_scores",
    primaryKeys = ["userId", "weekStartDate"]
)
data class UserScoreEntity(
    val userId: String,
    val weekStartDate: Long,
    val score: Int
)
```

### 3. data/local/db/dao/*.kt

Пример TaskDao:

```kotlin
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE assignedToUserId = :userId")
    suspend fun getByAssignee(userId: String): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Delete
    suspend fun delete(task: TaskEntity)
}
```

ShoppingItemDao:

```kotlin
@Dao
interface ShoppingItemDao {

    @Query("SELECT * FROM shopping_items ORDER BY createdAt DESC")
    suspend fun getAll(): List<ShoppingItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)

    @Delete
    suspend fun delete(item: ShoppingItemEntity)
}
```

UserScoreDao (нужен инкремент баллов):

```kotlin
@Dao
interface UserScoreDao {

    @Query("SELECT * FROM user_scores WHERE weekStartDate = :weekStartDate")
    suspend fun getScoresForWeek(weekStartDate: Long): List<UserScoreEntity>

    @Query("SELECT * FROM user_scores WHERE userId = :userId AND weekStartDate = :weekStartDate LIMIT 1")
    suspend fun getUserScoreForWeek(userId: String, weekStartDate: Long): UserScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: UserScoreEntity)
}
```

FamilyEventDao и FamilyMemberDao по той же схеме.

---

### 4. data/datastore/AppSettingsDataStore.kt

Нам надо хранить только `currentUserId`, чтобы понимать, "я сейчас кто".
Используем Proto DataStore или Preferences. Для MVP хватит Preferences.

Примерно:

```kotlin
class AppSettingsDataStore(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_CURRENT_USER = stringPreferencesKey("current_user_id")
    }

    val settingsFlow: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            AppSettings(
                currentUserId = prefs[KEY_CURRENT_USER] ?: ""
            )
        }

    suspend fun setCurrentUser(id: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CURRENT_USER] = id
        }
    }
}
```

Domain-модель `AppSettings`:

```kotlin
data class AppSettings(
    val currentUserId: String
)
```

---

### 5. data/repository/*.kt

Репозитории делают:

* маппинг Entity ↔ Domain model,
* высокоуровневые операции (например "создать задачу", "пометить задачу выполненной" и т.д.).

Пример TaskRepository:

```kotlin
class TaskRepository(
    private val taskDao: TaskDao
) {
    suspend fun getAllTasks(): List<Task> {
        return taskDao.getAll().map { it.toDomain() }
    }

    suspend fun getTasksForUser(userId: String): List<Task> {
        return taskDao.getByAssignee(userId).map { it.toDomain() }
    }

    suspend fun upsertTask(task: Task) {
        taskDao.upsert(task.toEntity())
    }

    suspend fun upsertTasks(tasks: List<Task>) {
        taskDao.upsertAll(tasks.map { it.toEntity() })
    }

    suspend fun deleteTask(task: Task) {
        taskDao.delete(task.toEntity())
    }
}
```

Значит нам нужны мапперы:

* в `TaskEntity.kt` или отдельный файл `TaskMappers.kt`:

```kotlin
fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    createdByUserId = createdByUserId,
    assignedToUserId = assignedToUserId,
    dueDate = dueDate,
    repeatRule = RepeatRule.valueOf(repeatRule),
    status = TaskStatus.valueOf(status),
    createdAt = createdAt
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    createdByUserId = createdByUserId,
    assignedToUserId = assignedToUserId,
    dueDate = dueDate,
    repeatRule = repeatRule.name,
    status = status.name,
    createdAt = createdAt
)
```

То же самое для остальных сущностей.

---

### 6. domain/usecase/*.kt

#### 6.1. CompleteTaskUseCase

Когда задача отмечена DONE:

1. Меняем задаче статус на DONE.
2. Начисляем баллы исполнителю.
3. Если задача повторяющаяся — создаём новую задачу на следующий цикл.

```kotlin
class CompleteTaskUseCase(
    private val taskRepository: TaskRepository,
    private val scoreRepository: ScoreRepository,
    private val weekUtils: WeekUtils,
    private val idGenerator: IdGenerator,
    private val createRepeatingTaskUseCase: CreateRepeatingTaskUseCase
) {
    suspend operator fun invoke(task: Task) {
        // 1. Отметить задачу как DONE
        val doneTask = task.copy(status = TaskStatus.DONE)
        taskRepository.upsertTask(doneTask)

        // 2. Начислить баллы
        val weekStart = weekUtils.getCurrentWeekStartMillis()
        scoreRepository.incrementScore(
            userId = task.assignedToUserId,
            weekStartDate = weekStart,
            delta = 1
        )

        // 3. Если повторы — создать новую копию
        if (task.repeatRule != RepeatRule.NONE) {
            createRepeatingTaskUseCase(task)
        }
    }
}
```

#### 6.2. CreateRepeatingTaskUseCase

Создаёт следующую задачу на завтра / через неделю.

```kotlin
class CreateRepeatingTaskUseCase(
    private val taskRepository: TaskRepository,
    private val idGenerator: IdGenerator,
    private val timeUtils: TimeUtils // для +1 день / +7 дней
) {
    suspend operator fun invoke(original: Task) {
        val nextDue = when(original.repeatRule) {
            RepeatRule.DAILY -> timeUtils.addDays(original.dueDate ?: timeUtils.now(), 1)
            RepeatRule.WEEKLY -> timeUtils.addDays(original.dueDate ?: timeUtils.now(), 7)
            RepeatRule.NONE -> return
        }

        val newTask = original.copy(
            id = idGenerator.newId(),
            status = TaskStatus.TODO,
            dueDate = nextDue,
            createdAt = timeUtils.now()
        )

        taskRepository.upsertTask(newTask)
    }
}
```

#### 6.3. GetCurrentWeekScoresUseCase

Читает список очков всех за эту неделю, сортирует по убыванию.

```kotlin
class GetCurrentWeekScoresUseCase(
    private val scoreRepository: ScoreRepository,
    private val memberRepository: FamilyMemberRepository,
    private val weekUtils: WeekUtils
) {
    suspend operator fun invoke(): List<Pair<FamilyMember, Int>> {
        val weekStart = weekUtils.getCurrentWeekStartMillis()
        val scores = scoreRepository.getScoresForWeek(weekStart)
        val members = memberRepository.getAllMembers().associateBy { it.id }

        return scores
            .mapNotNull { s ->
                val user = members[s.userId]
                if (user != null) user to s.score else null
            }
            .sortedByDescending { it.second }
    }
}
```

---

### 7. ui/navigation

#### NavRoutes.kt

Определяет маршруты для навигации.

```kotlin
object NavRoutes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val SHOPPING = "shopping"
    const val RATING = "rating"
}
```

#### BottomNavItem.kt

Описания вкладок для BottomBar.

```kotlin
data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(NavRoutes.HOME, Icons.Default.Home, "Дом"),
    BottomNavItem(NavRoutes.TASKS, Icons.Default.List, "Задачи"),
    BottomNavItem(NavRoutes.SHOPPING, Icons.Default.ShoppingCart, "Покупки"),
    BottomNavItem(NavRoutes.RATING, Icons.Default.Star, "Рейтинг")
)
```

#### FamilyHubNavHost.kt

Создаёт NavHost с composable для каждого экрана.

```kotlin
@Composable
fun FamilyHubNavHost(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME
    ) {
        composable(NavRoutes.HOME) { HomeScreen() }
        composable(NavRoutes.TASKS) { TasksScreen() }
        composable(NavRoutes.SHOPPING) { ShoppingScreen() }
        composable(NavRoutes.RATING) { RatingScreen() }
    }
}
```

---

### 8. ui/screens/*/ViewModel

Общий паттерн:

* ViewModel держит `UiState` через `StateFlow`.
* Репозитории инжектируются.
* Вызовы use case'ов идут из ViewModel.

Пример RatingViewModel:

```kotlin
data class RatingUiState(
    val items: List<RatingRow> = emptyList()
)

data class RatingRow(
    val memberName: String,
    val score: Int
)

class RatingViewModel(
    private val getCurrentWeekScoresUseCase: GetCurrentWeekScoresUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RatingUiState())
    val state: StateFlow<RatingUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val pairs = getCurrentWeekScoresUseCase()
            _state.value = RatingUiState(
                items = pairs.map { (member, score) ->
                    RatingRow(member.displayName, score)
                }
            )
        }
    }
}
```

На экране `RatingScreen` мы просто наблюдаем `state` и рисуем LazyColumn со списком.

---

### 9. ui/components/*

Мини-компоненты UI, переиспользуемые в разных экранах:

* `TaskItemCard.kt` — карточка задачи с чекбоксом "сделано".
* `ShoppingItemRow.kt` — строка покупки с чекбоксом.
* `ScoreRow.kt` — строка рейтинга "Имя — очки".
* `SectionHeader.kt` — заголовок секции с текстом типа "Мои задачи сегодня".
* `AddFloatingButton.kt` — FAB с плюсиком.

---

### 10. ui/MainActivity.kt

Точка входа:

* создаёт NavController,
* Scaffold с BottomBar,
* внутри — FamilyHubNavHost.

Псевдо:

```kotlin
@AndroidEntryPoint // если используем Hilt (хорошо бы сразу)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FamilyHubTheme {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = {
                        BottomNavigationBar(navController)
                    }
                ) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
                        FamilyHubNavHost(navController)
                    }
                }
            }
        }
    }
}
```

BottomNavigationBar использует `bottomNavItems`, показывает иконки и навигацию.

---

## Что мы сейчас уже имеем как финальную основу для агента

1. **Домены** (Task, ShoppingItem, FamilyEvent, UserScore, FamilyMember, AppSettings) определены.
2. **Room-сущности (Entity)** и DAO — понятны и описаны.
3. **Репозитории** знают как маппить Entity ↔ Domain.
4. **Use cases**: CompleteTaskUseCase, CreateRepeatingTaskUseCase, GetCurrentWeekScoresUseCase.
5. **DataStore** для currentUserId задан.
6. **Навигация** через NavHost с 4 экранами.
7. **ViewModel-паттерн** показан.
8. **Scaffold + BottomBar** описан.
9. **Принцип начисления баллов и повторных задач** понятен.

Это уже прямо рабочая архитектурная схема MVP.

Следующий естественный шаг — можно попросить агента:

* сгенерировать пустые классы/файлы по этой структуре,
* добавить Hilt-модули (для репозиториев, DAO, DataStore, usecase),
* сгенерировать базовые Composable для каждого экрана (просто заглушки со списками),
* собрать Gradle зависимости (Compose, Room, DataStore, Hilt, Navigation Compose, Material3).

