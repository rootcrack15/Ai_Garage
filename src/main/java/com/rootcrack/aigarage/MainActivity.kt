package com.rootcrack.aigarage

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.ads.MobileAds
import com.rootcrack.aigarage.navigation.AppNavigation
import com.rootcrack.aigarage.screens.THEME_KEY
import com.rootcrack.aigarage.screens.dataStore
import kotlinx.coroutines.flow.map


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val THEME_KEY = stringPreferencesKey("theme_preference")
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge için
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // AdMob SDK'sını başlatın. Bu, reklamların yüklenebilmesi için gereklidir.
        // Uygulamanız başladığında yalnızca bir kez çağrılmalıdır.
        MobileAds.initialize(this) {}

        setContent {
            val themePreference = LocalContext.current.dataStore.data
                .map { preferences ->
                    preferences[THEME_KEY] ?: "light"
                }
                .collectAsState(initial = "light")
            val isDarkTheme = themePreference.value == "dark"

            MaterialTheme(
                colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            ) {
                AppNavigation()
            }
        }
    }
}
