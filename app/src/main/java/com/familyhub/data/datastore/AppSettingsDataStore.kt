package com.familyhub.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettingsDataStore(private val context: Context) {
    companion object {
        val KEY_CURRENT_USER = stringPreferencesKey("current_user_id")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_CUSTOM_JSON = stringPreferencesKey("theme_custom_json")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_ACTIVE_SHOPPING_LIST = stringPreferencesKey("active_shopping_list")
        val KEY_BOTTOM_NAV_ROUTES = stringPreferencesKey("bottom_nav_routes")
        val KEY_FAMILY_ONBOARDING_DISMISSED = booleanPreferencesKey("family_onboarding_dismissed")
    }

    val settingsFlow: Flow<AppSettings> =
        context.dataStore.data.map { prefs ->
            val routesString = prefs[KEY_BOTTOM_NAV_ROUTES] ?: ""
            val routes = if (routesString.isBlank()) {
                emptyList()
            } else {
                routesString.split(",").map { it.trim() }
            }
            AppSettings(
                currentUserId = prefs[KEY_CURRENT_USER] ?: "",
                themeMode = try {
                    ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name)
                } catch (e: Exception) {
                    ThemeMode.SYSTEM
                },
                themeCustomJson = prefs[KEY_THEME_CUSTOM_JSON],
                biometricEnabled = prefs[KEY_BIOMETRIC_ENABLED] ?: false,
                activeShoppingListId = prefs[KEY_ACTIVE_SHOPPING_LIST] ?: "",
                bottomNavRoutes = routes,
                familyOnboardingDismissed = prefs[KEY_FAMILY_ONBOARDING_DISMISSED] ?: false,
            )
        }

    suspend fun setCurrentUser(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_USER] = id
        }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = themeMode.name
        }
    }

    // JSON со схемой цветов конструктора; null/пусто = использовать дефолт
    suspend fun setCustomThemeJson(json: String?) {
        context.dataStore.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(KEY_THEME_CUSTOM_JSON) else prefs[KEY_THEME_CUSTOM_JSON] = json
        }
    }
    
    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = enabled
        }
    }
    
    suspend fun setActiveShoppingList(listId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_SHOPPING_LIST] = listId
        }
    }

    suspend fun setBottomNavRoutes(routes: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BOTTOM_NAV_ROUTES] = routes.joinToString(",")
        }
    }

    suspend fun setFamilyOnboardingDismissed(dismissed: Boolean) {
        context.dataStore.edit { prefs ->
            if (dismissed) {
                prefs[KEY_FAMILY_ONBOARDING_DISMISSED] = true
            } else {
                prefs.remove(KEY_FAMILY_ONBOARDING_DISMISSED)
            }
        }
    }

    /** Clears per-account session pointers while keeping device-level preferences (theme, biometrics). */
    suspend fun resetUserSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CURRENT_USER)
            prefs.remove(KEY_ACTIVE_SHOPPING_LIST)
            prefs.remove(KEY_FAMILY_ONBOARDING_DISMISSED)
        }
    }
}
