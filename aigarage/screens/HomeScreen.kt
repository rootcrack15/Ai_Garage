package com.rootcrack.aigarage.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.rootcrack.aigarage.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "AI Garage") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(Screen.Camera.route) }) { // Rota kullanımı daha iyi
                        Icon(Icons.Default.CameraAlt, contentDescription = "Kamera")
                    }
                },
                actions = {
                    // Mevcut Bildirim İkonu
                    IconButton(onClick = {
                        // TODO: Bildirimler için bir rota veya işlev tanımlayın
                        // Örnek: navController.navigate("notifications_route")
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Bildirimler")
                    }
                    /*
                    // YENİ EKLENEN AYARLAR İKONU
                    IconButton(onClick = {
                        navController.navigate(Screen.Settings.route) // Ayarlar ekranına git
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Ayarlar"
                        )
                    }
                    */
                }
            )
        })
    { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("Hoş geldin! Yakında burada efektler, öneriler ve daha fazlası olacak!")
        }
    }
}