package com.rootcrack.aigarage.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomBottomNavItem(
                icon = Icons.Default.Home,
                label = "Ana Ekran",
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

            CustomBottomNavItem(
                icon = Icons.Default.Collections,
                label = "Galeri",
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

            CustomBottomNavItem(
                icon = Icons.Default.Settings,
                label = "Ayarlar",
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

@Composable
fun CustomBottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(if (isSelected) 1.2f else 1.0f)

    IconButton(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp)
        )
    }
}