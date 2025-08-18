// Navigations.kt: Uygulamanın navigasyon yapısını tanımlar, ekranlar arasında geçişi yönetir. API 24+ uyumlu.

package com.rootcrack.aigarage.navigation

import android.net.Uri
import android.os.Build
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
import com.rootcrack.aigarage.components.CustomBottomNavBar // CustomBottomNavBar kullandığınızı varsayıyorum
import com.rootcrack.aigarage.screens.AutoMaskPreviewScreen
import com.rootcrack.aigarage.screens.CameraScreen
import com.rootcrack.aigarage.screens.EditPhotoScreenWithPrompt
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
    object Settings : Screen("settings_screen")
    object PhotoPreview : Screen("photo_preview_screen/{imageUri}")
    // EditPhoto rotasına EDIT_TYPE argümanını ekleyelim (isteğe bağlı ama kullanışlı olabilir)
    object EditPhoto : Screen("edit_photo_screen/{${NavArgs.IMAGE_URI}}/{${NavArgs.INSTRUCTION}}/{${NavArgs.MASK}}/{${NavArgs.EDIT_TYPE}}")
    object PhotoDetail : Screen("photo_detail_screen/{${NavArgs.IMAGE_URI}}")
    object AutoMaskPreview : Screen("auto_mask_preview_screen/{${NavArgs.IMAGE_URI}}/{${NavArgs.OBJECT_TO_MASK}}")
}

object NavArgs {
    const val IMAGE_URI = "imageUri"
    const val INSTRUCTION = "instruction"
    const val OBJECT_TO_MASK = "objectToMask"
    const val MASK = "mask"
    const val EDIT_TYPE = "editType" // Yeni argüman: "foreground" veya "background" olabilir
}

// EDIT_TYPE için varsayılan değerler
object EditTypeValues {
    const val FOREGROUND = "foreground"
    const val BACKGROUND = "background"
    const val NONE = "none" // Maske olmadan direkt düzenleme veya varsayılan
}

@RequiresApi(Build.VERSION_CODES.O)
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

    val showGlobalBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Gallery.route,
        Screen.Settings.route
    ) && currentRoute?.startsWith(Screen.PhotoDetail.route.substringBefore("/{")) != true &&
            currentRoute?.startsWith(Screen.PhotoPreview.route.substringBefore("/{")) != true &&
            currentRoute?.startsWith(Screen.EditPhoto.route.substringBefore("/{")) != true &&
            currentRoute?.startsWith(Screen.AutoMaskPreview.route.substringBefore("/{")) != true &&
            currentRoute != Screen.Camera.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showGlobalBottomBar) {
                // CustomBottomNavBar yerine BottomNavigationBar kullandığınızı varsayıyorum,
                // eğer CustomBottomNavBar ise o kalsın.
                // com.rootcrack.aigarage.components.BottomNavigationBar(navController = navController)
                CustomBottomNavBar(navController = navController, currentRoute = currentRoute)
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
                    cameraViewModel = cameraViewModel
                )
            }

            composable(
                route = Screen.PhotoPreview.route,
                arguments = listOf(navArgument(NavArgs.IMAGE_URI) { type = NavType.StringType })
            ) { backStackEntry ->
                val imageUriString = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI)
                PhotoPreviewScreen(
                    filePath = imageUriString?.let { Uri.decode(it) },
                    navController = navController
                )
            }

            composable(
                route = Screen.PhotoDetail.route,
                arguments = listOf(navArgument(NavArgs.IMAGE_URI) { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedImageUri = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI)
                PhotoPreviewScreen(
                    filePath = encodedImageUri?.let { Uri.decode(it) },
                    navController = navController
                )
            }

            composable(
                route = Screen.EditPhoto.route,
                arguments = listOf(
                    navArgument(NavArgs.IMAGE_URI) { type = NavType.StringType },
                    navArgument(NavArgs.INSTRUCTION) { type = NavType.StringType; nullable = true },
                    navArgument(NavArgs.MASK) { type = NavType.StringType; nullable = true },
                    navArgument(NavArgs.EDIT_TYPE) { type = NavType.StringType; nullable = true; defaultValue = EditTypeValues.NONE } // Yeni argüman eklendi
                )
            ) { backStackEntry ->
                val encodedImageUri = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI)
                val encodedInitialInstruction = backStackEntry.arguments?.getString(NavArgs.INSTRUCTION)
                val encodedMask = backStackEntry.arguments?.getString(NavArgs.MASK)
                val editType = backStackEntry.arguments?.getString(NavArgs.EDIT_TYPE) ?: EditTypeValues.NONE

                if (encodedImageUri == null) {
                    Log.e("AppNavigation", "EditPhoto için imageUri null geldi.")
                    Toast.makeText(context, "Resim düzenleme başlatılamadı.", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                    return@composable
                }

                EditPhotoScreenWithPrompt(
                    navController = navController,
                    imageUri = Uri.decode(encodedImageUri),
                    initialInstruction = encodedInitialInstruction?.let { Uri.decode(it) } ?: "",
                    base64Mask = encodedMask?.let { if (it.isNotEmpty() && it != "null") Uri.decode(it) else null },
                    editType = editType, // editType'ı EditPhotoScreenWithPrompt'a iletiyoruz
                    onProcessPhoto = { originalImageUri, maskBase64, instruction ->
                        coroutineScope.launch {
                            val processedUri = cameraScreenState.processImageWithAI(
                                context = context,
                                imageUri = originalImageUri,
                                instruction = instruction,
                                base64Mask = maskBase64
                            )
                            if (processedUri != null) {
                                cameraScreenState.lastSavedImageUri.value = processedUri
                                Toast.makeText(context, "Fotoğraf başarıyla işlendi!", Toast.LENGTH_SHORT).show()

                                val routeWithArgument = Screen.PhotoPreview.route.replace(
                                    "{${NavArgs.IMAGE_URI}}",
                                    Uri.encode(processedUri.toString())
                                )
                                Log.d("AppNavigation", "Navigating to PhotoPreview with: $routeWithArgument")
                                navController.navigate(routeWithArgument) {
                                    popUpTo(Screen.Gallery.route) { inclusive = false }
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
                SettingsScreen(navController = navController)
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
                    imageUriString = Uri.decode(imageUriString),
                    objectsToMaskCommaSeparated = Uri.decode(objectToMask)
                )
            }
        }
    }
}
