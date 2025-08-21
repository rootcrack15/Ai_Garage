// Dosya: app/src/main/java/com/rootcrack/aigarage/data/preferences/ThemePreferences.kt
package com.rootcrack.aigarage.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Açıklama: DataStore sabitleri ve extension'lar tek dosyada toplandı
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemePreferences {
    val THEME_KEY = stringPreferencesKey("theme_preference")
    
    fun getThemeFlow(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[THEME_KEY] ?: "light"
        }
    }
    
    suspend fun setTheme(context: Context, theme: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }
}
