package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.SupportedLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mangalingo_settings"
)

data class AppSettings(
    val defaultTargetLang: SupportedLanguage = SupportedLanguage.SPANISH,
    val autoScanEnabled: Boolean = false,
    val rtlReadingDirection: Boolean = false,
    val showBubbleOutlines: Boolean = true,
    val darkThemeEnabled: Boolean = true
)

class SettingsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    val settingsFlow: Flow<AppSettings> = appContext.settingsDataStore.data.map { prefs ->
        val langCode = prefs[KEY_DEFAULT_TARGET_LANG]
        val targetLang = if (langCode.isNullOrBlank()) {
            SupportedLanguage.SPANISH
        } else {
            SupportedLanguage.fromCode(langCode)
        }
        AppSettings(
            defaultTargetLang = targetLang,
            autoScanEnabled = prefs[KEY_AUTO_SCAN_ENABLED] ?: false,
            rtlReadingDirection = prefs[KEY_RTL_READING] ?: false,
            showBubbleOutlines = prefs[KEY_SHOW_BUBBLE_OUTLINES] ?: true,
            darkThemeEnabled = prefs[KEY_DARK_THEME] ?: true
        )
    }

    suspend fun setDefaultTargetLang(language: SupportedLanguage) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_TARGET_LANG] = language.code
        }
    }

    suspend fun setAutoScanEnabled(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_SCAN_ENABLED] = enabled
        }
    }

    suspend fun setRtlReadingDirection(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_RTL_READING] = enabled
        }
    }

    suspend fun setShowBubbleOutlines(show: Boolean) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_SHOW_BUBBLE_OUTLINES] = show
        }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_DARK_THEME] = enabled
        }
    }

    suspend fun resetToDefaults() {
        appContext.settingsDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        private val KEY_DEFAULT_TARGET_LANG = stringPreferencesKey("default_target_lang")
        private val KEY_AUTO_SCAN_ENABLED = booleanPreferencesKey("auto_scan_enabled")
        private val KEY_RTL_READING = booleanPreferencesKey("rtl_reading_direction")
        private val KEY_SHOW_BUBBLE_OUTLINES = booleanPreferencesKey("show_bubble_outlines")
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme_enabled")

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
        }
    }
}
