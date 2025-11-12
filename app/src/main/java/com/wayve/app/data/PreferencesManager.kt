package com.wayve.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

// Extension property to create DataStore instance
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * PreferencesManager - Simplified for Wayve Song app
 * Old backlog functionality removed
 */
class PreferencesManager(private val context: Context) {
    
    companion object {
        val DOWNLOAD_LOCATION_KEY = stringPreferencesKey("download_location")
        val USE_CUSTOM_LOCATION_KEY = stringPreferencesKey("use_custom_location")
    }
    
    // Flow to observe download location changes
    val downloadLocationFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[DOWNLOAD_LOCATION_KEY]
        }
    
    val useCustomLocationFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USE_CUSTOM_LOCATION_KEY]?.toBoolean() ?: false
        }
    
    // Save download location
    suspend fun saveDownloadLocation(location: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_LOCATION_KEY] = location
        }
    }
    
    // Get download location
    suspend fun getDownloadLocation(): String? {
        return context.dataStore.data
            .map { preferences -> preferences[DOWNLOAD_LOCATION_KEY] }
            .first()
    }
}
