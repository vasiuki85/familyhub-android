@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package com.familyhub.ui.screens.shopping

import com.familyhub.ui.components.FhIcons
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.familyhub.ui.components.FhProgressBar
import com.familyhub.ui.components.FhScrollableHorizontalRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingItem
import com.familyhub.domain.model.ShoppingList
import com.familyhub.ui.screens.shopping.ShoppingTemplateUi
import com.familyhub.domain.model.ShoppingActivity
import com.familyhub.domain.model.ShoppingActivityType
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.components.FhDialogFormBody
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhSearchField
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.ShoppingItemRow
import com.familyhub.ui.components.SwipeToDeleteContainer
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.FhTheme
import com.familyhub.util.TimeUtils

private data class IconOption(
    val id: String,
    val label: String,
    val aliases: Set<String> = emptySet()
) {
    fun matches(value: String): Boolean = value == id || aliases.contains(value)
}

private val ListColorOptions = listOf("#4CAF50", "#03A9F4", "#FFB300", "#E91E63", "#9C27B0", "#607D8B")
private val ListIconOptions = listOf(
    IconOption(id = "cart", label = "🛒", aliases = setOf("🛒")),
    IconOption(id = "clipboard", label = "📋", aliases = setOf("📋")),
    IconOption(id = "party", label = "🎉", aliases = setOf("🎉")),
    IconOption(id = "dinner", label = "🍽", aliases = setOf("🍽")),
    IconOption(id = "laundry", label = "🧺", aliases = setOf("🧺")),
    IconOption(id = "medicine", label = "💊", aliases = setOf("💊"))
)
private val CategoryColorOptions = listOf("#4CAF50", "#FFB300", "#F06292", "#4DD0E1", "#A1887F", "#8BC34A")
private val CategoryIconOptions = listOf(
    IconOption(id = "fruits", label = "🥦", aliases = setOf("🥦")),
    IconOption(id = "meat", label = "🥩", aliases = setOf("🥩")),
    IconOption(id = "dairy", label = "🥛", aliases = setOf("🥛")),
    IconOption(id = "bakery", label = "🍞", aliases = setOf("🍞")),
    IconOption(id = "household", label = "🧴", aliases = setOf("🧴", "cleaning")),
    IconOption(id = "snacks", label = "🍬", aliases = setOf("🍬")),
    IconOption(id = "beverages", label = "🍷", aliases = setOf("🍷"))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var initialAddTitle by remember { mutableStateOf("") }
    var showListsDialog by remember { mutableStateOf(false) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    var listToRename by remember { mutableStateOf<ShoppingList?>(null) }
    var duplicateSource by remember { mutableStateOf<ShoppingList?>(null) }
    var categoryEditorState by remember { mutableStateOf<CategoryEditorState?>(null) }
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var listToDelete by remember { mutableStateOf<ShoppingList?>(null) }
    var categoryToDelete by remember { mutableStateOf<ShoppingCategory?>(null) }
    var participantsEditor by remember { mutableStateOf<ShoppingList?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var shoppingActionsExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val contentUi = ShoppingContentUiStateMapper.mapFromState(state)
    val displayedContentUi = if (
        !state.canMutateFamily &&
        contentUi.primaryAction != ShoppingContentAction.RESET_FILTERS
    ) {
        contentUi.copy(
            actionLabelResId = null,
            primaryAction = null,
            actionContentDescriptionResId = null,
            fabAction = ShoppingFabAction.NONE,
        )
    } else {
        contentUi
    }
    val showsFloatingActionButton =
        state.canMutateFamily && displayedContentUi.fabAction != ShoppingFabAction.NONE

    LaunchedEffect(state.canMutateFamily) {
        if (!state.canMutateFamily) {
            showAddDialog = false
            showListsDialog = false
            showCategoriesDialog = false
            listToRename = null
            duplicateSource = null
            categoryEditorState = null
            itemToDelete = null
            listToDelete = null
            categoryToDelete = null
            participantsEditor = null
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccessMessage()
        }
    }

    FhScreenScaffold(
        title = stringResource(R.string.shopping_topbar_title),
        titleContent = {
            ShoppingListTitleContent(
                state = state,
                onSelectList = { viewModel.selectList(it) },
            )
        },
        actions = {
            Box {
                IconButton(onClick = { shoppingActionsExpanded = true }) {
                    Icon(
                        imageVector = FhIcons.MoreVert,
                        contentDescription = stringResource(R.string.shopping_v2_more_actions_cd),
                    )
                }
                DropdownMenu(
                    expanded = shoppingActionsExpanded,
                    onDismissRequest = { shoppingActionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_history_open)) },
                        leadingIcon = {
                            Icon(imageVector = FhIcons.History, contentDescription = null)
                        },
                        onClick = {
                            shoppingActionsExpanded = false
                            showHistory = true
                        },
                    )
                    if (state.canMutateFamily) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_manage_categories)) },
                            leadingIcon = {
                                Icon(imageVector = FhIcons.Label, contentDescription = null)
                            },
                            onClick = {
                                shoppingActionsExpanded = false
                                showCategoriesDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_manage_lists)) },
                            leadingIcon = {
                                Icon(imageVector = FhIcons.Settings, contentDescription = null)
                            },
                            onClick = {
                                shoppingActionsExpanded = false
                                showListsDialog = true
                            },
                        )
                    }
                }
            }
        },
        snackbarHostState = snackbarHostState,
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
        floatingActionButton = {
            if (state.canMutateFamily) {
                when (displayedContentUi.fabAction) {
                    ShoppingFabAction.CREATE_LIST -> {
                        FloatingActionButton(
                            onClick = { showListsDialog = true },
                            containerColor = FhTheme.modules.shopping.accent,
                            contentColor = FhTheme.modules.shopping.onAccent,
                        ) {
                            Icon(
                                imageVector = FhIcons.Add,
                                contentDescription = stringResource(R.string.shopping_empty_no_lists_fab_cd),
                            )
                        }
                    }
                    ShoppingFabAction.ADD_ITEM -> {
                        FloatingActionButton(
                            onClick = {
                                initialAddTitle = ""
                                showAddDialog = true
                            },
                            containerColor = FhTheme.modules.shopping.accent,
                            contentColor = FhTheme.modules.shopping.onAccent,
                        ) {
                            Icon(
                                imageVector = FhIcons.Add,
                                contentDescription = stringResource(R.string.shopping_add_item),
                            )
                        }
                    }
                    ShoppingFabAction.NONE -> Unit
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!state.isLoading && state.totalCount > 0) {
                ShoppingProgressSummary(
                    boughtCount = state.boughtCount,
                    totalCount = state.totalCount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = FhTheme.spacing.lg,
                            vertical = FhTheme.spacing.sm,
                        ),
                )
            }

            if (contentUi.showSearchAndFilters) {
                FhSearchField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = stringResource(R.string.shopping_search_hint),
                    searchContentDescription = stringResource(R.string.shopping_search_cd),
                    clearContentDescription = stringResource(R.string.shopping_search_clear_cd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FhTheme.spacing.lg),
                    accentColor = FhTheme.modules.shopping.accent,
                )

                FilterSection(
                    state = state,
                    onFilterSelected = { viewModel.applyFilter(it) }
                )
            }

            if (contentUi.showQuickSuggestions) {
                QuickSuggestionsRow(
                    suggestions = state.quickSuggestions,
                    onSuggestion = {
                        if (state.canMutateFamily) {
                            initialAddTitle = it
                            showAddDialog = true
                        }
                    },
                    modifier = Modifier.padding(
                        bottom = FhTheme.spacing.xs,
                    ),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        FhListSkeleton(modifier = Modifier.fillMaxSize())
                    }

                    displayedContentUi.showsEmptyState -> {
                        ShoppingContentEmptyState(
                            model = displayedContentUi,
                            onPrimaryAction = {
                                when (displayedContentUi.primaryAction) {
                                    ShoppingContentAction.CREATE_LIST -> showListsDialog = true
                                    ShoppingContentAction.ADD_ITEM -> {
                                        initialAddTitle = ""
                                        showAddDialog = true
                                    }
                                    ShoppingContentAction.RESET_FILTERS -> viewModel.resetSearchAndFilter()
                                    null -> Unit
                                }
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        val groupedItems = state.groupedItems
                        val showCategoryHeaders = state.activeFilter !is ShoppingFilter.Category
                        val membersById = state.members.associateBy { it.id }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = FhTheme.spacing.lg,
                                end = FhTheme.spacing.lg,
                                top = FhTheme.spacing.sm,
                                bottom = ShoppingScreenLayoutPolicy.listBottomPadding(
                                    showsFloatingActionButton = showsFloatingActionButton,
                                ),
                            ),
                            verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
                        ) {
                            groupedItems.forEach { section ->
                                val headerColor = section.category?.colorHex ?: "#9E9E9E"
                                if (showCategoryHeaders) {
                                    item(key = "header_${section.category?.id ?: "none"}") {
                                        val headerTitle = section.category?.title
                                            ?: stringResource(R.string.shopping_category_none_header)
                                        CategoryHeader(
                                            title = headerTitle,
                                            colorHex = headerColor,
                                            itemCount = section.items.size,
                                        )
                                    }
                                }
                                items(section.items, key = { it.id }) { item ->
                                    val creator = membersById[item.createdByUserId]
                                    ShoppingItemRow(
                                        item = item,
                                        category = section.category,
                                        creator = creator,
                                        showCategoryInMeta = false,
                                        onStatusChange = if (state.canMutateFamily) {
                                            { viewModel.toggleItemStatus(item) }
                                        } else {
                                            null
                                        },
                                        onDelete = if (state.canMutateFamily) {
                                            { itemToDelete = item }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && state.canMutateFamily) {
        ShoppingAddDialog(
            categories = state.categories,
            suggestions = state.nameSuggestions,
            initialTitle = initialAddTitle,
            onManageCategories = { showCategoriesDialog = true },
            onDismiss = {
                showAddDialog = false
                initialAddTitle = ""
            },
            onAddAnother = { title, categoryId, quantity, unit, note ->
                viewModel.addItem(title, categoryId, quantity, unit, note)
            },
            onConfirm = { title, categoryId, quantity, unit, note ->
                viewModel.addItem(title, categoryId, quantity, unit, note)
                showAddDialog = false
                initialAddTitle = ""
            },
        )
    }

    if (showListsDialog && listToDelete == null && state.canMutateFamily) {
        ManageShoppingListsDialog(
            lists = state.lists,
            archivedLists = state.archivedLists,
            members = state.members,
            templates = state.templates,
            onAddList = { title, color, icon, templateKey, participantIds ->
                viewModel.addList(title, color, icon, templateKey, participantIds)
            },
            onRenameList = { listToRename = it },
            onDeleteList = { listToDelete = it },
            onDuplicateList = { duplicateSource = it },
            onArchiveList = { viewModel.archiveList(it) },
            onRestoreList = { viewModel.restoreList(it) },
            onEditParticipants = { participantsEditor = it },
            onDismiss = { showListsDialog = false }
        )
    }

    listToRename?.takeIf { state.canMutateFamily }?.let { list ->
        ListRenameDialog(
            list = list,
            onConfirm = { newName -> viewModel.renameList(list, newName) },
            onDismiss = { listToRename = null }
        )
    }

    duplicateSource?.takeIf { state.canMutateFamily }?.let { list ->
        ShoppingListDuplicateDialog(
            list = list,
            onConfirm = { title ->
                viewModel.duplicateList(list, title)
                duplicateSource = null
            },
            onDismiss = { duplicateSource = null }
        )
    }

    participantsEditor?.takeIf { state.canMutateFamily }?.let { list ->
        ShoppingListParticipantsDialog(
            list = list,
            members = state.members,
            onConfirm = { participants ->
                viewModel.updateListParticipants(list, participants)
                participantsEditor = null
            },
            onDismiss = { participantsEditor = null }
        )
    }

    if (showCategoriesDialog && categoryToDelete == null && state.canMutateFamily) {
        ManageShoppingCategoriesDialog(
            categories = state.categories,
            onAddRequest = {
                categoryEditorState = CategoryEditorState()
            },
            onEditRequest = { category ->
                categoryEditorState = CategoryEditorState(category)
            },
            onDeleteCategory = { categoryToDelete = it },
            onDismiss = { showCategoriesDialog = false }
        )
    }

    if (showHistory) {
        ShoppingHistoryBottomSheet(
            history = state.history,
            members = state.members,
            templates = state.templates,
            hasMore = state.historyHasMore,
            onLoadMoreHistory = { viewModel.loadMoreHistory() },
            onDismiss = { showHistory = false }
        )
    }

    categoryEditorState?.takeIf { state.canMutateFamily }?.let { editorState ->
        CategoryEditorDialog(
            initialTitle = editorState.initialTitle,
            initialColor = editorState.initialColor,
            initialIcon = editorState.initialIcon,
            onDismiss = { categoryEditorState = null },
            onConfirm = { title, color, icon ->
                if (editorState.category == null) {
                    viewModel.addCategory(title, color, icon)
                } else {
                    viewModel.updateCategory(editorState.category, title, color, icon)
                }
                categoryEditorState = null
            }
        )
    }

    itemToDelete?.takeIf { state.canMutateFamily }?.let { item ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete_shopping_title),
            message = stringResource(R.string.delete_shopping_message, item.title),
            onDismiss = { itemToDelete = null },
            onConfirm = {
                viewModel.deleteItem(item)
                itemToDelete = null
            }
        )
    }

    listToDelete?.takeIf { state.canMutateFamily }?.let { list ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.shopping_list_delete_confirm_title),
            message = stringResource(R.string.shopping_list_delete_confirm_message, list.title),
            onDismiss = { listToDelete = null },
            onConfirm = {
                viewModel.deleteList(list)
                listToDelete = null
            },
        )
    }

    categoryToDelete?.takeIf { state.canMutateFamily }?.let { category ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.shopping_category_delete_confirm_title),
            message = stringResource(R.string.shopping_category_delete_confirm_message, category.title),
            onDismiss = { categoryToDelete = null },
            onConfirm = {
                viewModel.deleteCategory(category)
                categoryToDelete = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@VisibleForTesting
@Composable
internal fun ShoppingListTitleContent(
    state: ShoppingUiState,
    onSelectList: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        val lists = state.lists
        if (lists.isEmpty()) {
            Text(
                text = stringResource(R.string.shopping_topbar_title),
                style = MaterialTheme.typography.titleLarge,
            )
        } else {
            var expanded by remember { mutableStateOf(false) }
            val selectedList = lists.firstOrNull { it.id == state.selectedListId }
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FhTheme.radius.chip),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = FhTheme.spacing.md,
                            vertical = FhTheme.spacing.xs,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    selectedList?.let {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(colorFromHex(it.colorHex), CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.size(FhTheme.spacing.sm))
                    Text(
                        text = selectedList?.title
                            ?: stringResource(R.string.shopping_topbar_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = FhIcons.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                lists.forEach { list ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(colorFromHex(list.colorHex), CircleShape)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = list.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectList(list.id)
                        }
                    )
                }
            }
        }
    }
}

@VisibleForTesting
internal object ShoppingScreenLayoutPolicy {
    fun listBottomPadding(showsFloatingActionButton: Boolean) =
        FhLayout.listBottomPadding(showsFloatingActionButton)
}

@VisibleForTesting
@Composable
internal fun ShoppingProgressSummary(
    boughtCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val progress = if (totalCount == 0) 0f else boughtCount.toFloat() / totalCount
    val remaining = (totalCount - boughtCount).coerceAtLeast(0)
    val progressMessage = if (remaining == 0 && totalCount > 0) {
        stringResource(R.string.shopping_module_progress_complete)
    } else {
        stringResource(R.string.shopping_module_progress_remaining, remaining)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(FhTheme.radius.card),
        color = FhTheme.modules.shopping.container,
        contentColor = FhTheme.modules.shopping.onContainer,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = FhTheme.spacing.md,
                vertical = FhTheme.spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = progressMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.shopping_progress, boughtCount, totalCount),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            FhProgressBar(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = FhTheme.modules.shopping.accent,
                trackColor = FhTheme.modules.shopping.onContainer.copy(alpha = 0.12f),
            )
        }
    }
}

@VisibleForTesting
@Composable
internal fun FilterSection(
    state: ShoppingUiState,
    onFilterSelected: (ShoppingFilter) -> Unit
) {
    val selectedIndex = when (val filter = state.activeFilter) {
        is ShoppingFilter.All -> 0
        is ShoppingFilter.NotBought -> 1
        is ShoppingFilter.Category -> {
            val categoryIndex = state.categories.indexOfFirst { it.id == filter.categoryId }
            if (categoryIndex >= 0) categoryIndex + 2 else 0
        }
    }

    FhScrollableHorizontalRow(
        selectedIndex = selectedIndex,
        contentPadding = PaddingValues(horizontal = FhTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
    ) {
        item {
            FilterChip(
                selected = state.activeFilter is ShoppingFilter.All,
                onClick = { onFilterSelected(ShoppingFilter.All) },
                label = { Text(stringResource(R.string.shopping_filter_all)) },
                colors = shoppingFilterChipColors(),
            )
        }
        item {
            FilterChip(
                selected = state.activeFilter is ShoppingFilter.NotBought,
                onClick = { onFilterSelected(ShoppingFilter.NotBought) },
                label = { Text(stringResource(R.string.shopping_filter_not_bought)) },
                colors = shoppingFilterChipColors(),
            )
        }
        items(state.categories, key = { it.id }) { category ->
            FilterChip(
                modifier = Modifier.animateItem(),
                selected = state.activeFilter is ShoppingFilter.Category &&
                    state.activeFilter.categoryId == category.id,
                onClick = { onFilterSelected(ShoppingFilter.Category(category.id)) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colorFromHex(category.colorHex), CircleShape)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(category.title)
                    }
                },
                colors = shoppingFilterChipColors(),
            )
        }
    }
}

@VisibleForTesting
@Composable
internal fun QuickSuggestionsRow(
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val quickAddRowCd = stringResource(R.string.shopping_quick_add_row_cd)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.shopping_quick_add_heading),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = FhTheme.spacing.lg),
        )
        FhScrollableHorizontalRow(
            contentPadding = PaddingValues(horizontal = FhTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
            modifier = Modifier.semantics {
                contentDescription = quickAddRowCd
            },
        ) {
            items(suggestions) { suggestion ->
                AssistChip(
                    onClick = { onSuggestion(suggestion) },
                    label = { Text(suggestion) },
                    leadingIcon = {
                        Icon(
                            imageVector = FhIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun shoppingFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    selectedContainerColor = FhTheme.modules.shopping.container,
    selectedLabelColor = FhTheme.modules.shopping.onContainer,
    selectedLeadingIconColor = FhTheme.modules.shopping.onContainer,
)

@Composable
private fun CategoryHeader(
    title: String,
    colorHex: String,
    itemCount: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FhTheme.spacing.xs),
        shape = RoundedCornerShape(FhTheme.radius.chip),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = FhTheme.spacing.md,
                vertical = FhTheme.spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(colorFromHex(colorHex), CircleShape),
            )
            Spacer(modifier = Modifier.size(FhTheme.spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = itemCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManageShoppingListsDialog(
    lists: List<ShoppingList>,
    archivedLists: List<ShoppingList>,
    members: List<FamilyMember>,
    templates: List<ShoppingTemplateUi>,
    onAddList: (String, String, String, String?, List<String>) -> Unit,
    onRenameList: (ShoppingList) -> Unit,
    onDeleteList: (ShoppingList) -> Unit,
    onDuplicateList: (ShoppingList) -> Unit,
    onArchiveList: (ShoppingList) -> Unit,
    onRestoreList: (ShoppingList) -> Unit,
    onEditParticipants: (ShoppingList) -> Unit,
    onDismiss: () -> Unit
) {
    var newListName by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedColor by remember { mutableStateOf(ListColorOptions.first()) }
    var selectedIcon by remember { mutableStateOf(ListIconOptions.first().id) }
    var selectedTemplateKey by remember { mutableStateOf<String?>(null) }
    var selectedParticipants by remember(members) { mutableStateOf(members.map { it.id }.toSet()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_manage_lists_title)) },
        text = {
            FhDialogFormBody {
                Text(stringResource(R.string.shopping_manage_lists_active_header), style = MaterialTheme.typography.titleSmall)
                if (lists.isEmpty()) {
                    Text(stringResource(R.string.shopping_manage_lists_empty))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lists.forEach { list ->
                            ShoppingListManagementRow(
                                list = list,
                                onRename = { onRenameList(list) },
                                onDuplicate = { onDuplicateList(list) },
                                onEditParticipants = { onEditParticipants(list) },
                                onArchive = { onArchiveList(list) },
                                onDelete = { onDeleteList(list) },
                            )
                        }
                    }
                }

                if (archivedLists.isNotEmpty()) {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Icon(
                            imageVector = if (showArchived) FhIcons.KeyboardArrowUp else FhIcons.KeyboardArrowDown,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = stringResource(
                                if (showArchived) R.string.shopping_manage_lists_archived_hide else R.string.shopping_manage_lists_archived_show,
                                archivedLists.size
                            )
                        )
                    }
                    AnimatedVisibility(showArchived) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.shopping_manage_lists_archived_header),
                                style = MaterialTheme.typography.titleSmall
                            )
                            archivedLists.forEach { list ->
                                ShoppingListManagementRow(
                                    list = list,
                                    onRestore = { onRestoreList(list) },
                                    onDelete = { onDeleteList(list) },
                                )
                            }
                        }
                    }
                }

                Text(stringResource(R.string.shopping_manage_lists_create_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = newListName,
                    onValueChange = {
                        newListName = it
                        nameError = null
                    },
                    label = { Text(stringResource(R.string.shopping_manage_lists_new_name)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } }
                )

                if (templates.isNotEmpty()) {
                    Text(stringResource(R.string.shopping_manage_lists_template_label), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { template ->
                            FilterChip(
                                selected = selectedTemplateKey == template.key,
                                onClick = {
                                    selectedTemplateKey = if (selectedTemplateKey == template.key) null else template.key
                                },
                                label = { Text(template.title) }
                            )
                        }
                    }
                    selectedTemplateKey?.let { key ->
                        templates.firstOrNull { it.key == key }?.let { selectedTemplate ->
                            Text(
                                text = selectedTemplate.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(stringResource(R.string.shopping_manage_lists_participants_label), style = MaterialTheme.typography.bodySmall)
                if (members.isEmpty()) {
                    Text(stringResource(R.string.shopping_manage_lists_no_members), style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedParticipants = members.map { it.id }.toSet() }) {
                            Text(stringResource(R.string.shopping_participants_select_all))
                        }
                        TextButton(onClick = { selectedParticipants = emptySet() }) {
                            Text(stringResource(R.string.shopping_participants_clear_all))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { member ->
                            val selected = selectedParticipants.contains(member.id)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedParticipants = if (selected) {
                                        selectedParticipants - member.id
                                    } else {
                                        selectedParticipants + member.id
                                    }
                                },
                                label = { Text(member.displayLabel()) }
                            )
                        }
                    }
                }

                ColorSelector(
                    options = ListColorOptions,
                    selected = selectedColor,
                    onSelect = { selectedColor = it }
                )
                IconSelector(
                    options = ListIconOptions,
                    selectedValue = selectedIcon,
                    onSelect = { selectedIcon = it }
                )
                com.familyhub.ui.theme.components.FHTonalButton(onClick = {
                    if (newListName.isBlank()) {
                        nameError = context.getString(R.string.validation_item_name_empty)
                    } else {
                        onAddList(newListName.trim(), selectedColor, selectedIcon, selectedTemplateKey, selectedParticipants.toList())
                        newListName = ""
                        selectedColor = ListColorOptions.first()
                        selectedIcon = ListIconOptions.first().id
                        selectedTemplateKey = null
                        selectedParticipants = members.map { it.id }.toSet()
                    }
                }) {
                    Text(stringResource(R.string.shopping_manage_lists_add))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.shopping_close))
            }
        }
    )
}

@Composable
private fun ShoppingListManagementRow(
    list: ShoppingList,
    onRename: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onEditParticipants: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDelete: () -> Unit,
) {
    var actionsExpanded by remember(list.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = iconLabelForList(list.iconName),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(end = 8.dp),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(colorFromHex(list.colorHex), CircleShape),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = list.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { actionsExpanded = true }) {
                Icon(
                    imageVector = FhIcons.MoreVert,
                    contentDescription = stringResource(R.string.shopping_v2_more_actions_cd),
                )
            }
            DropdownMenu(
                expanded = actionsExpanded,
                onDismissRequest = { actionsExpanded = false },
            ) {
                onRename?.let { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_list_rename)) },
                        leadingIcon = { Icon(FhIcons.Edit, contentDescription = null) },
                        onClick = {
                            actionsExpanded = false
                            action()
                        },
                    )
                }
                onDuplicate?.let { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_list_duplicate)) },
                        leadingIcon = { Icon(FhIcons.ContentCopy, contentDescription = null) },
                        onClick = {
                            actionsExpanded = false
                            action()
                        },
                    )
                }
                onEditParticipants?.let { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_list_participants_edit)) },
                        leadingIcon = { Icon(FhIcons.Group, contentDescription = null) },
                        onClick = {
                            actionsExpanded = false
                            action()
                        },
                    )
                }
                onArchive?.let { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_list_archive)) },
                        leadingIcon = { Icon(FhIcons.Archive, contentDescription = null) },
                        onClick = {
                            actionsExpanded = false
                            action()
                        },
                    )
                }
                onRestore?.let { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_list_restore)) },
                        leadingIcon = { Icon(FhIcons.Restore, contentDescription = null) },
                        onClick = {
                            actionsExpanded = false
                            action()
                        },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.shopping_list_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = FhIcons.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        actionsExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun ListRenameDialog(
    list: ShoppingList,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(list.title) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_list_rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = {
                    newName = it
                    error = null
                },
                label = { Text(stringResource(R.string.shopping_manage_lists_new_name)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isBlank()) {
                    error = context.getString(R.string.validation_item_name_empty)
                } else {
                    onConfirm(newName.trim())
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.shopping_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shopping_cancel)) }
        }
    )
}

@Composable
private fun ShoppingListDuplicateDialog(
    list: ShoppingList,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultTitle = stringResource(R.string.shopping_list_duplicate_default, list.title)
    var newName by remember(list) { mutableStateOf(defaultTitle) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_list_duplicate_title)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = {
                    newName = it
                    error = null
                },
                label = { Text(stringResource(R.string.shopping_manage_lists_new_name)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isBlank()) {
                    error = context.getString(R.string.validation_item_name_empty)
                } else {
                    onConfirm(newName.trim())
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.shopping_duplicate_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shopping_cancel)) }
        }
    )
}

@Composable
private fun ShoppingListParticipantsDialog(
    list: ShoppingList,
    members: List<FamilyMember>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(list, members) {
        mutableStateOf(
            if (list.participantIds.isEmpty()) members.map { it.id }.toSet() else list.participantIds.toSet()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_participants_title, list.title)) },
        text = {
            FhDialogFormBody {
                if (members.isEmpty()) {
                    Text(stringResource(R.string.shopping_manage_lists_no_members))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selected = members.map { it.id }.toSet() }) {
                            Text(stringResource(R.string.shopping_participants_select_all))
                        }
                        TextButton(onClick = { selected = emptySet() }) {
                            Text(stringResource(R.string.shopping_participants_clear_all))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { member ->
                            val isSelected = selected.contains(member.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selected = if (isSelected) {
                                        selected - member.id
                                    } else {
                                        selected + member.id
                                    }
                                },
                                label = { Text(member.displayLabel()) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.shopping_participants_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selected.toList())
                onDismiss()
            }) {
                Text(stringResource(R.string.shopping_participants_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shopping_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingHistoryBottomSheet(
    history: List<ShoppingActivity>,
    members: List<FamilyMember>,
    templates: List<ShoppingTemplateUi>,
    hasMore: Boolean,
    onLoadMoreHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.shopping_history_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.shopping_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history, key = { it.id }) { entry ->
                        Column(
                            modifier = Modifier.animateItem(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = formatHistoryMessage(entry, members, templates),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = TimeUtils.formatDateTime(entry.occurredAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (hasMore) {
                        item {
                            TextButton(
                                onClick = onLoadMoreHistory,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.shopping_history_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatHistoryMessage(
    activity: ShoppingActivity,
    members: List<FamilyMember>,
    templates: List<ShoppingTemplateUi>
): String {
    val actorName = activity.payload[ShoppingViewModel.PAYLOAD_ACTOR_NAME]
        ?: members.firstOrNull { it.id == activity.actorId }?.displayLabel()
        ?: stringResource(R.string.shopping_member_unknown)
    val listTitle = activity.payload[ShoppingViewModel.PAYLOAD_LIST_TITLE] ?: ""
    return when (activity.actionType) {
        ShoppingActivityType.ITEM_ADDED -> {
            val itemTitle = activity.payload[ShoppingViewModel.PAYLOAD_ITEM_TITLE] ?: ""
            stringResource(R.string.shopping_notification_item_added, actorName, itemTitle)
        }
        ShoppingActivityType.ITEM_COMPLETED -> {
            val itemTitle = activity.payload[ShoppingViewModel.PAYLOAD_ITEM_TITLE] ?: ""
            stringResource(R.string.shopping_notification_item_completed, actorName, itemTitle)
        }
        ShoppingActivityType.ITEM_REOPENED -> {
            val itemTitle = activity.payload[ShoppingViewModel.PAYLOAD_ITEM_TITLE] ?: ""
            stringResource(R.string.shopping_notification_item_reopened, actorName, itemTitle)
        }
        ShoppingActivityType.ITEM_DELETED -> {
            val itemTitle = activity.payload[ShoppingViewModel.PAYLOAD_ITEM_TITLE] ?: ""
            stringResource(R.string.shopping_notification_item_deleted, actorName, itemTitle)
        }
        ShoppingActivityType.LIST_CREATED ->
            stringResource(R.string.shopping_notification_list_created, actorName, listTitle)
        ShoppingActivityType.LIST_ARCHIVED ->
            stringResource(R.string.shopping_notification_list_archived, actorName, listTitle)
        ShoppingActivityType.LIST_RESTORED ->
            stringResource(R.string.shopping_notification_list_restored, actorName, listTitle)
        ShoppingActivityType.LIST_DUPLICATED -> {
            val sourceTitle = activity.payload[ShoppingViewModel.PAYLOAD_SOURCE_TITLE] ?: ""
            stringResource(R.string.shopping_notification_list_duplicated, actorName, sourceTitle)
        }
        ShoppingActivityType.LIST_TEMPLATE_APPLIED -> {
            val templateKey = activity.payload[ShoppingViewModel.PAYLOAD_TEMPLATE_KEY] ?: ""
            val templateTitle = templates.firstOrNull { it.key == templateKey }?.title ?: templateKey
            stringResource(R.string.shopping_notification_template_applied, actorName, templateTitle)
        }
        ShoppingActivityType.MEMBER_ADDED -> {
            val memberName = activity.payload[ShoppingViewModel.PAYLOAD_MEMBER_NAME] ?: ""
            stringResource(R.string.shopping_notification_member_added, actorName, memberName)
        }
        ShoppingActivityType.MEMBER_REMOVED -> {
            val memberName = activity.payload[ShoppingViewModel.PAYLOAD_MEMBER_NAME] ?: ""
            stringResource(R.string.shopping_notification_member_removed, actorName, memberName)
        }
    }
}

@Composable
private fun ManageShoppingCategoriesDialog(
    categories: List<ShoppingCategory>,
    onAddRequest: () -> Unit,
    onEditRequest: (ShoppingCategory) -> Unit,
    onDeleteCategory: (ShoppingCategory) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_manage_categories_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (categories.isEmpty()) {
                    Text(stringResource(R.string.shopping_manage_categories_empty))
                } else {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = iconLabelForCategory(category.iconName),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(colorFromHex(category.colorHex), CircleShape)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(category.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row {
                                IconButton(onClick = { onEditRequest(category) }) {
                                    Icon(imageVector = FhIcons.Edit, contentDescription = stringResource(R.string.shopping_category_edit))
                                }
                                IconButton(onClick = { onDeleteCategory(category) }) {
                                    Icon(imageVector = FhIcons.Close, contentDescription = stringResource(R.string.shopping_category_delete))
                                }
                            }
                        }
                    }
                }
    com.familyhub.ui.theme.components.FHTonalButton(onClick = onAddRequest) {
                    Text(stringResource(R.string.shopping_manage_categories_add))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.shopping_close))
            }
        }
    )
}

@Composable
private fun CategoryEditorDialog(
    initialTitle: String,
    initialColor: String,
    initialIcon: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var selectedColor by remember { mutableStateOf(initialColor.ifBlank { CategoryColorOptions.first() }) }
    val normalizedInitialIcon = remember(initialIcon) { resolveIconSelection(initialIcon, CategoryIconOptions) }
    var selectedIcon by remember { mutableStateOf(normalizedInitialIcon) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_category_editor_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.shopping_category_name_label)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
                ColorSelector(
                    options = CategoryColorOptions,
                    selected = selectedColor,
                    onSelect = { selectedColor = it }
                )
                IconSelector(
                    options = CategoryIconOptions,
                    selectedValue = selectedIcon,
                    onSelect = { selectedIcon = it }
                )
                if (CategoryIconOptions.none { it.matches(selectedIcon) } && selectedIcon.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.shopping_icon_legacy_hint, selectedIcon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    error = context.getString(R.string.validation_item_name_empty)
                } else {
                    onConfirm(title.trim(), selectedColor, selectedIcon)
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.shopping_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shopping_cancel)) }
        }
    )
}

@Composable
private fun ColorSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val color = colorFromHex(option)
            val isSelected = selected == option
            val optionDescription = stringResource(
                R.string.shopping_color_option_cd,
                index + 1,
                options.size,
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .semantics {
                        contentDescription = optionDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun IconSelector(
    options: List<IconOption>,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option.matches(selectedValue),
                onClick = { onSelect(option.id) },
                label = { Text(option.label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
        }
    }
}

private fun resolveIconSelection(initial: String, options: List<IconOption>): String {
    if (initial.isBlank()) return options.first().id
    val normalized = options.firstOrNull { it.matches(initial) }?.id
    return normalized ?: initial
}

private fun iconLabelForList(iconName: String): String {
    return ListIconOptions.firstOrNull { it.matches(iconName) }?.label ?: iconName
}

private fun iconLabelForCategory(iconName: String): String {
    return CategoryIconOptions.firstOrNull { it.matches(iconName) }?.label ?: iconName
}

@Composable
private fun colorFromHex(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: IllegalArgumentException) {
    MaterialTheme.colorScheme.outline
}

private data class CategoryEditorState(
    val category: ShoppingCategory? = null
) {
    val initialTitle: String = category?.title ?: ""
    val initialColor: String = category?.colorHex ?: CategoryColorOptions.first()
    val initialIcon: String = category?.iconName?.let { resolveIconSelection(it, CategoryIconOptions) }
        ?: CategoryIconOptions.first().id
}
