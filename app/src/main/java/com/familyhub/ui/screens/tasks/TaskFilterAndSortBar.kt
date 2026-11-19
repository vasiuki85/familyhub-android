package com.familyhub.ui.screens.tasks

import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhScrollableHorizontalRow
import com.familyhub.ui.components.FhSearchField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.TaskCategory
import com.familyhub.ui.theme.FhTheme

const val TASK_CATEGORY_COLOR_DOT_TEST_TAG = "task_category_color_dot"

private enum class TaskFilterSheet {
    FILTER,
    SORT,
    CATEGORY,
    SEARCH,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFilterCompactBar(
    filterType: TaskFilterType,
    sortType: TaskSortType,
    categories: List<TaskCategory>,
    selectedCategoryId: String?,
    searchQuery: String,
    showSearch: Boolean,
    onFilterChange: (TaskFilterType) -> Unit,
    onSortChange: (TaskSortType) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visibleSheet by remember { mutableStateOf<TaskFilterSheet?>(null) }
    val taskColors = FhTheme.modules.task
    val selectedCategoryName = selectedCategoryId?.let { id ->
        categories.firstOrNull { it.id == id }?.name
    }
    val selectedCategoryColor = selectedCategoryId?.let { id ->
        categories.firstOrNull { it.id == id }?.color
    }
    val chipSelectedFlags = listOf(
        filterType != TaskFilterType.ALL,
        sortType != TaskSortType.DATE_NEWEST,
        selectedCategoryId != null,
    )
    val selectedChipIndex = chipSelectedFlags.indexOfFirst { it }.takeIf { it >= 0 }

    FhScrollableHorizontalRow(
        selectedIndex = selectedChipIndex,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = filterType != TaskFilterType.ALL,
                onClick = { visibleSheet = TaskFilterSheet.FILTER },
                label = {
                    Text(
                        text = filterType.label(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = filterChipColors(taskColors),
            )
        }
        item {
            FilterChip(
                selected = sortType != TaskSortType.DATE_NEWEST,
                onClick = { visibleSheet = TaskFilterSheet.SORT },
                label = {
                    Text(
                        text = sortType.label(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = filterChipColors(taskColors),
            )
        }
        item {
            FilterChip(
                selected = selectedCategoryId != null,
                onClick = { visibleSheet = TaskFilterSheet.CATEGORY },
                label = {
                    Text(
                        text = selectedCategoryName
                            ?: stringResource(R.string.task_category_filter_all),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = selectedCategoryColor?.let { color ->
                    {
                        TaskCategoryColorDot(
                            color = color,
                            modifier = Modifier.testTag(TASK_CATEGORY_COLOR_DOT_TEST_TAG),
                        )
                    }
                },
                colors = filterChipColors(taskColors),
            )
        }
        if (showSearch) {
            item {
                IconButton(
                    onClick = { visibleSheet = TaskFilterSheet.SEARCH },
                    modifier = Modifier.padding(horizontal = 0.dp),
                ) {
                    Icon(
                        imageVector = FhIcons.FilterList,
                        contentDescription = stringResource(R.string.tasks_search_filter_cd),
                        tint = if (searchQuery.isNotBlank()) {
                            taskColors.accent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }

    when (visibleSheet) {
        TaskFilterSheet.FILTER -> {
            ModalBottomSheet(onDismissRequest = { visibleSheet = null }) {
                TaskFilterSheetContent(
                    title = stringResource(R.string.tasks_v2_filter_sheet_title),
                    options = TaskFilterType.entries,
                    selected = filterType,
                    label = { it.label() },
                    onSelect = {
                        onFilterChange(it)
                        visibleSheet = null
                    },
                )
            }
        }
        TaskFilterSheet.SORT -> {
            ModalBottomSheet(onDismissRequest = { visibleSheet = null }) {
                TaskFilterSheetContent(
                    title = stringResource(R.string.tasks_v2_sort_sheet_title),
                    options = TaskSortType.entries,
                    selected = sortType,
                    label = { it.label() },
                    onSelect = {
                        onSortChange(it)
                        visibleSheet = null
                    },
                )
            }
        }
        TaskFilterSheet.CATEGORY -> {
            ModalBottomSheet(onDismissRequest = { visibleSheet = null }) {
                TaskCategorySheetContent(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onSelect = {
                        onCategorySelected(it)
                        visibleSheet = null
                    },
                )
            }
        }
        TaskFilterSheet.SEARCH -> {
            ModalBottomSheet(onDismissRequest = { visibleSheet = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tasks_search_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    FhSearchField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = stringResource(R.string.tasks_search_hint),
                        searchContentDescription = stringResource(R.string.tasks_search_hint),
                        clearContentDescription = stringResource(R.string.tasks_search_clear_cd),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        null -> Unit
    }
}

@Composable
private fun TaskCategorySheetContent(
    categories: List<TaskCategory>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.tasks_category_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.task_category_filter_all)) },
            leadingContent = {
                RadioButton(
                    selected = selectedCategoryId == null,
                    onClick = null,
                )
            },
            modifier = Modifier.selectable(
                selected = selectedCategoryId == null,
                role = Role.RadioButton,
                onClick = { onSelect(null) },
            ),
        )
        categories.forEach { category ->
            ListItem(
                headlineContent = { Text(category.name) },
                leadingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TaskCategoryColorDot(color = category.color)
                        RadioButton(
                            selected = selectedCategoryId == category.id,
                            onClick = null,
                        )
                    }
                },
                modifier = Modifier.selectable(
                    selected = selectedCategoryId == category.id,
                    role = Role.RadioButton,
                    onClick = { onSelect(category.id) },
                ),
            )
        }
    }
}

@Composable
private fun TaskCategoryColorDot(
    color: Long,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

@Composable
private fun filterChipColors(taskColors: com.familyhub.ui.theme.FhModulePalette) =
    FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        selectedContainerColor = taskColors.container,
        selectedLabelColor = taskColors.onContainer,
        labelColor = MaterialTheme.colorScheme.onSurface,
    )

@Composable
private fun <T> TaskFilterSheetContent(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        options.forEach { option ->
            ListItem(
                headlineContent = { Text(label(option)) },
                leadingContent = {
                    RadioButton(
                        selected = option == selected,
                        onClick = null,
                    )
                },
                modifier = Modifier.selectable(
                    selected = option == selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(option) },
                ),
            )
        }
    }
}

@Composable
private fun TaskFilterType.label(): String = when (this) {
    TaskFilterType.ALL -> stringResource(R.string.tasks_filter_all)
    TaskFilterType.TODO -> stringResource(R.string.tasks_filter_todo)
    TaskFilterType.DONE -> stringResource(R.string.tasks_filter_done)
}

@Composable
private fun TaskSortType.label(): String = when (this) {
    TaskSortType.DATE_NEWEST -> stringResource(R.string.tasks_sort_date_newest)
    TaskSortType.DATE_OLDEST -> stringResource(R.string.tasks_sort_date_oldest)
    TaskSortType.STATUS -> stringResource(R.string.tasks_sort_status)
    TaskSortType.ASSIGNEE -> stringResource(R.string.tasks_sort_assignee)
    TaskSortType.PRIORITY -> stringResource(R.string.tasks_sort_priority)
    TaskSortType.MANUAL -> stringResource(R.string.tasks_sort_manual)
}
