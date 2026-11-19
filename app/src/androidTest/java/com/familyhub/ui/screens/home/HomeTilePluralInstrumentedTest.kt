package com.familyhub.ui.screens.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HomeTilePluralInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun russianShoppingPlurals_useCorrectForms() {
        val localized = localizedContext(Locale("ru"))

        assertEquals(
            "Осталось купить 1 товар",
            localized.resources.getQuantityString(R.plurals.home_redesign_shopping_remaining, 1, 1),
        )
        assertEquals(
            "Осталось купить 2 товара",
            localized.resources.getQuantityString(R.plurals.home_redesign_shopping_remaining, 2, 2),
        )
        assertEquals(
            "Осталось купить 5 товаров",
            localized.resources.getQuantityString(R.plurals.home_redesign_shopping_remaining, 5, 5),
        )
        assertEquals(
            "Осталось купить 21 товар",
            localized.resources.getQuantityString(R.plurals.home_redesign_shopping_remaining, 21, 21),
        )
    }

    @Test
    fun russianFoodPlurals_useCorrectForms() {
        val localized = localizedContext(Locale("ru"))

        assertEquals(
            "Сегодня запланировано 1 блюдо",
            localized.resources.getQuantityString(R.plurals.home_redesign_food_meals_today, 1, 1),
        )
        assertEquals(
            "Сегодня запланировано 2 блюда",
            localized.resources.getQuantityString(R.plurals.home_redesign_food_meals_today, 2, 2),
        )
        assertEquals(
            "Сегодня запланировано 5 блюд",
            localized.resources.getQuantityString(R.plurals.home_redesign_food_meals_today, 5, 5),
        )
        assertEquals(
            "Сегодня запланировано 21 блюдо",
            localized.resources.getQuantityString(R.plurals.home_redesign_food_meals_today, 21, 21),
        )
    }

    private fun localizedContext(locale: Locale): Context {
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
