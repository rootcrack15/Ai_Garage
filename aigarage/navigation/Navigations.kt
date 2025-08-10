// Navigations.kt: Uygulamanın navigasyon yapısını tanımlar, ekranlar arasında geçişi yönetir. API 24+ uyumlu.

package com.rootcrack.aigarage.navigation

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rootcrack.aigarage.components.CustomBottomNavBar // CustomBottomNavBar'ı kullanıyoruz
import com.rootcrack.aigarage.screens.AutoMaskPreviewScreen
import com.rootcrack.aigarage.screens.CameraScreen
import com.rootcrack.aigarage.screens.EditPhotoScreenWithPrompt // Güncellenmiş düzenleme ekranı
import com.rootcrack.aigarage.screens.GalleryScreen
import com.rootcrack.aigarage.screens.HomeScreen
import com.rootcrack.aigarage.screens.PhotoPreviewScreen
import com.rootcrack.aigarage.screens.SettingsScreen
import com.rootcrack.aigarage.screens.rememberCameraScreenStateHolder
import com.rootcrack.aigarage.viewmodels.CameraViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home : Screen("home_screen")
    object Camera : Screen("camera_screen")
    object Gallery : Screen("gallery_screen")
    // object PhotoPager : Screen("photo_pager_screen") // Kullanılmıyorsa kaldırılabilir veya yorumda bırakılabilir
    object Settings : Screen("settings_screen")
    object PhotoPreview : Screen("photo_preview_screen/{imageUri}")

    // EditPhoto artık maske parametresini de alacak (opsiyonel)
    // NavArgs.MASK boş string olabilir veya base64 maske içerebilir
    object EditPhoto : Screen("edit_photo_screen/{${NavArgs.IMAGE_URI}}/{${NavArgs.INSTRUCTION}}/{${NavArgs.MASK}}")

    object PhotoDetail : Screen("photo_detail_screen/{${NavArgs.IMAGE_URI}}") // Rotayı daha belirgin yapalım
    object AutoMaskPreview : Screen("auto_mask_preview_screen/{${NavArgs.IMAGE_URI}}/{${NavArgs.OBJECT_TO_MASK}}")
}

object NavArgs {
    const val IMAGE_URI = "imageUri"
    // const val FILE_PATH = "filePath" // PhotoPreview için kullanılmıyorsa kaldırılabilir
    const val INSTRUCTION = "instruction"
    const val OBJECT_TO_MASK = "objectToMask"
    const val MASK = "mask" // EditPhotoScreenWithPrompt için base64 maske argümanı
}

// objectLabelToClassIndex AutoMaskPreviewScreen.kt içine taşındı, burada artık gerek yok.

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val cameraViewModel: CameraViewModel = viewModel()
    val cameraScreenState = rememberCameraScreenStateHolder()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Bottom bar'ın hangi ekranlarda gösterileceğini belirle
    val showGlobalBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Gallery.route,
        Screen.Settings.route
        // PhotoPager veya PhotoDetail gibi iç sayfalarda gösterme
    ) && currentRoute?.startsWith(Screen.PhotoDetail.route.substringBefore("/{")) != true &&
            currentRoute?.startsWith(Screen.PhotoPreview.route.substringBefore("/{")) != true &&
            currentRoute?.startsWith(Screen.EditPhoto.route.substringBefore("/{")) != true &&
            currentRoute?.startsWith(Screen.AutoMaskPreview.route.substringBefore("/{")) != true &&
            currentRoute != Screen.Camera.route


    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showGlobalBottomBar) {
                // BottomNavigationBar yerine CustomBottomNavBar kullanılıyor
                CustomBottomNavBar(
                    navController = navController,
                    currentRoute = currentRoute
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showGlobalBottomBar) innerPadding else PaddingValues(0.dp))
        ) {
            composable(Screen.Home.route) {
                HomeScreen(navController = navController)
            }
            composable(Screen.Camera.route) {
                CameraScreen(
                    navController = navController,
                    cameraViewModel = cameraViewModel,
                    // stateHolder = cameraScreenState // Gerekliyse paslanabilir
                )
            }
            composable(
                route = Screen.PhotoPreview.route,
                arguments = listOf(navArgument(NavArgs.IMAGE_URI) { type = NavType.StringType })
            ) { backStackEntry ->
                val imageUriString = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI)
                // Bitmap yükleme PhotoPreviewScreen içinde yapılacak, burada sadece URI'yi geçir.
                PhotoPreviewScreen(
                    imageUriString = imageUriString?.let { Uri.decode(it) }, // URI'yi decode et
                    navController = navController
                )
            }

            composable(
                route = Screen.PhotoDetail.route,
                arguments = listOf(navArgument(NavArgs.IMAGE_URI) { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedImageUri = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI)
                // PhotoPreviewScreen'i PhotoDetail için de kullanıyoruz, URI'yi decode et
                PhotoPreviewScreen(
                    imageUriString = encodedImageUri?.let { Uri.decode(it) },
                    navController = navController
                    // Gerekirse farklı bir composable (PhotoDetailScreen) oluşturulabilir
                )
            }

            composable(
                route = Screen.EditPhoto.route,
                arguments = listOf(
                    navArgument(NavArgs.IMAGE_URI) { type = NavType.StringType },
                    navArgument(NavArgs.INSTRUCTION) { type = NavType.StringType; nullable = true }, // nullable olabilir
                    navArgument(NavArgs.MASK) { type = NavType.StringType; nullable = true } // Maske de nullable
                )
            ) { backStackEntry ->
                val encodedImageUri = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI)
                val encodedInitialInstruction = backStackEntry.arguments?.getString(NavArgs.INSTRUCTION)
                val encodedMask = backStackEntry.arguments?.getString(NavArgs.MASK) // Maskeyi al

                if (encodedImageUri == null) {
                    Log.e("AppNavigation", "EditPhoto için imageUri null geldi.")
                    Toast.makeText(context, "Resim düzenleme başlatılamadı.", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                    return@composable
                }

                EditPhotoScreenWithPrompt(
                    navController = navController,
                    imageUriString = Uri.decode(encodedImageUri), // URI'yi decode et
                    initialInstruction = encodedInitialInstruction?.let { Uri.decode(it) } ?: "",
                    base64Mask = encodedMask?.let { if (it.isNotEmpty()) Uri.decode(it) else null }, // Maskeyi decode et, boşsa null
                    onProcessPhoto = { originalImageUri, processedInstruction, finalMask ->
                        // AI işleme mantığı CameraScreenStateHolder'a taşındığı için
                        // CameraScreenStateHolder.processImageWithAI çağrılacak.
                        coroutineScope.launch {
                            val processedUri = cameraScreenState.processImageWithAI(
                                context = context,
                                imageUri = originalImageUri, // Bu zaten String
                                instruction = processedInstruction,
                                base64Mask = finalMask // Bu da zaten String?
                            )
                            if (processedUri != null) {
                                cameraScreenState.lastSavedImageUri.value = processedUri // Son kaydedilen URI'yi güncelle
                                Toast.makeText(context, "Fotoğraf başarıyla işlendi!", Toast.LENGTH_SHORT).show()

                                // İşlem sonrası PhotoPreview'a yönlendir
                                val routeWithArgument = Screen.PhotoPreview.route.replace(
                                    "{${NavArgs.IMAGE_URI}}",
                                    Uri.encode(processedUri.toString())
                                )
                                Log.d("AppNavigation", "Navigating to PhotoPreview with: $routeWithArgument")
                                navController.navigate(routeWithArgument) {
                                    popUpTo(Screen.Gallery.route) { inclusive = false } // Galeriyi backstack'te tut
                                }
                            } else {
                                Log.e("AppNavigation", "İşlenmiş URI null geldi.")
                                Toast.makeText(context, "Fotoğraf işlenirken sorun oluştu.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            composable(Screen.Gallery.route) {
                GalleryScreen(navController = navController)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController) // SettingsScreen'in navController alması gerekebilir
            }

            composable(
                route = Screen.AutoMaskPreview.route,
                arguments = listOf(
                    navArgument(NavArgs.IMAGE_URI) { type = NavType.StringType },
                    navArgument(NavArgs.OBJECT_TO_MASK) { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val imageUriString = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI)
                val objectToMask = backStackEntry.arguments?.getString(NavArgs.OBJECT_TO_MASK)

                if (imageUriString == null || objectToMask == null) {
                    Log.e("AppNavigation", "AutoMaskPreview için imageUri veya objectToMask null.")
                    Toast.makeText(context, "Otomatik maskeleme başlatılamadı.", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                    return@composable
                }
                AutoMaskPreviewScreen(
                    navController = navController,
                    imageUriString = Uri.decode(imageUriString), // URI'yi decode et
                    objectsToMaskCommaSeparated = Uri.decode(objectToMask) // Nesneyi decode et
                    // cameraScreenState = cameraScreenState // Gerekirse
                )
            }
        }
    }
}
