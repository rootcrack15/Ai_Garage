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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPreviewScreen(
    navController: NavController,
    imageUri: String?,
    base64Mask: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var fileDate by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }

    // Açıklama: Dosya bilgilerini yükle
    LaunchedEffect(imageUri) {
        if (imageUri != null) {
            try {
                val uri = Uri.parse(imageUri)
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    fileDate = dateFormat.format(Date(file.lastModified()))
                    fileSize = "${(file.length() / 1024)} KB"
                    filePath = file.absolutePath
                } else {
                    fileDate = context.getString(R.string.unknown)
                    fileSize = context.getString(R.string.unknown)
                    filePath = context.getString(R.string.unknown)
                }
            } catch (e: Exception) {
                Log.e("PhotoPreviewScreen", "Dosya bilgileri yüklenemedi", e)
                fileDate = context.getString(R.string.failed_to_load)
                fileSize = context.getString(R.string.failed_to_load)
                filePath = context.getString(R.string.failed_to_load)
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = context.getString(R.string.photo_preview),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Açıklama: Tam ekran butonu
                    IconButton(
                        onClick = { 
                            showFullscreen = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = stringResource(R.string.fullscreen))
                    }
                    
                    // Açıklama: Paylaş butonu
                    IconButton(
                        onClick = { showShareDialog = true },
                        enabled = !isSharing
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                    }
                    
                    // Açıklama: Kaydet butonu
                    IconButton(
                        onClick = { showSaveDialog = true },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.save))
                    }
                    
                    // Açıklama: Sil butonu
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
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
                // Açıklama: Loading indicator
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else if (imageUri != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Açıklama: Ana resim kartı
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box {
                            Image(
                                painter = rememberAsyncImagePainter(model = imageUri),
                                contentDescription = stringResource(R.string.preview_image),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable { showFullscreen = true }
                            )
                            
                            // Açıklama: Tam ekran butonu overlay
                            FloatingActionButton(
                                onClick = { showFullscreen = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(40.dp),
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            ) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = stringResource(R.string.fullscreen),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    // Açıklama: Dosya bilgileri kartı
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.file_information),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            
                            FileInfoRow(
                                icon = Icons.Default.Schedule,
                                label = stringResource(R.string.created_date),
                                value = fileDate
                            )
                            
                            FileInfoRow(
                                icon = Icons.Default.Storage,
                                label = stringResource(R.string.file_size),
                                value = fileSize
                            )
                            
                            FileInfoRow(
                                icon = Icons.Default.Folder,
                                label = stringResource(R.string.file_path),
                                value = filePath
                            )
                        }
                    }
                    
                    // Açıklama: Aksiyon butonları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionButton(
                            icon = Icons.Default.Share,
                            text = stringResource(R.string.share),
                            onClick = { showShareDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isSharing
                        )
                        
                        ActionButton(
                            icon = Icons.Default.Download,
                            text = stringResource(R.string.save),
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isSaving
                        )
                        
                        ActionButton(
                            icon = Icons.Default.Delete,
                            text = stringResource(R.string.delete),
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                // Açıklama: Hata durumu
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Text(
                        text = stringResource(R.string.image_not_loaded),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Açıklama: Tam ekran dialog
        if (showFullscreen && imageUri != null) {
            FullscreenImageDialog(
                imageUri = imageUri,
                onDismiss = { showFullscreen = false }
            )
        }
        
        // Açıklama: Silme confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { 
                    Text(
                        stringResource(R.string.delete_image),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                text = { 
                    Text(stringResource(R.string.delete_image_confirmation)) 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (imageUri != null) {
                                try {
                                    val uri = Uri.parse(imageUri)
                                    val file = File(uri.path ?: "")
                                    if (file.delete()) {
                                        Toast.makeText(context, context.getString(R.string.image_deleted_success), Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.image_delete_error), Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("PhotoPreviewScreen", "Dosya silme hatası", e)
                                    Toast.makeText(context, context.getString(R.string.image_delete_error), Toast.LENGTH_SHORT).show()
                                }
                            }
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { 
                        Text(stringResource(R.string.delete), color = Color.White) 
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteDialog = false }) { 
                        Text(stringResource(R.string.cancel)) 
                    }
                }
            )
        }
        
        // Açıklama: Kaydetme dialog
        if (showSaveDialog && imageUri != null) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { 
                    Text(
                        stringResource(R.string.save_to_gallery),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                text = { 
                    Text(stringResource(R.string.save_to_gallery_confirmation)) 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isSaving = true
                            showSaveDialog = false
                            coroutineScope.launch {
                                val success = saveImageToGallery(context, Uri.parse(imageUri))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context, 
                                        if (success) context.getString(R.string.saved_to_gallery) else context.getString(R.string.save_failed), 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isSaving = false
                                }
                            }
                        }
                    ) { 
                        Text(stringResource(R.string.save)) 
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showSaveDialog = false }) { 
                        Text(stringResource(R.string.cancel)) 
                    }
                }
            )
        }
        
        // Açıklama: Paylaşma dialog
        if (showShareDialog && imageUri != null) {
            AlertDialog(
                onDismissRequest = { showShareDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { 
                    Text(
                        stringResource(R.string.share_image),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                text = { 
                    Text(stringResource(R.string.share_image_confirmation)) 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isSharing = true
                            showShareDialog = false
                            shareImage(context, Uri.parse(imageUri))
                            isSharing = false
                        }
                    ) { 
                        Text(stringResource(R.string.share)) 
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showShareDialog = false }) { 
                        Text(stringResource(R.string.cancel)) 
                    }
                }
            )
        }
    }
}

@Composable
private fun FileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "button_scale"
    )
    
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = tint.copy(alpha = if (enabled) 1f else 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun FullscreenImageDialog(
    imageUri: String,
    onDismiss: () -> Unit
) {
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
            Image(
                painter = rememberAsyncImagePainter(model = imageUri),
                contentDescription = stringResource(R.string.fullscreen_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            
            // Açıklama: Kapatma butonu
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }
        }
    }
}

// Açıklama: Galeriye kaydetme fonksiyonu
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
            Log.e("PhotoPreviewScreen", "Galeriye kaydetme hatası", e)
            newImageUri?.let { context.contentResolver.delete(it, null, null) }
            false
        }
    }
}

// Açıklama: Paylaşma fonksiyonu
private fun shareImage(context: Context, uri: Uri) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.image_share_text))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_image_title)))
    } catch (e: Exception) {
        Log.e("PhotoPreviewScreen", "Paylaşma hatası", e)
        Toast.makeText(context, context.getString(R.string.share_failed_short), Toast.LENGTH_SHORT).show()
    }
}
