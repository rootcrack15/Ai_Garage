// Dosya: app/src/main/java/com/rootcrack/aigarage/MainActivity.kt
package com.rootcrack.aigarage

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.ads.MobileAds
import com.rootcrack.aigarage.data.preferences.ThemePreferences
import com.rootcrack.aigarage.data.preferences.LanguageManager // Açıklama: Dil yöneticisi import'u
import com.rootcrack.aigarage.navigation.AppNavigation
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Açıklama: Kaydedilen dil ayarını uygula - uygulama başlatılırken
        LanguageManager.applySavedLanguage(this)

        // Açıklama: Edge-to-edge düzeltildi - sistem bar'ları sistem penceresi dışına çıkarıyor
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Açıklama: Sistem bar'larını şeffaf yapıyor
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            AIGarageApp()
        }
    }
}

@Composable
fun AIGarageApp() {
    val context = LocalContext.current
    
    // Açıklama: AdMob'u Application sınıfında değil burada başlatıyoruz (daha iyi lifecycle yönetimi)
    LaunchedEffect(Unit) {
        MobileAds.initialize(context) {}
    }
    
    // Açıklama: Alpha animasyonu deaktif - görünürlük problemi yaratıyordu
    // var isLoading by remember { mutableStateOf(true) }
    // val alpha by animateFloatAsState(
    //     targetValue = if (isLoading) 0f else 1f,
    //     animationSpec = tween(durationMillis = 500),
    //     label = "fade_animation"
    // )
    // 
    // LaunchedEffect(Unit) {
    //     delay(100) // Kısa loading için
    //     isLoading = false
    // }
    
    val themePreference = ThemePreferences.getThemeFlow(context)
        .collectAsState(initial = "light")
    val isDarkTheme = themePreference.value == "dark"

    MaterialTheme(
        colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(), // Açıklama: Alpha kaldırıldı
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation()
        }
    }
}