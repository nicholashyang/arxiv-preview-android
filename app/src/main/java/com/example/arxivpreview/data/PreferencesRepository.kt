package com.example.arxivpreview.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

data class AppPreferences(
    val onboardingComplete: Boolean = false,
    val categories: Set<String> = emptySet(),
    val notificationsEnabled: Boolean = false,
    val lastPublishedAt: Long = 0L,
    val automaticAppUpdates: Boolean = false,
)

class PreferencesRepository(private val context: Context) {
    val preferences: Flow<AppPreferences> = context.settingsDataStore.data.map { values ->
        AppPreferences(
            onboardingComplete = values[ONBOARDING_COMPLETE] ?: false,
            categories = values[CATEGORIES].orEmpty(),
            notificationsEnabled = values[NOTIFICATIONS_ENABLED] ?: false,
            lastPublishedAt = values[LAST_PUBLISHED_AT] ?: 0L,
            automaticAppUpdates = values[AUTOMATIC_APP_UPDATES] ?: false,
        )
    }

    suspend fun finishOnboarding(categories: Set<String>) {
        require(categories.isNotEmpty())
        context.settingsDataStore.edit {
            it[ONBOARDING_COMPLETE] = true
            it[CATEGORIES] = categories
        }
    }

    suspend fun setCategories(categories: Set<String>) {
        require(categories.isNotEmpty())
        context.settingsDataStore.edit { it[CATEGORIES] = categories }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setLastPublishedAt(value: Long) {
        context.settingsDataStore.edit { it[LAST_PUBLISHED_AT] = value }
    }

    suspend fun setAutomaticAppUpdates(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTOMATIC_APP_UPDATES] = enabled }
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val CATEGORIES = stringSetPreferencesKey("categories")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LAST_PUBLISHED_AT = longPreferencesKey("last_published_at")
        val AUTOMATIC_APP_UPDATES = booleanPreferencesKey("automatic_app_updates")
    }
}
