// Dosya: app/src/main/java/com/rootcrack/aigarage/components/CustomBottomNavBar.kt
package com.rootcrack.aigarage.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rootcrack.aigarage.navigation.Screen

@Composable
fun CustomBottomNavBar(
    navController: NavController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    // Açıklama: Instagram tarzı gradient background
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Açıklama: Keşfet (Ana Ekran)
                ModernBottomNavItem(
                    icon = Icons.Outlined.Explore,
                    filledIcon = Icons.Filled.Explore,
                    isSelected = currentRoute == Screen.Home.route,
                    onClick = {
                        if (currentRoute != Screen.Home.route) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )

                // Açıklama: Galeri (Fotoğraf seçimi)
                ModernBottomNavItem(
                    icon = Icons.Outlined.PhotoLibrary,
                    filledIcon = Icons.Filled.PhotoLibrary,
                    isSelected = currentRoute == Screen.Gallery.route,
                    onClick = {
                        if (currentRoute != Screen.Gallery.route) {
                            navController.navigate(Screen.Gallery.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )

                // Açıklama: Kamera (Merkez - özel tasarım)
              //  CameraButton(
              //      onClick = {
              //          navController.navigate(Screen.Camera.route)
              //      }
              //  )

                // Açıklama: AI Studio (Koleksiyonlar)
                ModernBottomNavItem(
                    icon = Icons.Outlined.AutoAwesome,
                    filledIcon = Icons.Filled.AutoAwesome,
                    isSelected = false, // Henüz bu ekran yok
                    onClick = {
                        // TODO: AI Studio ekranına navigation
                    }
                )

                // Açıklama: Profil/Ayarlar
                ModernBottomNavItem(
                    icon = Icons.Outlined.Person,
                    filledIcon = Icons.Filled.Person,
                    isSelected = currentRoute == Screen.Settings.route,
                    onClick = {
                        if (currentRoute != Screen.Settings.route) {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ModernBottomNavItem(
    icon: ImageVector,
    filledIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "nav_scale"
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .size(48.dp)
    ) {
        Icon(
            imageVector = if (isSelected) filledIcon else icon,
            contentDescription = null,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            },
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun CameraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "camera_scale"
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .size(56.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                ),
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = "Kamera",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}