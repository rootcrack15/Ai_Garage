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
import com.rootcrack.aigarage.components.CustomBottomNavBar
import com.rootcrack.aigarage.screens.*
import com.rootcrack.aigarage.viewmodels.CameraViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home : Screen("home_screen")
    object Camera : Screen("camera_screen")
    object Gallery : Screen("gallery_screen")
    object Settings : Screen("settings_screen")
    object PhotoPreview : Screen("photo_preview_screen/{imageUri}")
    object EditPhoto : Screen("edit_photo_screen/{${NavArgs.IMAGE_URI}}/{${NavArgs.INSTRUCTION}}/{${NavArgs.MASK}}/{${NavArgs.EDIT_TYPE}}")
    object PhotoDetail : Screen("photo_detail_screen/{${NavArgs.IMAGE_URI}}")
    object AutoMaskPreview : Screen("auto_mask_preview_screen/{${NavArgs.IMAGE_URI}}/{${NavArgs.OBJECT_TO_MASK}}/{${NavArgs.MODEL_PATH}}")
}

object NavArgs {
    const val IMAGE_URI = "imageUri"
    const val INSTRUCTION = "instruction"
    const val OBJECT_TO_MASK = "objectToMask"
    const val MASK = "mask"
    const val EDIT_TYPE = "editType"
    const val MODEL_PATH = "modelPath" // Yeni argüman
}

object EditTypeValues {
    const val FOREGROUND = "foreground"
    const val BACKGROUND = "background"
    const val NONE = "none"
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
                    navArgument(NavArgs.EDIT_TYPE) { type = NavType.StringType; nullable = true; defaultValue = EditTypeValues.NONE }
                )
            ) { backStackEntry ->
                val encodedImageUri = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI)
                val encodedInitialInstruction = backStackEntry.arguments?.getString(NavArgs.INSTRUCTION)
                val encodedMask = backStackEntry.arguments?.getString(NavArgs.MASK)
                val editType = backStackEntry.arguments?.getString(NavArgs.EDIT_TYPE) ?: EditTypeValues.NONE

                if (encodedImageUri == null) {
                    navController.popBackStack()
                    return@composable
                }

                EditPhotoScreenWithPrompt(
                    navController = navController,
                    imageUri = Uri.decode(encodedImageUri),
                    initialInstruction = encodedInitialInstruction?.let { Uri.decode(it) } ?: "",
                    base64Mask = encodedMask?.let { if (it.isNotEmpty() && it != "null") Uri.decode(it) else null },
                    editType = editType,
                    onProcessPhoto = { originalImageUri, maskBase64, instruction ->
                        coroutineScope.launch {
                            val processedUri = cameraScreenState.processImageWithAI(
                                context = context,
                                imageUri = originalImageUri,
                                instruction = instruction,
                                base64Mask = maskBase64
                            )
                            if (processedUri != null) {
                                val routeWithArgument = Screen.PhotoPreview.route.replace(
                                    "{${NavArgs.IMAGE_URI}}",
                                    Uri.encode(processedUri.toString())
                                )
                                navController.navigate(routeWithArgument)
                            } else {
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
                    navArgument(NavArgs.OBJECT_TO_MASK) { type = NavType.StringType },
                    navArgument(NavArgs.MODEL_PATH) { type = NavType.StringType } // Yeni argüman
                )
            ) { backStackEntry ->
                val imageUriString = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI)
                val objectToMask = backStackEntry.arguments?.getString(NavArgs.OBJECT_TO_MASK)
                val modelPath = backStackEntry.arguments?.getString(NavArgs.MODEL_PATH) // Yeni argüman

                if (imageUriString == null || objectToMask == null || modelPath == null) {
                    Toast.makeText(context, "Otomatik maskeleme başlatılamadı.", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                    return@composable
                }
                AutoMaskPreviewScreen(
                    navController = navController,
                    imageUriString = Uri.decode(imageUriString),
                    objectsToMaskCommaSeparated = Uri.decode(objectToMask),
                    modelPath = Uri.decode(modelPath) // Yeni argümanı pasla
                )
            }
        }
    }
}
