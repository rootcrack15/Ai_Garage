package com.rootcrack.aigarage.components // Paket adınız farklı olabilir

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

@Composable
fun CameraScreenBottomNavBar(
    onTakePhoto: () -> Unit,
    lastCapturedPhotoUri: Uri?,
    onGalleryShortcutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        // Elemanları yatayda nasıl dağıtacağımızı buradan ayarlıyoruz.
        // Ortadaki butonun tam ortada olması ve sol/sağ elemanların buna göre konumlanması için
        // SpaceBetween kullanabiliriz ve sol tarafa bir Spacer ekleyebiliriz.
        horizontalArrangement = Arrangement.SpaceBetween, // Elemanlar arasında boşluk bırakır
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sol: Boşluk (Sanki bir ikon varmış gibi)
        // Sağdaki galeri kısayolu ile aynı boyutta bir boşluk bırakalım.
        // Galeri kısayolunun boyutu 48.dp.
        Spacer(modifier = Modifier.size(48.dp)) // Veya .width(48.dp)

        // Orta: Fotoğraf Çekme Butonu
        Box(
            modifier = Modifier
                .size(72.dp) // Butonun boyutu
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(BorderStroke(3.dp, Color.White), CircleShape)
                .padding(6.dp)
                .clickable(onClick = onTakePhoto),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        // Sağ: Son Çekilen Fotoğraf / Galeri Kısayolu
        Box(
            modifier = Modifier
                .size(48.dp) // Bu elemanın boyutu
                .clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(1.5.dp, Color.White), RoundedCornerShape(8.dp))
                .clickable(onClick = onGalleryShortcutClick),
            contentAlignment = Alignment.Center
        ) {
            if (lastCapturedPhotoUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(lastCapturedPhotoUri)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = "Son Çekilen Fotoğraf",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = "Galeri",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

