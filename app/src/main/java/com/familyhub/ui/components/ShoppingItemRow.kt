package com.familyhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingItem
import com.familyhub.domain.model.ShoppingItemUnit
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.screens.shopping.shoppingUnitDisplayName
import com.familyhub.ui.theme.FhTheme
import java.util.Locale

const val SHOPPING_CATEGORY_STRIPE_TEST_TAG = "shopping_category_stripe"
const val SHOPPING_CREATOR_ATTRIBUTION_TEST_TAG = "shopping_creator_attribution"

@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    category: ShoppingCategory?,
    creator: FamilyMember?,
    showCategoryInMeta: Boolean,
    onStatusChange: (() -> Unit)?,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    val toggleBoughtLabel = stringResource(R.string.shopping_toggle_bought_cd, item.title)
    val categoryStripeColor = category?.colorHex?.let { colorFromHex(it) }
    val quantityLabel = formatQuantity(item.quantity)?.let { formattedQuantity ->
        val unitText = item.quantityUnit
            ?.takeIf { it != ShoppingItemUnit.NONE }
            ?.let { shoppingUnitDisplayName(it) }
        if (unitText != null) "$formattedQuantity $unitText" else formattedQuantity
    }
    val showMetaRow = quantityLabel != null ||
        (showCategoryInMeta && category != null) ||
        creator != null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        shape = RoundedCornerShape(FhTheme.radius.card),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            if (categoryStripeColor != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(categoryStripeColor)
                        .testTag(SHOPPING_CATEGORY_STRIPE_TEST_TAG),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (categoryStripeColor != null) 4.dp else 2.dp,
                        end = 2.dp,
                        top = 2.dp,
                        bottom = 2.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Checkbox(
                checked = item.isBought,
                onCheckedChange = onStatusChange?.let { action ->
                    { action() }
                },
                enabled = onStatusChange != null,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = toggleBoughtLabel },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (item.isBought) TextDecoration.LineThrough else null,
                    ),
                    color = if (item.isBought) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (showMetaRow) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showCategoryInMeta) {
                                category?.let { cat ->
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(colorFromHex(cat.colorHex), CircleShape),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                            }
                            val metaSeparator = stringResource(R.string.task_meta_separator)
                            val metaText = buildList {
                                quantityLabel?.let { add(it) }
                                if (showCategoryInMeta) {
                                    category?.title?.let { add(it) }
                                }
                            }.joinToString(separator = metaSeparator)
                            if (metaText.isNotBlank()) {
                                Text(
                                    text = metaText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        creator?.let { member ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                modifier = Modifier.testTag(SHOPPING_CREATOR_ATTRIBUTION_TEST_TAG),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                FhMemberAvatar(
                                    member = member,
                                    size = 20.dp,
                                )
                                Text(
                                    text = member.displayLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                item.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onDelete != null) {
                Box {
                    IconButton(onClick = { showActions = true }) {
                        Icon(
                            imageVector = FhIcons.MoreVert,
                            contentDescription = stringResource(
                                R.string.shopping_item_more_actions,
                                item.title,
                            ),
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_delete_cd)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = FhIcons.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showActions = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            }
        }
    }
}

private fun formatQuantity(quantity: Double?): String? {
    if (quantity == null) return null
    val formattedQuantity = if (quantity % 1.0 == 0.0) {
        quantity.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", quantity).trimEnd('0').trimEnd('.')
    }
    return formattedQuantity
}

@Composable
private fun colorFromHex(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: IllegalArgumentException) {
    MaterialTheme.colorScheme.outline
}
