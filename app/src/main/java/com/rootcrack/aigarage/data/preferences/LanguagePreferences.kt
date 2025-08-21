// Dosya: app/src/main/java/com/rootcrack/aigarage/data/preferences/LanguagePreferences.kt
// Açıklama: Uygulama dil ayarlarını yöneten sınıf
// SharedPreferences kullanarak dil tercihini saklar ve geri yükler
package com.rootcrack.aigarage.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.*

// Açıklama: Desteklenen diller
enum class AppLanguage(val code: String, val displayName: String) {
    TURKISH("tr", "Türkçe"),
    ENGLISH("en", "English")
}

// Açıklama: Dil tercihlerini yöneten sınıf
class LanguagePreferences(context: Context) {
    
    // Açıklama: SharedPreferences dosya adı
    companion object {
        private const val PREF_NAME = "language_preferences"
        private const val KEY_LANGUAGE = "selected_language"
        private const val DEFAULT_LANGUAGE = "tr" // Açıklama: Varsayılan dil Türkçe
    }
    
    // Açıklama: SharedPreferences instance'ı
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, 
        Context.MODE_PRIVATE
    )
    
    // Açıklama: Seçili dili al
    fun getSelectedLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }
    
    // Açıklama: Dil tercihini kaydet
    fun setSelectedLanguage(languageCode: String) {
        sharedPreferences.edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
    }
    
    // Açıklama: Mevcut dili AppLanguage enum'una dönüştür
    fun getCurrentLanguage(): AppLanguage {
        return when (getSelectedLanguage()) {
            "en" -> AppLanguage.ENGLISH
            "tr" -> AppLanguage.TURKISH
            else -> AppLanguage.TURKISH // Açıklama: Varsayılan olarak Türkçe
        }
    }
    
    // Açıklama: Desteklenen tüm dilleri al
    fun getSupportedLanguages(): List<AppLanguage> {
        return AppLanguage.values().toList()
    }
}

// Açıklama: Dil değiştirme işlemlerini yöneten sınıf
object LanguageManager {
    
    // Açıklama: Uygulama dilini değiştir
    fun setAppLanguage(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        // Açıklama: Yeni konfigürasyonu uygula
        context.createConfigurationContext(config)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        
        // Açıklama: Dil tercihini kaydet
        val languagePreferences = LanguagePreferences(context)
        languagePreferences.setSelectedLanguage(languageCode)
    }
    
    // Açıklama: Uygulama başlatılırken dil ayarını uygula
    fun applySavedLanguage(context: Context) {
        val languagePreferences = LanguagePreferences(context)
        val savedLanguage = languagePreferences.getSelectedLanguage()
        
        // Açıklama: Eğer sistem dili farklıysa ve kullanıcı tercih etmişse, kaydedilen dili uygula
        val currentLocale = context.resources.configuration.locales[0]
        if (currentLocale.language != savedLanguage) {
            setAppLanguage(context, savedLanguage)
        }
    }
    
    // Açıklama: Sistem dilini al
    fun getSystemLanguage(): String {
        return Locale.getDefault().language
    }
    
    // Açıklama: Dil kodunu display name'e çevir
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            "tr" -> "Türkçe"
            "en" -> "English"
            else -> "Türkçe"
        }
    }
}
