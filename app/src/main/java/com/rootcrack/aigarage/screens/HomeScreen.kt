// Dosya: app/src/main/java/com/rootcrack/aigarage/screens/HomeScreen.kt
package com.rootcrack.aigarage.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.rootcrack.aigarage.R
import com.rootcrack.aigarage.navigation.Screen
import com.rootcrack.aigarage.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.random.Random


// Açıklama: Instagram tarzı resim item data class'ı
data class ImageItem(
    val file: File,
    val isLiked: Boolean = false,
    val likeCount: Int = Random.nextInt(5, 150),
    val viewCount: Int = Random.nextInt(50, 500),
    var index: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var imageItems by remember { mutableStateOf<List<ImageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedImageItem by remember { mutableStateOf<ImageItem?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var draggedItem by remember { mutableStateOf<ImageItem?>(null) }

    // Açıklama: Beğeni durumlarını yönetmek için state
    var likedItems by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun loadProcessedImages() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val processedDir = context.getExternalFilesDir(FileUtil.APP_MEDIA_SUBDIRECTORY)
            val files: List<File> = processedDir?.listFiles { file ->
                file.isFile && (file.extension.equals("png", true) || file.extension.equals("jpg", true))
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            withContext(Dispatchers.Main) {
                imageItems = files.mapIndexed { index, file ->
                    ImageItem(
                        file = file,
                        isLiked = likedItems.contains(file.absolutePath),
                        index = index
                    )
                }
                isLoading = false
            }
        }
    }

    // Açıklama: Beğeni toggle fonksiyonu
    fun toggleLike(item: ImageItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val newLikedItems = if (likedItems.contains(item.file.absolutePath)) {
            likedItems - item.file.absolutePath
        } else {
            likedItems + item.file.absolutePath
        }
        likedItems = newLikedItems
        
        // Açıklama: Liste güncelleme
        imageItems = imageItems.map {
            if (it.file.absolutePath == item.file.absolutePath) {
                it.copy(isLiked = newLikedItems.contains(item.file.absolutePath))
            } else it
        }
    }

    LaunchedEffect(Unit, navController.currentBackStackEntry) {
        loadProcessedImages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SportsMotorsports,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.ai_garage_title),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                },
                actions = {
                    // Açıklama: Sürükle-bırak mode toggle
                    IconButton(
                        onClick = { 
                            isDragging = !isDragging
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    ) {
                        Icon(
                            imageVector = if (isDragging) Icons.Default.DragHandle else Icons.Default.GridView,
                            contentDescription = if (isDragging) stringResource(R.string.edit_mode_active) else stringResource(R.string.edit_mode_active),
                            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { navController.navigate(Screen.Camera.route) }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.camera_screen))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            if (isLoading) {
                // Açıklama: Modern loading indicator
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = stringResource(R.string.discover_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (imageItems.isEmpty()) {
                // Açıklama: Boş durum tasarımı
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Text(
                        text = stringResource(R.string.no_ai_processed_images),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(R.string.start_with_camera),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    
                    Button(
                        onClick = { navController.navigate(Screen.Camera.route) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.create_first_image))
                    }
                }
            } else {
                // Açıklama: Instagram tarzı staggered grid
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(8.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(imageItems, key = { it.file.absolutePath }) { item ->
                        InstagramStyleImageCard(
                            item = item,
                            isDragging = isDragging,
                            onClick = { selectedImageItem = item },
                            onLike = { toggleLike(item) },
                            onShare = { shareImage(context, item.file) },
                            onDelete = { fileToDelete = item.file },
                            modifier = Modifier
                        )
                    }
                }
            }

            // Açıklama: Sürükle-bırak mode indicator
            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.edit_mode_active),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Açıklama: Detay dialog
            selectedImageItem?.let { item ->
                InstagramStyleImageDialog(
                    item = item,
                    onDismiss = { selectedImageItem = null },
                    onDeleteRequest = { fileToDelete = it },
                    onShare = { shareImage(context, it) },
                    onLike = { toggleLike(item) }
                )
            }

            // Açıklama: Silme confirmation dialog
            fileToDelete?.let { file ->
                AlertDialog(
                    onDismissRequest = { fileToDelete = null },
                    icon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = { Text(stringResource(R.string.delete_image), fontWeight = FontWeight.Bold) },
                    text = { 
                        Text(stringResource(R.string.delete_image_confirmation)) 
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (file.delete()) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(R.string.image_deleted_success), Toast.LENGTH_SHORT).show()
                                            loadProcessedImages()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(R.string.image_delete_error), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                fileToDelete = null
                                selectedImageItem = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { 
                            Text(stringResource(R.string.delete), color = Color.White) 
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { fileToDelete = null }) { 
                            Text(stringResource(R.string.cancel)) 
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun InstagramStyleImageCard(
    item: ImageItem,
    isDragging: Boolean,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "card_scale"
    )
    
    // Açıklama: Rastgele aspect ratio Instagram tarzı
    val aspectRatio = remember { listOf(0.8f, 1f, 1.2f, 1.4f).random() }

    Card(
        modifier = modifier
            .scale(scale)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { 
                        if (isDragging) {
                            isPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = { isPressed = false }
                ) { _, _ ->
                    // Açıklama: Sürükle-bırak logic burada implementasyonlanacak
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (!isDragging) {
                    onClick()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            // Açıklama: Ana resim
            Image(
                painter = rememberAsyncImagePainter(model = item.file),
                contentDescription = stringResource(R.string.ai_result, item.file.name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))
            )
            
            // Açıklama: Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            
            // Açıklama: Sağ üst işlem butonları (sürükle modunda)
            if (isDragging) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FloatingActionButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp),
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
            
            // Açıklama: Sol alt etkileşim butonları
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Beğeni butonu
                InstagramActionButton(
                    icon = if (item.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    onClick = onLike,
                    tint = if (item.isLiked) Color.Red else Color.White
                )
                
                Text(
                    text = "${item.likeCount}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // Paylaşma butonu
                InstagramActionButton(
                    icon = Icons.Default.Share,
                    onClick = onShare,
                    tint = Color.White
                )
            }
            
            // Açıklama: Sağ alt görüntülenme sayısı
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "${item.viewCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun InstagramActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = tween(100),
        label = "button_scale"
    )
    
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .scale(scale)
            .size(32.dp)
            .background(
                Color.Black.copy(alpha = 0.4f),
                CircleShape
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isPressed = true },
                    onDragEnd = { isPressed = false }
                ) { _, _ -> }
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun InstagramStyleImageDialog(
    item: ImageItem,
    onDismiss: () -> Unit,
    onDeleteRequest: (File) -> Unit,
    onShare: (File) -> Unit,
    onLike: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    val imageUri = remember(item.file) {
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", item.file)
        } catch (e: Exception) {
            Log.e("HomeScreen", "FileProvider URI oluşturulamadı.", e)
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Açıklama: Ana resim
                if (imageUri != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageUri),
                            contentDescription = stringResource(R.string.detailed_image),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Açıklama: İstatistikler
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatisticItem(
                            icon = Icons.Default.Favorite,
                            count = item.likeCount,
                            label = stringResource(R.string.likes),
                            tint = if (item.isLiked) Color.Red else Color.White
                        )
                        StatisticItem(
                            icon = Icons.Default.Visibility,
                            count = item.viewCount,
                            label = stringResource(R.string.views)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Açıklama: Aksiyon butonları
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        // Beğeni
                        ActionDialogButton(
                            icon = if (item.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            text = if (item.isLiked) stringResource(R.string.liked) else stringResource(R.string.like),
                            onClick = onLike,
                            tint = if (item.isLiked) Color.Red else Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Paylaş
                        ActionDialogButton(
                            icon = Icons.Default.Share,
                            text = stringResource(R.string.share),
                            onClick = { onShare(item.file) },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Kaydet
                        ActionDialogButton(
                            icon = if (isSaving) Icons.Default.HourglassEmpty else Icons.Default.Download,
                            text = if (isSaving) stringResource(R.string.downloading) else stringResource(R.string.download),
                            onClick = {
                                if (isSaving) return@ActionDialogButton
                                isSaving = true
                                coroutineScope.launch {
                                    val success = saveImageToGallery(context, imageUri)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context, 
                                            if (success) context.getString(R.string.saved_to_gallery) else context.getString(R.string.save_failed), 
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Sil
                        ActionDialogButton(
                            icon = Icons.Default.Delete,
                            text = stringResource(R.string.delete),
                            onClick = { onDeleteRequest(item.file) },
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.image_not_loaded_error),
                        color = Color.White,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            
            // Açıklama: Kapatma butonu
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}

@Composable
private fun StatisticItem(
    icon: ImageVector,
    count: Int,
    label: String,
    tint: Color = Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ActionDialogButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

// Açıklama: Paylaşma fonksiyonu
private fun shareImage(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.image_share_text))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_image_title)))
    } catch (e: Exception) {
        Log.e("HomeScreen", "Paylaşma hatası", e)
        Toast.makeText(context, context.getString(R.string.share_failed_short), Toast.LENGTH_SHORT).show()
    }
}

// Açıklama: Galeriye kaydetme fonksiyonu (öncekiyle aynı)
private suspend fun saveImageToGallery(context: Context, uri: Uri): Boolean {
    return withContext(Dispatchers.IO) {
        val displayName = "AIGarage_${System.currentTimeMillis()}.png"
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "AIGarage")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var newImageUri: Uri? = null
        try {
            newImageUri = context.contentResolver.insert(imageCollection, contentValues)
                ?: return@withContext false

            context.contentResolver.openOutputStream(newImageUri)?.use { outputStream: OutputStream ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                } ?: return@withContext false
            } ?: return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(newImageUri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e("HomeScreen", "Galeriye kaydetme hatası", e)
            newImageUri?.let { context.contentResolver.delete(it, null, null) }
            false
        }
    }
}