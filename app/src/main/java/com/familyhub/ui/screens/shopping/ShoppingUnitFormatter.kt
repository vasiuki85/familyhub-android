package com.familyhub.ui.screens.shopping

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.familyhub.R
import com.familyhub.domain.model.ShoppingItemUnit

@Composable
fun shoppingUnitDisplayName(unit: ShoppingItemUnit): String = when (unit) {
    ShoppingItemUnit.NONE -> stringResource(R.string.shopping_unit_none)
    ShoppingItemUnit.PIECES -> stringResource(R.string.shopping_unit_pieces)
    ShoppingItemUnit.PACK -> stringResource(R.string.shopping_unit_pack)
    ShoppingItemUnit.KILOGRAM -> stringResource(R.string.shopping_unit_kilogram)
    ShoppingItemUnit.GRAM -> stringResource(R.string.shopping_unit_gram)
    ShoppingItemUnit.LITER -> stringResource(R.string.shopping_unit_liter)
    ShoppingItemUnit.MILLILITER -> stringResource(R.string.shopping_unit_milliliter)
    ShoppingItemUnit.BOTTLE -> stringResource(R.string.shopping_unit_bottle)
    ShoppingItemUnit.BOX -> stringResource(R.string.shopping_unit_box)
}
