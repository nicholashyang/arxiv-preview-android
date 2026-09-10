package com.example.arxivpreview.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");
    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class SwipeAction(val label: String) {
    FAVORITE("Toggle favorite"), ORGANIZE("Groups & tags"), SHARE("Share"), DOWNLOAD("Download PDF"), MORE("More actions"), NONE("Off");
    companion object { fun stored(value: String?, fallback: SwipeAction) = entries.firstOrNull { it.name == value } ?: fallback }
}

data class AppPreferences(
    val swipeLeft: SwipeAction = SwipeAction.MORE,
    val swipeRight: SwipeAction = SwipeAction.FAVORITE,
    val readerMobile: Boolean = true,
    val readerFont: Int = 18,
    val readerLine: Float = 1.6f,
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val categories: Set<String> = emptySet(),
    val notificationsEnabled: Boolean = false,
    val lastPublishedAt: Long = 0L,
)

class PreferencesRepository(private val context: Context) {
    val preferences: Flow<AppPreferences> = context.settingsDataStore.data.map { values ->
        AppPreferences(
            swipeLeft = SwipeAction.stored(values[SWIPE_LEFT], SwipeAction.MORE),
            swipeRight = SwipeAction.stored(values[SWIPE_RIGHT], SwipeAction.FAVORITE),
            readerMobile = values[READER_MOBILE] ?: true,
            readerFont = values[READER_FONT]?.toIntOrNull()?.takeIf { it in listOf(16,18,20,22,24) } ?: 18,
            readerLine = values[READER_LINE]?.toFloatOrNull()?.takeIf { it in listOf(1.4f,1.6f,1.8f) } ?: 1.6f,
            onboardingComplete = values[ONBOARDING_COMPLETE] ?: false,
            themeMode = ThemeMode.fromStored(values[THEME_MODE]),
            categories = values[CATEGORIES].orEmpty(),
            notificationsEnabled = values[NOTIFICATIONS_ENABLED] ?: false,
            lastPublishedAt = values[LAST_PUBLISHED_AT] ?: 0L,
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

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setSwipe(left: Boolean, action: SwipeAction) {
        context.settingsDataStore.edit { it[if (left) SWIPE_LEFT else SWIPE_RIGHT] = action.name }
    }
    suspend fun setReader(mobile: Boolean, font: Int, line: Float) {
        context.settingsDataStore.edit { it[READER_MOBILE] = mobile; it[READER_FONT] = font.toString(); it[READER_LINE] = line.toString() }
    }
    private companion object {
        val SWIPE_LEFT = stringPreferencesKey("swipe_left")
        val SWIPE_RIGHT = stringPreferencesKey("swipe_right")
        val READER_MOBILE = booleanPreferencesKey("reader_mobile")
        val READER_FONT = stringPreferencesKey("reader_font")
        val READER_LINE = stringPreferencesKey("reader_line")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val CATEGORIES = stringSetPreferencesKey("categories")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LAST_PUBLISHED_AT = longPreferencesKey("last_published_at")
    }
}
