// Dosya: app/src/main/java/com/rootcrack/aigarage/repository/ThemeRepository.kt
package com.rootcrack.aigarage.repository

import android.content.Context
import com.rootcrack.aigarage.data.preferences.ThemePreferences
import kotlinx.coroutines.flow.Flow

// Açıklama: Repository pattern - veri yönetimi için ayrılmış katman (DI olmadan basit implementasyon)
interface ThemeRepository {
    fun getThemeFlow(): Flow<String>
    suspend fun setTheme(theme: String)
}

class ThemeRepositoryImpl(
    private val context: Context
) : ThemeRepository {
    
    override fun getThemeFlow(): Flow<String> {
        return ThemePreferences.getThemeFlow(context)
    }
    
    override suspend fun setTheme(theme: String) {
        ThemePreferences.setTheme(context, theme)
    }
}