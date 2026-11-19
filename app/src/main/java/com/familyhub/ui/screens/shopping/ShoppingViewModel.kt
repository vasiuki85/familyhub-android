package com.familyhub.ui.screens.shopping

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.notification.ShoppingNotificationManager
import com.familyhub.data.notification.ShoppingNotificationResult
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.utils.displayLabel
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.ShoppingActivity
import com.familyhub.domain.model.ShoppingActivityType
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingItem
import com.familyhub.domain.model.ShoppingItemUnit
import com.familyhub.domain.model.ShoppingList
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShoppingFilter {
    object All : ShoppingFilter
    object NotBought : ShoppingFilter
    data class Category(val categoryId: String) : ShoppingFilter
}

data class ShoppingCategorySection(
    val category: ShoppingCategory?,
    val items: List<ShoppingItem>
)

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val lists: List<ShoppingList> = emptyList(),
    val archivedLists: List<ShoppingList> = emptyList(),
    val selectedListId: String? = null,
    val items: List<ShoppingItem> = emptyList(),
    val categories: List<ShoppingCategory> = emptyList(),
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING,
    val searchQuery: String = "",
    val activeFilter: ShoppingFilter = ShoppingFilter.All,
    val members: List<FamilyMember> = emptyList(),
    val quickSuggestions: List<String> = emptyList(),
    val nameSuggestions: List<String> = emptyList(),
    val history: List<ShoppingActivity> = emptyList(),
    val historyHasMore: Boolean = false,
    val templates: List<ShoppingTemplateUi> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val canMutateFamily: Boolean
        get() = currentUserRole != MemberRole.PENDING

    val filteredItems: List<ShoppingItem>
        get() {
            val base = when (val filter = activeFilter) {
                ShoppingFilter.All -> items
                ShoppingFilter.NotBought -> items.filter { !it.isBought }
                is ShoppingFilter.Category -> items.filter { it.categoryId == filter.categoryId }
            }
            if (searchQuery.isBlank()) return base
            return base.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

    val selectedList: ShoppingList?
        get() = lists.firstOrNull { it.id == selectedListId }

    val boughtCount: Int
        get() = items.count { it.isBought }

    val totalCount: Int
        get() = items.size

    val groupedItems: List<ShoppingCategorySection>
        get() {
            if (filteredItems.isEmpty()) return emptyList()
            val categoryMap = categories.associateBy { it.id }
            val itemsByCategory = filteredItems.groupBy { it.categoryId }
            val comparator = compareBy<ShoppingItem> { it.isBought }
                .thenByDescending { it.createdAt }
            val sections = mutableListOf<ShoppingCategorySection>()

            fun addSection(categoryId: String?) {
                val unsortedItems = itemsByCategory[categoryId] ?: return
                val category = categoryId?.let { categoryMap[it] }
                sections.add(
                    ShoppingCategorySection(
                        category = category,
                        items = unsortedItems.sortedWith(comparator)
                    )
                )
            }

            categories.forEach { category ->
                val items = itemsByCategory[category.id] ?: return@forEach
                if (items.any { !it.isBought }) {
                    addSection(category.id)
                }
            }

            itemsByCategory[null]?.takeIf { items -> items.any { !it.isBought } }?.let {
                sections.add(
                    ShoppingCategorySection(
                        category = null,
                        items = it.sortedWith(comparator)
                    )
                )
            }

            categories.forEach { category ->
                val items = itemsByCategory[category.id] ?: return@forEach
                if (items.isNotEmpty() && items.all { it.isBought }) {
                    addSection(category.id)
                }
            }

            itemsByCategory[null]?.takeIf { items ->
                items.isNotEmpty() && items.all { it.isBought }
            }?.let {
                sections.add(
                    ShoppingCategorySection(
                        category = null,
                        items = it.sortedWith(comparator)
                    )
                )
            }

            return sections
        }
}

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val memberRepository: FamilyMemberRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val idGenerator: IdGenerator,
    private val shoppingNotificationManager: ShoppingNotificationManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val selectedListId = MutableStateFlow<String?>(null)
    private val historyLimit = MutableStateFlow(HISTORY_PAGE_SIZE)

    private val templateDefinitions = createTemplateDefinitions()

    private val _state = MutableStateFlow(
        ShoppingUiState(
            quickSuggestions = DEFAULT_SUGGESTIONS,
            templates = buildTemplateUi()
        )
    )
    val state: StateFlow<ShoppingUiState> = _state.asStateFlow()

    private var lastNotifiedActivityId: String? = null

    init {
        viewModelScope.launch { shoppingRepository.ensureDefaultList() }
        observeBaseData()
        observeListContent()
    }

    private fun observeBaseData() {
        viewModelScope.launch {
            try {
                combine(
                    appSettingsDataStore.settingsFlow,
                    shoppingRepository.observeLists(),
                    memberRepository.getAllMembers()
                ) { settings, lists, members ->
                    Triple(settings, lists, members)
                }.collect { (settings, lists, members) ->
                    val activeLists = lists.filterNot { it.isArchived }
                    val archivedLists = lists.filter { it.isArchived }
                    val resolvedListId = resolveSelectedListId(settings.activeShoppingListId, activeLists)
                    when {
                        resolvedListId != null && resolvedListId != selectedListId.value ->
                            applySelectedList(resolvedListId)
                        resolvedListId == null && activeLists.isNotEmpty() -> {
                            val fallback = activeLists.first().id
                            applySelectedList(fallback)
                            viewModelScope.launch { appSettingsDataStore.setActiveShoppingList(fallback) }
                        }
                        activeLists.isEmpty() -> {
                            historyLimit.value = HISTORY_PAGE_SIZE
                            selectedListId.value = null
                            lastNotifiedActivityId = null
                        }
                    }
                    if (resolvedListId != null && settings.activeShoppingListId != resolvedListId) {
                        viewModelScope.launch { appSettingsDataStore.setActiveShoppingList(resolvedListId) }
                    }
                    _state.update { current ->
                        if (activeLists.isEmpty()) {
                            current.copy(
                                isLoading = false,
                                lists = emptyList(),
                                archivedLists = archivedLists,
                                selectedListId = resolvedListId ?: selectedListId.value,
                                currentUserId = settings.currentUserId,
                                currentUserRole = members.firstOrNull {
                                    it.id == settings.currentUserId
                                }?.role ?: MemberRole.PENDING,
                                members = members,
                                items = emptyList(),
                                categories = emptyList(),
                                history = emptyList(),
                                historyHasMore = false,
                                quickSuggestions = if (current.quickSuggestions.isEmpty()) {
                                    DEFAULT_SUGGESTIONS
                                } else {
                                    current.quickSuggestions
                                },
                            )
                        } else {
                            current.copy(
                                isLoading = false,
                                lists = activeLists,
                                archivedLists = archivedLists,
                                selectedListId = resolvedListId ?: selectedListId.value,
                                currentUserId = settings.currentUserId,
                                currentUserRole = members.firstOrNull {
                                    it.id == settings.currentUserId
                                }?.role ?: MemberRole.PENDING,
                                members = members,
                                quickSuggestions = if (current.quickSuggestions.isEmpty()) {
                                    DEFAULT_SUGGESTIONS
                                } else {
                                    current.quickSuggestions
                                },
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.LoadData,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    private fun observeListContent() {
        viewModelScope.launch {
            combine(
                selectedListId.filterNotNull(),
                historyLimit
            ) { listId, limit -> listId to limit }
                .flatMapLatest { (listId, limit) ->
                    combine(
                        shoppingRepository.observeItems(listId),
                        shoppingRepository.observeCategories(listId),
                        shoppingRepository.observeActivity(listId, limit + 1)
                    ) { items, categories, history ->
                        ShoppingListContentSnapshot(listId, limit, items, categories, history)
                    }
                }
                .collect { snapshot ->
                    val (listId, limit, items, categories, history) = snapshot
                    val hasMore = history.size > limit
                    val trimmedHistory = if (hasMore) history.take(limit) else history
                    _state.update { current ->
                        val adjustedFilter = when (val filter = current.activeFilter) {
                            is ShoppingFilter.Category ->
                                if (categories.any { it.id == filter.categoryId }) filter else ShoppingFilter.All
                            else -> filter
                        }
                        current.copy(
                            selectedListId = listId,
                            items = items,
                            categories = categories,
                            activeFilter = adjustedFilter,
                            history = trimmedHistory,
                            historyHasMore = hasMore
                        )
                    }
                    refreshNameSuggestions()
                    maybeNotify(trimmedHistory)
                }
        }
    }

    private fun resolveSelectedListId(preferred: String, lists: List<ShoppingList>): String? {
        if (lists.isEmpty()) return null
        val currentSelected = selectedListId.value
        return when {
            preferred.isNotBlank() && lists.any { it.id == preferred } -> preferred
            currentSelected != null && lists.any { it.id == currentSelected } -> currentSelected
            else -> lists.minByOrNull { it.createdAt }?.id ?: lists.first().id
        }
    }

    private fun applySelectedList(listId: String) {
        historyLimit.value = HISTORY_PAGE_SIZE
        selectedListId.value = listId
        lastNotifiedActivityId = null
    }

    fun selectList(listId: String) {
        if (_state.value.selectedListId == listId) return
        applySelectedList(listId)
        viewModelScope.launch { appSettingsDataStore.setActiveShoppingList(listId) }
        _state.update { it.copy(activeFilter = ShoppingFilter.All, searchQuery = "") }
    }

    fun addList(title: String, colorHex: String, iconName: String, templateKey: String?, participantIds: List<String>) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                val list = ShoppingList(
                    id = idGenerator.newId(),
                    title = title.trim(),
                    colorHex = colorHex,
                    iconName = iconName,
                    createdAt = TimeUtils.now(),
                    templateKey = templateKey,
                    participantIds = participantIds
                )
                shoppingRepository.upsertList(list)
                selectList(list.id)
                recordActivity(ShoppingActivityType.LIST_CREATED, list)
                templateKey?.let { key ->
                    applyTemplate(list, key)
                }
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_list_created_success, list.title))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingListCreate,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun renameList(list: ShoppingList, newTitle: String) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                shoppingRepository.upsertList(list.copy(title = newTitle.trim()))
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_list_renamed_success, newTitle.trim()))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingListUpdate,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun deleteList(list: ShoppingList) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            val currentLists = _state.value.lists
            if (currentLists.size <= 1) {
                _state.update {
                    it.copy(errorMessage = context.getString(R.string.shopping_list_delete_forbidden))
                }
                return@launch
            }
            try {
                shoppingRepository.deleteList(list)
                if (selectedListId.value == list.id) {
                    val fallback = currentLists.firstOrNull { it.id != list.id }?.id
                        ?: ShoppingRepository.DEFAULT_LIST_ID
                    selectList(fallback)
                }
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_list_deleted_success, list.title))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingListDelete,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun duplicateList(source: ShoppingList, title: String) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                val newList = source.copy(
                    id = idGenerator.newId(),
                    title = title.trim(),
                    createdAt = TimeUtils.now(),
                    isArchived = false,
                    archivedAt = null
                )
                shoppingRepository.duplicateList(source, newList)
                selectList(newList.id)
                recordActivity(
                    ShoppingActivityType.LIST_DUPLICATED,
                    newList,
                    extraPayload = mapOf(PAYLOAD_SOURCE_TITLE to source.title)
                )
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_list_duplicated_success, newList.title))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingListUpdate,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun archiveList(list: ShoppingList) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                val timestamp = TimeUtils.now()
                shoppingRepository.archiveList(list.id, timestamp)
                recordActivity(
                    ShoppingActivityType.LIST_ARCHIVED,
                    list.copy(isArchived = true, archivedAt = timestamp)
                )
                if (selectedListId.value == list.id) {
                    val fallback = _state.value.lists.firstOrNull { it.id != list.id }?.id
                    fallback?.let { selectList(it) }
                }
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_list_archived_success, list.title))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingListUpdate,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun restoreList(list: ShoppingList) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                shoppingRepository.restoreList(list.id)
                recordActivity(ShoppingActivityType.LIST_RESTORED, list.copy(isArchived = false, archivedAt = null))
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_list_restored_success, list.title))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingListUpdate,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun updateListParticipants(list: ShoppingList, participantIds: List<String>) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                val distinctIds = participantIds.distinct()
                shoppingRepository.updateParticipants(list.id, distinctIds)
                val added = distinctIds.toSet() - list.participantIds.toSet()
                val removed = list.participantIds.toSet() - distinctIds.toSet()
                val updatedList = list.copy(participantIds = distinctIds)
                added.forEach { memberId ->
                    recordActivity(
                        ShoppingActivityType.MEMBER_ADDED,
                        updatedList,
                        extraPayload = mapOf(PAYLOAD_MEMBER_NAME to resolveMemberName(memberId))
                    )
                }
                removed.forEach { memberId ->
                    recordActivity(
                        ShoppingActivityType.MEMBER_REMOVED,
                        updatedList,
                        extraPayload = mapOf(PAYLOAD_MEMBER_NAME to resolveMemberName(memberId))
                    )
                }
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_list_members_updated))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingListUpdate,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun addCategory(title: String, colorHex: String, iconName: String) {
        if (!_state.value.canMutateFamily) return
        val listId = selectedListId.value ?: return
        viewModelScope.launch {
            try {
                val category = ShoppingCategory(
                    id = idGenerator.newId(),
                    listId = listId,
                    title = title.trim(),
                    colorHex = colorHex,
                    iconName = iconName,
                    sortOrder = (_state.value.categories.maxOfOrNull { it.sortOrder } ?: 0) + 1
                )
                shoppingRepository.upsertCategory(category)
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_category_created_success, category.title))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingCategoryCreate,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun updateCategory(category: ShoppingCategory, title: String, colorHex: String, iconName: String) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                shoppingRepository.upsertCategory(
                    category.copy(
                        title = title.trim(),
                        colorHex = colorHex,
                        iconName = iconName
                    )
                )
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_category_updated_success, title.trim()))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingCategoryUpdate,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun deleteCategory(category: ShoppingCategory) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                shoppingRepository.deleteCategory(category)
                _state.update {
                    val resetFilter = if (it.activeFilter is ShoppingFilter.Category && it.activeFilter.categoryId == category.id) {
                        ShoppingFilter.All
                    } else {
                        it.activeFilter
                    }
                    it.copy(
                        activeFilter = resetFilter,
                        successMessage = context.getString(R.string.shopping_category_deleted_success, category.title)
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingCategoryDelete,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun addItem(
        title: String,
        categoryId: String?,
        quantity: Double?,
        unit: ShoppingItemUnit?,
        note: String?
    ) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                val currentUserId = _state.value.currentUserId
                val listId = selectedListId.value
                if (currentUserId.isBlank()) {
                    _state.update {
                        it.copy(errorMessage = context.getString(R.string.error_no_current_user))
                    }
                    return@launch
                }
                if (listId.isNullOrBlank()) {
                    _state.update {
                        it.copy(errorMessage = context.getString(R.string.error_shopping_list_missing))
                    }
                    return@launch
                }

                val newItem = ShoppingItem(
                    id = idGenerator.newId(),
                    listId = listId,
                    title = title.trim(),
                    createdByUserId = currentUserId,
                    isBought = false,
                    createdAt = TimeUtils.now(),
                    categoryId = categoryId,
                    quantity = quantity,
                    quantityUnit = unit,
                    note = note
                )
                shoppingRepository.upsertItem(newItem)
                state.value.selectedList?.let { list ->
                    recordActivity(ShoppingActivityType.ITEM_ADDED, list, newItem)
                }
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_item_added_success))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingAdd,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun toggleItemStatus(item: ShoppingItem) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                val toggled = item.copy(
                    isBought = !item.isBought,
                    boughtAt = if (!item.isBought) TimeUtils.now() else null
                )
                shoppingRepository.upsertItem(toggled)
                findListById(item.listId)?.let { list ->
                    val type = if (toggled.isBought) {
                        ShoppingActivityType.ITEM_COMPLETED
                    } else {
                        ShoppingActivityType.ITEM_REOPENED
                    }
                    recordActivity(type, list, toggled)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingUpdate,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun resetSearchAndFilter() {
        _state.update { ShoppingSearchFilterReducer.apply(it) }
    }

    fun applyFilter(filter: ShoppingFilter) {
        _state.update { it.copy(activeFilter = filter) }
    }

    fun deleteItem(item: ShoppingItem) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                shoppingRepository.deleteItem(item)
                findListById(item.listId)?.let { list ->
                    recordActivity(ShoppingActivityType.ITEM_DELETED, list, item)
                }
                _state.update {
                    it.copy(successMessage = context.getString(R.string.shopping_item_deleted_success))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.ShoppingDelete,
                            tag = "ShoppingViewModel",
                        )
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _state.update { it.copy(successMessage = null) }
    }

    fun loadMoreHistory() {
        historyLimit.update { it + HISTORY_PAGE_SIZE }
    }

    fun applySuggestion(suggestion: String) {
        val trimmed = suggestion.trim()
        if (trimmed.isEmpty()) return
        addItem(trimmed, categoryId = null, quantity = null, unit = null, note = null)
    }

    private fun refreshNameSuggestions() {
        viewModelScope.launch {
            val suggestions = runCatching { shoppingRepository.loadTopItemTitles(TITLE_SUGGESTION_LIMIT) }
                .getOrDefault(emptyList())
            _state.update { it.copy(nameSuggestions = suggestions) }
        }
    }

    private fun maybeNotify(history: List<ShoppingActivity>) {
        if (history.isEmpty()) return
        val latest = history.first()
        if (latest.id == lastNotifiedActivityId) return
        lastNotifiedActivityId = latest.id
        if (latest.actorId == _state.value.currentUserId) return
        val (title, message) = formatNotification(latest) ?: return
        when (shoppingNotificationManager.showActivityNotification(title, message)) {
            ShoppingNotificationResult.SHOWN -> Unit
            ShoppingNotificationResult.PERMISSION_REQUIRED -> {
                if (_state.value.errorMessage == null) {
                    _state.update {
                        it.copy(errorMessage = context.getString(R.string.shopping_notifications_permission_required))
                    }
                }
            }
            ShoppingNotificationResult.NOTIFICATIONS_DISABLED -> {
                if (_state.value.errorMessage == null) {
                    _state.update {
                        it.copy(errorMessage = context.getString(R.string.shopping_notifications_disabled))
                    }
                }
            }
        }
    }

    private fun formatNotification(activity: ShoppingActivity): Pair<String, String>? {
        val listTitle = activity.payload[PAYLOAD_LIST_TITLE]
            ?: findListById(activity.listId)?.title
            ?: return null
        val actorName = activity.payload[PAYLOAD_ACTOR_NAME] ?: resolveMemberName(activity.actorId)
        val notificationTitle = context.getString(R.string.shopping_notification_title, listTitle)
        val message = when (activity.actionType) {
            ShoppingActivityType.ITEM_ADDED -> {
                val itemTitle = activity.payload[PAYLOAD_ITEM_TITLE] ?: return null
                context.getString(R.string.shopping_notification_item_added, actorName, itemTitle)
            }
            ShoppingActivityType.ITEM_COMPLETED -> {
                val itemTitle = activity.payload[PAYLOAD_ITEM_TITLE] ?: return null
                context.getString(R.string.shopping_notification_item_completed, actorName, itemTitle)
            }
            ShoppingActivityType.ITEM_REOPENED -> {
                val itemTitle = activity.payload[PAYLOAD_ITEM_TITLE] ?: return null
                context.getString(R.string.shopping_notification_item_reopened, actorName, itemTitle)
            }
            ShoppingActivityType.ITEM_DELETED -> {
                val itemTitle = activity.payload[PAYLOAD_ITEM_TITLE] ?: return null
                context.getString(R.string.shopping_notification_item_deleted, actorName, itemTitle)
            }
            ShoppingActivityType.LIST_CREATED ->
                context.getString(R.string.shopping_notification_list_created, actorName, listTitle)
            ShoppingActivityType.LIST_ARCHIVED ->
                context.getString(R.string.shopping_notification_list_archived, actorName, listTitle)
            ShoppingActivityType.LIST_RESTORED ->
                context.getString(R.string.shopping_notification_list_restored, actorName, listTitle)
            ShoppingActivityType.LIST_DUPLICATED -> {
                val sourceTitle = activity.payload[PAYLOAD_SOURCE_TITLE] ?: return null
                context.getString(R.string.shopping_notification_list_duplicated, actorName, sourceTitle)
            }
            ShoppingActivityType.LIST_TEMPLATE_APPLIED -> {
                val templateKey = activity.payload[PAYLOAD_TEMPLATE_KEY] ?: return null
                val templateTitle = templateDefinitions.firstOrNull { it.key == templateKey }?.let {
                    context.getString(it.titleRes)
                } ?: templateKey
                context.getString(R.string.shopping_notification_template_applied, actorName, templateTitle)
            }
            ShoppingActivityType.MEMBER_ADDED -> {
                val memberName = activity.payload[PAYLOAD_MEMBER_NAME] ?: return null
                context.getString(R.string.shopping_notification_member_added, actorName, memberName)
            }
            ShoppingActivityType.MEMBER_REMOVED -> {
                val memberName = activity.payload[PAYLOAD_MEMBER_NAME] ?: return null
                context.getString(R.string.shopping_notification_member_removed, actorName, memberName)
            }
        }
        return notificationTitle to message
    }

    private fun resolveMemberName(memberId: String): String {
        return _state.value.members.firstOrNull { it.id == memberId }?.displayLabel()
            ?: context.getString(R.string.shopping_member_unknown)
    }

    private fun findListById(listId: String): ShoppingList? {
        val current = _state.value
        return current.lists.firstOrNull { it.id == listId }
            ?: current.archivedLists.firstOrNull { it.id == listId }
    }

    private suspend fun recordActivity(
        type: ShoppingActivityType,
        list: ShoppingList,
        item: ShoppingItem? = null,
        extraPayload: Map<String, String> = emptyMap()
    ) {
        val actorId = _state.value.currentUserId
        if (actorId.isBlank()) return
        val payload = buildMap {
            put(PAYLOAD_LIST_TITLE, list.title)
            put(PAYLOAD_ACTOR_NAME, resolveMemberName(actorId))
            item?.let { put(PAYLOAD_ITEM_TITLE, it.title) }
            putAll(extraPayload)
        }
        val activity = ShoppingActivity(
            id = idGenerator.newId(),
            listId = list.id,
            itemId = item?.id,
            actorId = actorId,
            actionType = type,
            payload = payload,
            occurredAt = TimeUtils.now()
        )
        shoppingRepository.logActivity(activity)
    }

    private suspend fun applyTemplate(list: ShoppingList, templateKey: String) {
        val template = templateDefinitions.firstOrNull { it.key == templateKey } ?: return
        val actorId = _state.value.currentUserId
        if (actorId.isBlank()) return
        val categories = template.categories.mapIndexed { index, definition ->
            val category = ShoppingCategory(
                id = idGenerator.newId(),
                listId = list.id,
                title = context.getString(definition.titleRes),
                colorHex = definition.colorHex,
                iconName = definition.iconName,
                sortOrder = index
            )
            shoppingRepository.upsertCategory(category)
            category
        }
        template.items.forEach { itemDefinition ->
            val title = context.getString(itemDefinition.titleRes)
            val categoryId = itemDefinition.categoryIndex?.let { index -> categories.getOrNull(index)?.id }
            val item = ShoppingItem(
                id = idGenerator.newId(),
                listId = list.id,
                title = title,
                createdByUserId = actorId,
                isBought = false,
                createdAt = TimeUtils.now(),
                categoryId = categoryId
            )
            shoppingRepository.upsertItem(item)
        }
        recordActivity(
            ShoppingActivityType.LIST_TEMPLATE_APPLIED,
            list,
            extraPayload = mapOf(PAYLOAD_TEMPLATE_KEY to template.key)
        )
    }

    private fun buildTemplateUi(): List<ShoppingTemplateUi> {
        return templateDefinitions.map {
            ShoppingTemplateUi(
                key = it.key,
                title = context.getString(it.titleRes),
                description = context.getString(it.descriptionRes)
            )
        }
    }

    private fun createTemplateDefinitions(): List<ShoppingTemplateDefinition> {
        return listOf(
            ShoppingTemplateDefinition(
                key = "weekly",
                titleRes = R.string.shopping_template_weekly_title,
                descriptionRes = R.string.shopping_template_weekly_description,
                categories = listOf(
                    ShoppingTemplateCategoryDefinition(R.string.shopping_template_category_fresh, "#4CAF50", "fruits"),
                    ShoppingTemplateCategoryDefinition(R.string.shopping_template_category_dairy, "#FFB300", "dairy"),
                    ShoppingTemplateCategoryDefinition(R.string.shopping_template_category_household, "#607D8B", "household")
                ),
                items = listOf(
                    ShoppingTemplateItemDefinition(R.string.shopping_template_item_bread, 0),
                    ShoppingTemplateItemDefinition(R.string.shopping_template_item_milk, 1),
                    ShoppingTemplateItemDefinition(R.string.shopping_template_item_pasta, 0),
                    ShoppingTemplateItemDefinition(R.string.shopping_template_item_cleaner, 2)
                )
            ),
            ShoppingTemplateDefinition(
                key = "party",
                titleRes = R.string.shopping_template_party_title,
                descriptionRes = R.string.shopping_template_party_description,
                categories = listOf(
                    ShoppingTemplateCategoryDefinition(R.string.shopping_template_category_snacks, "#F06292", "snacks"),
                    ShoppingTemplateCategoryDefinition(R.string.shopping_template_category_drinks, "#4DD0E1", "beverages"),
                    ShoppingTemplateCategoryDefinition(R.string.shopping_template_category_misc, "#A1887F", "party")
                ),
                items = listOf(
                    ShoppingTemplateItemDefinition(R.string.shopping_template_item_chips, 0),
                    ShoppingTemplateItemDefinition(R.string.shopping_template_item_juice, 1),
                    ShoppingTemplateItemDefinition(R.string.shopping_template_item_decor, 2),
                    ShoppingTemplateItemDefinition(R.string.shopping_template_item_dessert, 0)
                )
            )
        )
    }

    companion object {
        private const val HISTORY_PAGE_SIZE = 50
        private const val TITLE_SUGGESTION_LIMIT = 30
        internal const val PAYLOAD_LIST_TITLE = "listTitle"
        internal const val PAYLOAD_ACTOR_NAME = "actorName"
        internal const val PAYLOAD_ITEM_TITLE = "itemTitle"
        internal const val PAYLOAD_TEMPLATE_KEY = "templateKey"
        internal const val PAYLOAD_MEMBER_NAME = "memberName"
        internal const val PAYLOAD_SOURCE_TITLE = "sourceTitle"
        private val DEFAULT_SUGGESTIONS = listOf(
            "Хлеб",
            "Молоко",
            "Яйца",
            "Яблоки",
            "Сыр",
            "Помидоры"
        )
    }
}

private data class ShoppingListContentSnapshot(
    val listId: String,
    val limit: Int,
    val items: List<ShoppingItem>,
    val categories: List<ShoppingCategory>,
    val history: List<ShoppingActivity>
)

private data class ShoppingTemplateDefinition(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val categories: List<ShoppingTemplateCategoryDefinition>,
    val items: List<ShoppingTemplateItemDefinition>
)

private data class ShoppingTemplateCategoryDefinition(
    @StringRes val titleRes: Int,
    val colorHex: String,
    val iconName: String
)

private data class ShoppingTemplateItemDefinition(
    @StringRes val titleRes: Int,
    val categoryIndex: Int?
)

data class ShoppingTemplateUi(
    val key: String,
    val title: String,
    val description: String
)
