package com.wasal.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(
    name = "wasal_settings"
)

class SettingsDataStore(
    private val context: Context
) {
    private val notificationsKey =
        booleanPreferencesKey("notifications_enabled")

    val notificationsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[notificationsKey] ?: true
        }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[notificationsKey] = enabled
        }
    }
}
