package com.familyhub.ui.screens.menu

import com.familyhub.ui.components.FhHorizontalScrollFade
import com.familyhub.ui.components.FhScrollableHorizontalRow
import com.familyhub.ui.components.FhSwitchRow
import com.familyhub.ui.components.FhIcons
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import com.familyhub.R
import com.familyhub.data.export.MenuShareFormat
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MealPlanPreferences
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.MenuTemplate
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.FhLoading
import com.familyhub.ui.components.FhDialogFormBody
import com.familyhub.ui.components.fhDialogAction
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.screens.food.FoodSectionTabs
import com.familyhub.ui.screens.recipes.RecipeDetailsDialog
import com.familyhub.ui.navigation.FoodSectionTab
import com.familyhub.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyMenuScreen(
    onNavigateToRecipes: () -> Unit = {},
    viewModel: WeeklyMenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectionTarget by remember { mutableStateOf<MealSelectionTarget?>(null) }
    var showPreferences by remember { mutableStateOf(false) }
    var assignmentTarget by remember { mutableStateOf<AssignmentTarget?>(null) }
    var dayAssignmentTarget by remember { mutableStateOf<DayAssignmentTarget?>(null) }
    var rewardPromptTarget by remember { mutableStateOf<RewardPromptTarget?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Launcher для запроса разрешения на уведомления
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Разрешение получено, повторяем попытку отправить уведомление
            viewModel.retryNotificationAfterPermission()
        } else {
            // Разрешение отклонено - показываем сообщение с возможностью открыть настройки
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.menu_notification_permission_required),
                    actionLabel = context.getString(R.string.menu_notification_open_settings),
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openNotificationSettings(context)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is WeeklyMenuEvent.ShareMenu -> context.startActivity(event.intent)
                is WeeklyMenuEvent.RequestNotificationPermission -> {
                    // Запрашиваем разрешение
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                is WeeklyMenuEvent.OpenNotificationSettings -> {
                    openNotificationSettings(context)
                }
                is WeeklyMenuEvent.ShowNotificationError -> {
                    if (event.canOpenSettings) {
                        // Показываем Snackbar с возможностью открыть настройки
                        val result = snackbarHostState.showSnackbar(
                            message = event.message,
                            actionLabel = context.getString(R.string.menu_notification_open_settings),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            openNotificationSettings(context)
                        }
                    } else {
                        // Просто информационное сообщение
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is WeeklyMenuEvent.ShowMessage -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                    }
                }
                is WeeklyMenuEvent.ShowRewardPrompt -> {
                    // show dialog in UI
                    coroutineScope.launch {
                        rewardPromptTarget = RewardPromptTarget(
                            dayIndex = event.dayIndex,
                            dateMillis = event.dateMillis,
                            assignedMemberId = event.assignedMemberId
                        )
                    }
                }
            }
        }
    }

    val dayNames = remember(state.startOfWeekMillis) {
        val format = SimpleDateFormat("EEEE", Locale.getDefault())
        (0 until 7).map { offset ->
            val date = Date(TimeUtils.addDays(state.startOfWeekMillis, offset))
            format.format(date).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
    val shortDayNames = remember(state.startOfWeekMillis) {
        val format = SimpleDateFormat("EEE d", Locale.getDefault())
        (0 until 7).map { offset ->
            format.format(Date(TimeUtils.addDays(state.startOfWeekMillis, offset)))
        }
    }
    val weekRange = remember(state.startOfWeekMillis) {
        TimeUtils.formatWeekDateRange(
            startMillis = state.startOfWeekMillis,
            endMillis = TimeUtils.addDays(state.startOfWeekMillis, 6),
            compact = false,
        )
    }
    val compactWeekRange = remember(state.startOfWeekMillis) {
        TimeUtils.formatWeekDateRange(
            startMillis = state.startOfWeekMillis,
            endMillis = TimeUtils.addDays(state.startOfWeekMillis, 6),
            compact = true,
        )
    }

    val mealTypes = remember { MealType.values().toList() }
    val canManageMenu = state.canManageMenu
    val readOnlyEditCd = stringResource(R.string.menu_read_only_edit_disabled_cd)
    val loadingCd = stringResource(R.string.common_loading)

    val membersMap = remember(state.members) { state.members.associateBy { it.id } }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showLoadTemplateDialog by remember { mutableStateOf(false) }
    var showClearMenuConfirmation by remember { mutableStateOf(false) }
    var menuActionsExpanded by remember { mutableStateOf(false) }
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(0) }
    var viewingRecipe by remember { mutableStateOf<Recipe?>(null) }
    val defaultTemplateName = stringResource(R.string.menu_template_default_name)

    FhScreenScaffold(
        title = stringResource(R.string.food_hub_title),
        collapsingTopBar = false,
        isRefreshing = state.isLoading,
        snackbarHostState = snackbarHostState,
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError
    ) { paddingValues ->
        if (state.isLoading && !state.hasLoadedOnce) {
            FhLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .semantics { contentDescription = loadingCd }
            )
            return@FhScreenScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 0.dp,
                end = 16.dp,
                bottom = WeeklyMenuLayoutPolicy.listBottomPadding(),
            ),
        ) {
            item {
                FoodSectionTabs(
                    selectedTab = FoodSectionTab.WEEKLY_MENU,
                    onTabSelected = { tab ->
                        if (tab == FoodSectionTab.RECIPES) {
                            onNavigateToRecipes()
                        }
                    },
                )
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                onClick = viewModel::goToPreviousWeek,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = FhIcons.ChevronLeft,
                                    contentDescription = stringResource(R.string.menu_prev_week),
                                )
                            }
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f),
                            ) {
                                val useCompactRange =
                                    WeeklyMenuLayoutPolicy.useCompactWeekRange(maxWidth)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = if (useCompactRange) compactWeekRange else weekRange,
                                        style = if (useCompactRange) {
                                            MaterialTheme.typography.bodyMedium
                                        } else {
                                            MaterialTheme.typography.titleMedium
                                        },
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            IconButton(
                                onClick = viewModel::goToNextWeek,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = FhIcons.ChevronRight,
                                    contentDescription = stringResource(R.string.menu_next_week),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            com.familyhub.ui.theme.components.FHTonalButton(
                                onClick = viewModel::generateAutomaticMenu,
                                enabled = canManageMenu && state.recipes.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .semantics {
                                        if (!canManageMenu) contentDescription = readOnlyEditCd
                                    },
                            ) {
                                Icon(imageVector = FhIcons.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.menu_generate_auto),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { menuActionsExpanded = true },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = FhIcons.MoreVert,
                                        contentDescription = stringResource(R.string.menu_v2_more_actions_cd),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuActionsExpanded,
                                    onDismissRequest = { menuActionsExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_preferences_icon_cd)) },
                                        leadingIcon = {
                                            Icon(FhIcons.Settings, contentDescription = null)
                                        },
                                        onClick = {
                                            menuActionsExpanded = false
                                            showPreferences = true
                                        },
                                    )
                                    val hasMenuEntries = state.menu?.entries?.isNotEmpty() == true
                                    if (hasMenuEntries) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_export_week_to_shopping_icon_cd)) },
                                            leadingIcon = {
                                                Icon(FhIcons.ShoppingCart, contentDescription = null)
                                            },
                                            onClick = {
                                                menuActionsExpanded = false
                                                viewModel.exportWeekToShopping()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_share_pdf_icon_cd)) },
                                            leadingIcon = {
                                                Icon(FhIcons.PictureAsPdf, contentDescription = null)
                                            },
                                            onClick = {
                                                menuActionsExpanded = false
                                                viewModel.shareMenu(MenuShareFormat.PDF)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_share_image_icon_cd)) },
                                            leadingIcon = {
                                                Icon(FhIcons.Image, contentDescription = null)
                                            },
                                            onClick = {
                                                menuActionsExpanded = false
                                                viewModel.shareMenu(MenuShareFormat.IMAGE)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_notify_today_icon_cd)) },
                                            leadingIcon = {
                                                Icon(FhIcons.Notifications, contentDescription = null)
                                            },
                                            onClick = {
                                                menuActionsExpanded = false
                                                viewModel.sendTodayMenuNotification()
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_template_save_button)) },
                                        onClick = {
                                            menuActionsExpanded = false
                                            showSaveTemplateDialog = true
                                        },
                                        enabled = canManageMenu && hasMenuEntries,
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_template_load_button)) },
                                        onClick = {
                                            menuActionsExpanded = false
                                            showLoadTemplateDialog = true
                                        },
                                        enabled = canManageMenu && state.menuTemplates.isNotEmpty(),
                                    )
                                    if (hasMenuEntries) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_clear_icon_cd)) },
                                            leadingIcon = {
                                                Icon(FhIcons.Delete, contentDescription = null)
                                            },
                                            onClick = {
                                                menuActionsExpanded = false
                                                showClearMenuConfirmation = true
                                            },
                                            enabled = canManageMenu,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!canManageMenu) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = readOnlyEditCd },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = FhIcons.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.menu_v2_read_only_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.menu_read_only_role_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            if (state.recipes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.menu_empty_recipes_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                FhScrollableHorizontalRow(
                    selectedIndex = selectedDayIndex,
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shortDayNames.size) { index ->
                        FilterChip(
                            selected = selectedDayIndex == index,
                            onClick = { selectedDayIndex = index },
                            label = {
                                Text(
                                    text = shortDayNames[index],
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }

            item {
                val dayIndex = selectedDayIndex.coerceIn(dayNames.indices)
                val dayName = dayNames[dayIndex]
                val dayAssignment = state.dailyAssignments[dayIndex]
                val assignedMemberName =
                    dayAssignment?.assignedMemberId?.let { membersMap[it]?.displayLabel() }
                ThemedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            WeeklyMenuDayCookHeader(
                                assignedMemberName = assignedMemberName,
                                canManageMenu = canManageMenu,
                                readOnlyEditCd = readOnlyEditCd,
                                onChangeCook = {
                                    dayAssignmentTarget = DayAssignmentTarget(dayIndex, dayName)
                                },
                            )
                        }
                        mealTypes.forEach { mealType ->
                            val entry = state.entryFor(dayIndex, mealType)
                            val recipe = entry?.recipeId?.let { recipeId ->
                                state.recipes.firstOrNull { it.id == recipeId }
                            }
                            WeeklyMenuMealRow(
                                mealType = mealType,
                                entry = entry,
                                recipe = recipe,
                                canManageMenu = canManageMenu,
                                readOnlyEditCd = readOnlyEditCd,
                                onOpen = {
                                    if (entry != null && recipe != null) {
                                        viewingRecipe = recipe
                                    } else {
                                        selectionTarget =
                                            MealSelectionTarget(dayIndex, dayName, mealType, entry)
                                    }
                                },
                                onOpenRecipe = { viewingRecipe = recipe },
                                onReplace = {
                                    selectionTarget =
                                        MealSelectionTarget(dayIndex, dayName, mealType, entry)
                                },
                                onAssign = {
                                    assignmentTarget =
                                        AssignmentTarget(dayIndex, dayName, mealType, entry)
                                },
                                onRemove = { viewModel.clearMeal(dayIndex, mealType) },
                            )
                        }
                    }
                }
            }

            if (state.menu != null) {
                item {
                    MenuAnalyticsCard(
                        analytics = state.analytics,
                        members = membersMap,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                MenuHistorySection(
                    currentWeekStart = state.startOfWeekMillis,
                    history = state.history,
                    canManageMenu = canManageMenu,
                    readOnlyEditCd = readOnlyEditCd,
                    onCopy = { viewModel.copyFromWeek(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    selectionTarget?.let { target ->
        MealSelectionDialog(
            target = target,
            recipes = state.recipes,
            onDismiss = { selectionTarget = null },
            onSelectRecipe = { recipe ->
                viewModel.setRecipeForMeal(target.dayIndex, target.mealType, recipe.id)
                selectionTarget = null
            },
            onSaveCustom = { text ->
                viewModel.setCustomDish(target.dayIndex, target.mealType, text)
                selectionTarget = null
            },
            onClear = {
                viewModel.clearMeal(target.dayIndex, target.mealType)
                selectionTarget = null
            }
        )
    }

    assignmentTarget?.let { target ->
        AssignCookingDialog(
            dayName = target.dayName,
            mealType = target.mealType,
            members = state.members,
            currentAssigneeId = target.entry?.assignedMemberId,
            onDismiss = { assignmentTarget = null },
            onAssign = { memberId ->
                viewModel.assignCookingTask(target.dayIndex, target.mealType, memberId)
                assignmentTarget = null
            },
            onClear = {
                viewModel.assignCookingTask(target.dayIndex, target.mealType, null)
                assignmentTarget = null
            }
        )
    }

    dayAssignmentTarget?.let { target ->
        val dayAssignment = state.dailyAssignments[target.dayIndex]
        AssignCookingDialog(
            dayName = target.dayName,
            mealType = MealType.BREAKFAST,
            members = state.members,
            currentAssigneeId = dayAssignment?.assignedMemberId,
            onDismiss = { dayAssignmentTarget = null },
            onAssign = { memberId ->
                viewModel.assignResponsibleForDay(target.dayIndex, memberId)
                dayAssignmentTarget = null
            },
            onClear = {
                viewModel.assignResponsibleForDay(target.dayIndex, null)
                dayAssignmentTarget = null
            }
        )
    }

    if (showPreferences) {
        MenuPreferencesDialog(
            preferences = state.preferences,
            onDismiss = { showPreferences = false },
            onSave = { prefs ->
                viewModel.updatePreferences(prefs)
                showPreferences = false
            }
        )
    }

    if (showSaveTemplateDialog) {
        MenuTemplateDialog(
            initialName = defaultTemplateName,
            onDismiss = { showSaveTemplateDialog = false },
            onSave = {
                viewModel.saveMenuTemplate(it)
                showSaveTemplateDialog = false
            }
        )
    }

    if (showLoadTemplateDialog) {
        LoadMenuTemplateDialog(
            templates = state.menuTemplates,
            onDismiss = { showLoadTemplateDialog = false },
            onApply = { templateId ->
                viewModel.applyMenuTemplate(templateId)
                showLoadTemplateDialog = false
            }
        )
    }

    if (showClearMenuConfirmation) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.menu_v2_clear_title),
            message = stringResource(R.string.menu_v2_clear_message),
            onDismiss = { showClearMenuConfirmation = false },
            onConfirm = viewModel::clearMenu,
        )
    }

    rewardPromptTarget?.let { target ->
        val dayNameForDialog = dayNames.getOrNull(target.dayIndex) ?: TimeUtils.formatDate(target.dateMillis)
        RewardPromptDialog(
            target = target,
            dayName = dayNameForDialog,
            onDismiss = { rewardPromptTarget = null },
            onConfirm = { points ->
                viewModel.rateDay(target.dayIndex, points)
                rewardPromptTarget = null
            }
        )
    }

    viewingRecipe?.let { recipe ->
        RecipeDetailsDialog(
            recipe = recipe,
            onDismiss = { viewingRecipe = null },
            onDelete = {},
            onToggleFavorite = {},
            onEdit = {},
            onExportIngredients = {},
            canManage = false,
        )
    }
}

@VisibleForTesting
internal object WeeklyMenuLayoutPolicy {
    private val CompactWeekRangeWidth = 200.dp

    fun useCompactWeekRange(availableWidth: androidx.compose.ui.unit.Dp): Boolean =
        availableWidth < CompactWeekRangeWidth

    fun listBottomPadding() = FhLayout.listBottomPadding(hasFloatingActionButton = false)
}

data class MealSelectionTarget(
    val dayIndex: Int,
    val dayName: String,
    val mealType: MealType,
    val currentEntry: MenuEntry?
)

data class AssignmentTarget(
    val dayIndex: Int,
    val dayName: String,
    val mealType: MealType,
    val entry: MenuEntry?
)

data class RewardPromptTarget(
    val dayIndex: Int,
    val dateMillis: Long,
    val assignedMemberId: String?
)

data class DayAssignmentTarget(
    val dayIndex: Int,
    val dayName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealSelectionDialog(
    target: MealSelectionTarget,
    recipes: List<Recipe>,
    onDismiss: () -> Unit,
    onSelectRecipe: (Recipe) -> Unit,
    onSaveCustom: (String) -> Unit,
    onClear: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var customDish by rememberSaveable(target.dayIndex, target.mealType) {
        mutableStateOf(target.currentEntry?.customDish ?: "")
    }
    val filteredRecipes = remember(searchQuery, recipes) {
        if (searchQuery.isBlank()) {
            recipes
        } else {
            recipes.filter { recipe ->
                recipe.title.contains(searchQuery, ignoreCase = true) ||
                    recipe.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.menu_select_recipe_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${target.dayName} — ${mealLabel(target.mealType)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.menu_select_recipe_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredRecipes, key = { it.id }) { recipe ->
                    Surface(
                        modifier = Modifier.animateItem(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRecipe(recipe) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recipe.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (recipe.tags.isNotEmpty()) {
                                    Text(
                                        text = recipe.tags.joinToString(separator = ", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.menu_select_recipe_use),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.menu_custom_meal_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            OutlinedTextField(
                value = customDish,
                onValueChange = { customDish = it },
                placeholder = { Text(stringResource(R.string.menu_custom_meal_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            com.familyhub.ui.theme.components.FHButton(
                onClick = {
                    if (customDish.isNotBlank()) {
                        onSaveCustom(customDish.trim())
                    }
                },
                enabled = customDish.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.menu_select_recipe_custom_save))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onClear) {
                    Text(
                        text = stringResource(R.string.menu_select_recipe_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    }
}

@Composable
private fun MenuAnalyticsCard(
    analytics: MenuAnalytics,
    members: Map<String, FamilyMember>,
    modifier: Modifier = Modifier
) {
    ThemedCard(
        modifier = modifier,
        tone = FhCardTone.INFO
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.menu_analytics_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (analytics.autoGenerated) {
                Text(
                    text = stringResource(R.string.menu_analytics_auto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.menu_analytics_long_prep, analytics.longPrepMeals),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.menu_analytics_favorites, analytics.favoriteMeals),
                style = MaterialTheme.typography.bodyMedium
            )
            if (analytics.assignmentsByMember.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.menu_analytics_assignments),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                analytics.assignmentsByMember.forEach { (memberId, count) ->
                    val name = members[memberId]?.displayLabel() ?: stringResource(R.string.menu_assign_unknown)
                    Text(
                        text = "• $name — $count",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuHistorySection(
    currentWeekStart: Long,
    history: List<WeeklyMenu>,
    canManageMenu: Boolean,
    readOnlyEditCd: String,
    onCopy: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val pastWeeks = history.filter { it.startDateMillis != currentWeekStart }
        .sortedByDescending { it.startDateMillis }
        .take(6)
    if (pastWeeks.isEmpty()) return

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.menu_history_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(pastWeeks, key = { it.startDateMillis }) { week ->
                OutlinedButton(
                    onClick = { onCopy(week.startDateMillis) },
                    enabled = canManageMenu,
                    modifier = Modifier
                        .animateItem()
                        .semantics {
                        if (!canManageMenu) contentDescription = readOnlyEditCd
                    }
                ) {
                    Text(
                        text = stringResource(
                            R.string.menu_history_copy,
                            TimeUtils.formatDate(week.startDateMillis)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuTemplateDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var templateName by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_template_save_title)) },
        text = {
            FhDialogFormBody {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text(stringResource(R.string.menu_template_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(templateName.trim()) },
                enabled = templateName.isNotBlank(),
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.menu_template_save_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
private fun LoadMenuTemplateDialog(
    templates: List<MenuTemplate>,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_template_load_title)) },
        text = {
            if (templates.isEmpty()) {
                Text(
                    text = stringResource(R.string.menu_template_load_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(templates, key = { it.id }) { template ->
                        OutlinedButton(
                            onClick = { onApply(template.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = TimeUtils.formatDate(template.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuPreferencesDialog(
    preferences: MealPlanPreferences,
    onDismiss: () -> Unit,
    onSave: (MealPlanPreferences) -> Unit
) {
    var preferVegetarian by remember(preferences) { mutableStateOf(preferences.preferVegetarian) }
    var excludedTags by remember(preferences) { mutableStateOf(preferences.excludedTags.joinToString(", ")) }
    var maxPrep by remember(preferences) { mutableStateOf(preferences.maxPrepTimeMinutes.toString()) }
    var balanceDifficulty by remember(preferences) { mutableStateOf(preferences.balanceByDifficulty) }
    var rewardPromptHour by remember(preferences) { mutableStateOf(preferences.rewardPromptHour.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.menu_preferences_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FhSwitchRow(
                checked = preferVegetarian,
                onCheckedChange = { preferVegetarian = it },
                label = stringResource(R.string.menu_preferences_vegetarian),
            )
            FhSwitchRow(
                checked = balanceDifficulty,
                onCheckedChange = { balanceDifficulty = it },
                label = stringResource(R.string.menu_preferences_balance),
            )
            OutlinedTextField(
                value = excludedTags,
                onValueChange = { excludedTags = it },
                label = { Text(stringResource(R.string.menu_preferences_excluded_tags)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = maxPrep,
                onValueChange = { newValue -> maxPrep = newValue.filter { it.isDigit() } },
                label = { Text(stringResource(R.string.menu_preferences_max_prep)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = rewardPromptHour,
                onValueChange = { newValue -> rewardPromptHour = newValue.filter { it.isDigit() } },
                label = { Text(stringResource(R.string.menu_preferences_reward_prompt_time)) },
                placeholder = { Text(stringResource(R.string.menu_preferences_reward_prompt_time_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Button(
                onClick = {
                    val promptHour = rewardPromptHour.toIntOrNull()?.coerceIn(0, 23)
                        ?: preferences.rewardPromptHour
                    val prefs = MealPlanPreferences(
                        preferVegetarian = preferVegetarian,
                        excludedTags = excludedTags.split(',').mapNotNull { tag ->
                            val trimmed = tag.trim()
                            trimmed.takeIf { it.isNotEmpty() }
                        }.toSet(),
                        maxPrepTimeMinutes = maxPrep.toIntOrNull()
                            ?: preferences.maxPrepTimeMinutes,
                        balanceByDifficulty = balanceDifficulty,
                        rewardPromptHour = promptHour,
                    )
                    onSave(prefs)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.menu_save_preferences))
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}

@Composable
private fun AssignCookingDialog(
    dayName: String,
    mealType: MealType,
    members: List<FamilyMember>,
    currentAssigneeId: String?,
    onDismiss: () -> Unit,
    onAssign: (String?) -> Unit,
    onClear: () -> Unit
) {
    var selectedMemberId by remember(dayName, mealType, currentAssigneeId) { mutableStateOf(currentAssigneeId ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.menu_assign_dialog_title_day, dayName))
        },
        text = {
            FhDialogFormBody {
                Text(stringResource(R.string.menu_assign_select_member))
                members.forEach { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedMemberId == member.id,
                                role = Role.RadioButton,
                                onClick = { selectedMemberId = member.id },
                            )
                            .fhDialogAction()
                    ) {
                        RadioButton(
                            selected = selectedMemberId == member.id,
                            onClick = null
                        )
                        Text(
                            text = member.displayLabel(),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                TextButton(
                    onClick = { selectedMemberId = "" },
                    modifier = Modifier.fhDialogAction(),
                ) {
                    Text(stringResource(R.string.menu_assign_clear))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val memberId = selectedMemberId.ifBlank { null }
                    onAssign(memberId)
                },
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.menu_assign_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fhDialogAction(),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.menu_assign_remove))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fhDialogAction(),
                ) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    )
}

@Composable
private fun RewardPromptDialog(
    target: RewardPromptTarget,
    dayName: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var pointsInput by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.menu_reward_prompt_title, dayName))
        },
        text = {
            FhDialogFormBody {
                Text(text = stringResource(R.string.menu_reward_prompt_message))
                OutlinedTextField(
                    value = pointsInput,
                    onValueChange = { input -> pointsInput = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.menu_reward_prompt_points_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val points = pointsInput.toIntOrNull() ?: 0
                    onConfirm(points)
                },
                enabled = pointsInput.toIntOrNull() != null,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.menu_reward_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.menu_reward_prompt_cancel))
            }
        }
    )
}

private fun openNotificationSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}
