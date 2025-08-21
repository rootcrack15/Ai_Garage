// Dosya: app/src/main/java/com/rootcrack/aigarage/viewmodel/ThemeViewModel.kt
package com.rootcrack.aigarage.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootcrack.aigarage.repository.ThemeRepository
import com.rootcrack.aigarage.repository.ThemeRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// Açıklama: Modern MVVM pattern - tema yönetimi için ViewModel (DI olmadan basit implementasyon)
class ThemeViewModel(context: Context) : ViewModel() {
    
    private val themeRepository: ThemeRepository = ThemeRepositoryImpl(context)
    
    val themeFlow: Flow<String> = themeRepository.getThemeFlow()
    
    fun setTheme(theme: String) {
        viewModelScope.launch {
            themeRepository.setTheme(theme)
        }
    }
}