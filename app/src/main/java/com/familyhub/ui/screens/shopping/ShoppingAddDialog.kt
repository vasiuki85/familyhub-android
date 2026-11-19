package com.familyhub.ui.screens.shopping

import com.familyhub.ui.components.FhIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.familyhub.R
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingItemUnit
import com.familyhub.ui.theme.FhTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ShoppingAddDialog(
    categories: List<ShoppingCategory>,
    suggestions: List<String>,
    onManageCategories: () -> Unit,
    onDismiss: () -> Unit,
    onAddAnother: (
        title: String,
        categoryId: String?,
        quantity: Double?,
        unit: ShoppingItemUnit?,
        note: String?,
    ) -> Unit,
    onConfirm: (
        title: String,
        categoryId: String?,
        quantity: Double?,
        unit: ShoppingItemUnit?,
        note: String?,
    ) -> Unit,
    initialTitle: String = "",
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var quantityText by remember { mutableStateOf("") }
    var quantityError by remember { mutableStateOf<String?>(null) }
    var unitExpanded by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(ShoppingItemUnit.NONE) }
    var note by remember { mutableStateOf("") }

    val validationItemNameEmpty = stringResource(R.string.validation_item_name_empty)
    val validationItemNameTooLong = stringResource(R.string.validation_item_name_too_long)
    val validationQuantityInvalid = stringResource(R.string.validation_quantity_invalid)
    val titleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    var dropdownWidthPx by remember { mutableStateOf(0) }
    var titleHasFocus by remember { mutableStateOf(false) }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var suppressSuggestions by remember { mutableStateOf(false) }

    fun validateTitle(newTitle: String): String? = when {
        newTitle.isBlank() -> validationItemNameEmpty
        newTitle.length > 100 -> validationItemNameTooLong
        else -> null
    }

    fun validateQuantity(text: String): String? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isBlank() || normalized.lastOrNull() == '.') return null
        return if (normalized.toDoubleOrNull() == null) validationQuantityInvalid else null
    }

    val units = remember { ShoppingItemUnit.entries }
    val filteredSuggestions = remember(title, suggestions) {
        if (title.isBlank()) {
            emptyList()
        } else {
            suggestions.filter { suggestion ->
                suggestion.contains(title, ignoreCase = true) &&
                    !suggestion.equals(title, ignoreCase = true)
            }.take(5)
        }
    }

    LaunchedEffect(Unit) {
        titleFocusRequester.requestFocus()
    }

    LaunchedEffect(titleHasFocus, filteredSuggestions, suppressSuggestions) {
        suggestionsExpanded = titleHasFocus &&
            filteredSuggestions.isNotEmpty() &&
            !suppressSuggestions
    }

    fun submitForm(
        submit: (String, String?, Double?, ShoppingItemUnit?, String?) -> Unit,
        resetAfter: Boolean,
    ) {
        val trimmedTitle = title.trim()
        titleError = validateTitle(trimmedTitle)
        quantityError = validateQuantity(quantityText)
        if (titleError != null || quantityError != null || trimmedTitle.isEmpty()) return

        val normalizedQuantity = quantityText.replace(',', '.').trim().toDoubleOrNull()
        val unit = selectedUnit.takeIf { it != ShoppingItemUnit.NONE }
        val noteValue = note.trim().takeIf { it.isNotEmpty() }
        submit(trimmedTitle, selectedCategoryId, normalizedQuantity, unit, noteValue)

        if (resetAfter) {
            title = ""
            quantityText = ""
            selectedUnit = ShoppingItemUnit.NONE
            note = ""
            titleError = null
            quantityError = null
            suppressSuggestions = true
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val canSubmit = title.isNotBlank() && titleError == null && quantityError == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = FhTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.shopping_add_item),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                )

                Box {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            titleError = validateTitle(it)
                            suppressSuggestions = false
                        },
                        label = { Text(stringResource(R.string.shopping_item_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester)
                            .onGloballyPositioned { dropdownWidthPx = it.size.width }
                            .onFocusChanged {
                                titleHasFocus = it.isFocused
                                if (!it.isFocused) {
                                    suggestionsExpanded = false
                                }
                            },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                imageVector = FhIcons.ShoppingCart,
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text,
                        ),
                        isError = titleError != null,
                        supportingText = titleError?.let { message -> { Text(message) } },
                    )
                    val menuWidth = with(density) {
                        dropdownWidthPx.toDp().coerceAtLeast(220.dp)
                    }
                    DropdownMenu(
                        expanded = suggestionsExpanded,
                        onDismissRequest = {
                            suggestionsExpanded = false
                            suppressSuggestions = true
                        },
                        modifier = Modifier.width(menuWidth),
                        properties = PopupProperties(focusable = false),
                    ) {
                        filteredSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = suggestion,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    title = suggestion
                                    titleError = null
                                    suggestionsExpanded = false
                                    suppressSuggestions = true
                                    titleFocusRequester.requestFocus()
                                }
                            )
                        }
                    }
                }

                CompactShoppingPicker(
                    label = stringResource(R.string.shopping_add_category_label),
                    value = categories.firstOrNull { it.id == selectedCategoryId }?.title
                        ?: stringResource(R.string.shopping_category_none_option),
                    icon = FhIcons.Label,
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_category_none_option)) },
                        onClick = {
                            selectedCategoryId = null
                            categoryExpanded = false
                        },
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.title) },
                            onClick = {
                                selectedCategoryId = category.id
                                categoryExpanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_add_category_manage)) },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                imageVector = FhIcons.Edit,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            categoryExpanded = false
                            onManageCategories()
                        },
                    )
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val quantityField: @Composable (Modifier) -> Unit = { fieldModifier ->
                        ShoppingQuantityField(
                            value = quantityText,
                            error = quantityError,
                            onValueChange = { raw ->
                                quantityText = raw.filter { character ->
                                    character.isDigit() || character == ',' || character == '.'
                                }
                                quantityError = validateQuantity(quantityText)
                            },
                            modifier = fieldModifier,
                        )
                    }
                    val unitField: @Composable (Modifier) -> Unit = { fieldModifier ->
                        CompactShoppingPicker(
                            label = stringResource(R.string.shopping_add_unit),
                            value = shoppingUnitDisplayName(selectedUnit),
                            icon = FhIcons.Label,
                            expanded = unitExpanded,
                            onExpandedChange = { unitExpanded = it },
                            modifier = fieldModifier,
                        ) {
                            units.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(shoppingUnitDisplayName(unit)) },
                                    onClick = {
                                        selectedUnit = unit
                                        unitExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    if (ShoppingAddDialogLayoutPolicy.stackQuantityAndUnit(maxWidth)) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                        ) {
                            quantityField(Modifier.fillMaxWidth())
                            unitField(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                            verticalAlignment = Alignment.Top,
                        ) {
                            quantityField(Modifier.weight(0.48f))
                            unitField(Modifier.weight(0.52f))
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.shopping_add_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        androidx.compose.material3.Icon(
                            imageVector = FhIcons.Edit,
                            contentDescription = null,
                        )
                    },
                    minLines = 1,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                )
            }

            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FhTheme.spacing.lg)
                        .padding(top = FhTheme.spacing.md, bottom = FhTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs),
                ) {
                    Button(
                        onClick = { submitForm(onConfirm, resetAfter = false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.shopping_save))
                    }
                    OutlinedButton(
                        onClick = { submitForm(onAddAnother, resetAfter = true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.shopping_save_and_add_more),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.shopping_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingQuantityField(
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.shopping_add_quantity)) },
        modifier = modifier,
        leadingIcon = {
            androidx.compose.material3.Icon(
                imageVector = FhIcons.Add,
                contentDescription = null,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
    )
}

internal object ShoppingAddDialogLayoutPolicy {
    private val CompactWidth = 360.dp

    fun stackQuantityAndUnit(availableWidth: androidx.compose.ui.unit.Dp): Boolean =
        availableWidth < CompactWidth
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactShoppingPicker(
    label: String,
    value: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = androidx.compose.material3.MaterialTheme.shapes.medium,
            border = BorderStroke(
                width = 1.dp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
            ),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = FhTheme.spacing.md,
                    vertical = FhTheme.spacing.sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            content = content,
        )
    }
}
