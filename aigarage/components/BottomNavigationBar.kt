package com.rootcrack.aigarage.components

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.rootcrack.aigarage.navigation.Screen

// Navigasyon öğelerini ve ikonlarını tanımlamak için bir data class veya sealed class kullanmak daha iyi olur
// Ancak mevcut Screen yapınıza uyum sağlamak için şimdilik doğrudan Screen nesnelerini kullanalım.
// Eğer Screen class'ınızda ikon ve başlık bilgisi yoksa, onları eklemeniz iyi olur.
// Örnek:
// sealed class Screen(val route: String, val title: String? = null, val icon: ImageVector? = null) {
//     object Home : Screen("home_screen", "Ana Sayfa", Icons.Filled.Home)
//     object Gallery : Screen("gallery_screen", "Galeri", Icons.Filled.Image)
//     // ... diğer ekranlar
// }


@Composable
fun BottomNavigationBar(navController: NavController) {
    // Burada gösterilecek ana navigasyon öğelerini tanımlayın.
    // Screen sealed class'ınızda bu bilgilerin (route, title, icon) olması idealdir.
    val navigationItems = listOf(
        Screen.Home,    // Screen.Home.route, Screen.Home.title, Screen.Home.icon kullanılacak
        Screen.Gallery  // Screen.Gallery.route, Screen.Gallery.title, Screen.Gallery.icon kullanılacak
        // ... diğer ana ekranlarınız (Örn: Screen.Camera, Screen.Settings vb.)
    )

    // Box yerine doğrudan NavigationBar kullanmak Material 3 standartlarına daha uygun.
    // Eski Box yapısı, Material 2'deki BottomNavigation'a daha çok benziyor.
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp), // Yüksekliği tercihinize göre ayarlayabilirsiniz
        // containerColor = MaterialTheme.colorScheme.surface, // İsteğe bağlı arka plan rengi
        // contentColor = MaterialTheme.colorScheme.onSurface // İsteğe bağlı içerik rengi
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        navigationItems.forEach { screen ->
            // Eğer Screen class'ınızda title ve icon bilgisi yoksa, burada manuel olarak eşleştirmeniz gerekir.
            // Örnek olarak varsayılan ikon ve başlıklar eklendi, bunları Screen class'ınızdan almalısınız.
            val title = when (screen) {
                Screen.Home -> "Ana Sayfa"
                Screen.Gallery -> "Galeri"
                else -> screen.route // Başlık yoksa rota adını kullan
            }
            val icon = when (screen) {
                Screen.Home -> Icons.Filled.Home
                Screen.Gallery -> Icons.Filled.Image
                else -> Icons.Filled.BrokenImage // Eşleşen ikon yoksa
            }

            NavigationBarItem(
                icon = { Icon(icon, contentDescription = title) },
                label = { Text(title) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    Log.d("BottomNavBar", "Navigating to: ${screen.route}. Current dest: ${currentDestination?.route}")
                    if (currentDestination?.route != screen.route) { // Mevcut ekrana tekrar tıklamayı engelle (isteğe bağlı)
                        navController.navigate(screen.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            // on the back stack as users select items.
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item.
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item.
                            restoreState = true
                        }
                    }
                },
                // colors = NavigationBarItemDefaults.colors(...) // İsteğe bağlı renkler
            )
        }
    }
}
