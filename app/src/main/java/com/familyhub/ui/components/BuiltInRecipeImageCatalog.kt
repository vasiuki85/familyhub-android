package com.familyhub.ui.components

import androidx.annotation.DrawableRes
import com.familyhub.R
import com.familyhub.domain.utils.BuiltInRecipeImage

object BuiltInRecipeImageCatalog {
    private val drawables = listOf(
        R.drawable.fh_recipe_01,
        R.drawable.fh_recipe_02,
        R.drawable.fh_recipe_03,
        R.drawable.fh_recipe_04,
        R.drawable.fh_recipe_05,
        R.drawable.fh_recipe_06,
        R.drawable.fh_recipe_07,
        R.drawable.fh_recipe_08,
        R.drawable.fh_recipe_09,
        R.drawable.fh_recipe_10,
        R.drawable.fh_recipe_11,
        R.drawable.fh_recipe_12,
        R.drawable.fh_recipe_13,
        R.drawable.fh_recipe_14,
        R.drawable.fh_recipe_15,
        R.drawable.fh_recipe_16,
        R.drawable.fh_recipe_17,
        R.drawable.fh_recipe_18,
        R.drawable.fh_recipe_19,
        R.drawable.fh_recipe_20,
        R.drawable.fh_recipe_21,
        R.drawable.fh_recipe_22,
        R.drawable.fh_recipe_23,
        R.drawable.fh_recipe_24,
        R.drawable.fh_recipe_25,
        R.drawable.fh_recipe_26,
        R.drawable.fh_recipe_27,
        R.drawable.fh_recipe_28,
        R.drawable.fh_recipe_29,
        R.drawable.fh_recipe_30,
    )

    private val drawableByToken = BuiltInRecipeImage.tokens.zip(drawables).toMap()

    @DrawableRes
    fun drawableFor(token: String?): Int? = drawableByToken[token]
}
