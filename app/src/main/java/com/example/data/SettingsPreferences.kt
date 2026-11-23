package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cronDeckDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "crondeck_settings"
)

data class AppSettings(
    val darkMode: Boolean = true,
    val respectSystemDnd: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 22,
    val quietStartMinute: Int = 0,
    val quietEndHour: Int = 7,
    val quietEndMinute: Int = 0,
    val allowCriticalBypassQuietHours: Boolean = false,
    val encryptByDefault: Boolean = false,
    val maskEncryptedPreviews: Boolean = true,
    val defaultTtsEnabled: Boolean = true,
    val beginnerBannerDismissed: Boolean = false
)

class SettingsPreferences(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val RESPECT_SYSTEM_DND = booleanPreferencesKey("respect_system_dnd")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_START_MINUTE = intPreferencesKey("quiet_start_minute")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val QUIET_END_MINUTE = intPreferencesKey("quiet_end_minute")
        val CRITICAL_BYPASS_QUIET = booleanPreferencesKey("critical_bypass_quiet")
        val ENCRYPT_BY_DEFAULT = booleanPreferencesKey("encrypt_by_default")
        val MASK_ENCRYPTED_PREVIEWS = booleanPreferencesKey("mask_encrypted_previews")
        val DEFAULT_TTS_ENABLED = booleanPreferencesKey("default_tts_enabled")
        val BEGINNER_BANNER_DISMISSED = booleanPreferencesKey("beginner_banner_dismissed")
    }

    val settingsFlow: Flow<AppSettings> = context.cronDeckDataStore.data.map { prefs ->
        AppSettings(
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            respectSystemDnd = prefs[Keys.RESPECT_SYSTEM_DND] ?: true,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: false,
            quietStartHour = prefs[Keys.QUIET_START_HOUR] ?: 22,
            quietStartMinute = prefs[Keys.QUIET_START_MINUTE] ?: 0,
            quietEndHour = prefs[Keys.QUIET_END_HOUR] ?: 7,
            quietEndMinute = prefs[Keys.QUIET_END_MINUTE] ?: 0,
            allowCriticalBypassQuietHours = prefs[Keys.CRITICAL_BYPASS_QUIET] ?: false,
            encryptByDefault = prefs[Keys.ENCRYPT_BY_DEFAULT] ?: false,
            maskEncryptedPreviews = prefs[Keys.MASK_ENCRYPTED_PREVIEWS] ?: true,
            defaultTtsEnabled = prefs[Keys.DEFAULT_TTS_ENABLED] ?: true,
            beginnerBannerDismissed = prefs[Keys.BEGINNER_BANNER_DISMISSED] ?: false
        )
    }

    suspend fun getSnapshot(): AppSettings = settingsFlow.first()

    suspend fun setDarkMode(enabled: Boolean) {
        context.cronDeckDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setRespectSystemDnd(enabled: Boolean) {
        context.cronDeckDataStore.edit { it[Keys.RESPECT_SYSTEM_DND] = enabled }
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.cronDeckDataStore.edit { it[Keys.QUIET_HOURS_ENABLED] = enabled }
    }

    suspend fun setQuietWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        context.cronDeckDataStore.edit {
            it[Keys.QUIET_START_HOUR] = startHour
            it[Keys.QUIET_START_MINUTE] = startMinute
            it[Keys.QUIET_END_HOUR] = endHour
            it[Keys.QUIET_END_MINUTE] = endMinute
        }
    }

    suspend fun setCriticalBypassQuiet(enabled: Boolean) {
        context.cronDeckDataStore.edit { it[Keys.CRITICAL_BYPASS_QUIET] = enabled }
    }

    suspend fun setEncryptByDefault(enabled: Boolean) {
        context.cronDeckDataStore.edit { it[Keys.ENCRYPT_BY_DEFAULT] = enabled }
    }

    suspend fun setMaskEncryptedPreviews(enabled: Boolean) {
        context.cronDeckDataStore.edit { it[Keys.MASK_ENCRYPTED_PREVIEWS] = enabled }
    }

    suspend fun setDefaultTtsEnabled(enabled: Boolean) {
        context.cronDeckDataStore.edit { it[Keys.DEFAULT_TTS_ENABLED] = enabled }
    }

    suspend fun dismissBeginnerBanner() {
        context.cronDeckDataStore.edit { it[Keys.BEGINNER_BANNER_DISMISSED] = true }
    }
}
