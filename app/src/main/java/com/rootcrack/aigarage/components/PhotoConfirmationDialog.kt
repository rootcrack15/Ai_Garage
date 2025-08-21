// PhotoConfirmationDialog.kt: Çekilen fotoğrafı onaylamak için dialog gösterir, talimat alır ve düzenleme ekranına yönlendirir. API 24+ uyumlu.

package com.rootcrack.aigarage.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rootcrack.aigarage.navigation.Screen

@Composable
fun PhotoConfirmationDialog(
    imageUri: Uri,
    navController: NavController,
    onDismissRequest: () -> Unit,
    onRetakePhoto: () -> Unit,
    onSaveAndGoToGallery: () -> Unit
) {
    val context = LocalContext.current
    var instruction by remember { mutableStateOf("") }

    val bitmap = remember(imageUri) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Fotoğrafı Onayla") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Çekilen fotoğraf",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentScale = ContentScale.Fit
                    )
                } ?: Text("Resim yüklenemedi")

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("Talimat (ör. 'arka planı yarış pisti yap')") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Rota şablonundaki yer tutucuları gerçek değerlerle değiştiriyoruz.
                    val routeWithArgs = Screen.EditPhoto.route
                        .replace("{imageUri}", Uri.encode(imageUri.toString()))
                        .replace("{instruction}", Uri.encode(instruction))
                    navController.navigate(routeWithArgs)
                    onDismissRequest()
                },
                enabled = instruction.isNotBlank() && bitmap != null
            ) {
                Text("Düzenle")
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onRetakePhoto) {
                    Text("Yeniden Çek")
                }
                Button(onClick = onSaveAndGoToGallery) {
                    Text("Kayıt Et ve Galeriye Git")
                }
            }
        }
    )
}

// PhotoConfirmationDialog.kt: Dosya sonuu