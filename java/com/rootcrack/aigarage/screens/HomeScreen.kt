package com.rootcrack.aigarage.screens

import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android // Örnek uygulama ikonu
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SportsMotorsports // Örnek uygulama ikonu
// import androidx.compose.material.icons.filled.Settings // Kullanılmıyorsa kaldırılabilir
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.ImageLoader // Coil için
import coil.compose.rememberAsyncImagePainter // Coil için
import coil.decode.GifDecoder // Coil GIF için
import coil.decode.ImageDecoderDecoder // Coil GIF için (API 28+)
import com.rootcrack.aigarage.R // Drawable ve raw kaynaklarınız için R dosyanız
import com.rootcrack.aigarage.navigation.Screen
import androidx.media3.common.MediaItem // ExoPlayer için
import androidx.media3.exoplayer.ExoPlayer // ExoPlayer için
import androidx.media3.ui.PlayerView // ExoPlayer UI için

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Transparent, // Arka plan GIF/video'nun görünmesi için
        topBar = {
            TopAppBar(
                title = { Text(text = "AI Garage") },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Filled.SportsMotorsports, // VEYA painterResource(R.drawable.your_app_logo)
                        contentDescription = "Uygulama Logosu",
                        modifier = Modifier
                            .padding(start = 12.dp, end = 15.dp)
                            .size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Camera.route) }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Kamera")
                    }
                    IconButton(onClick = {
                        Log.d("HomeScreen", "Notifications icon clicked")
                        // TODO: Bildirimler için bir rota veya işlev tanımlayın
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Bildirimler")
                    }
                    /*
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                    }
                    */
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), // Hafif saydam
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        })
    { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- Arka Plan Seçenekleri ---

            // Seçenek 1: GIF Arka Planı (Şu an aktif olan)
            // Kullanmak için drawable klasörünüze bir GIF dosyası ekleyin (örn: R.drawable.animated_background)
            // ve aşağıdaki gifResourceId'yi güncelleyin.
            val imageLoader = remember {
                ImageLoader.Builder(context)
                    .components {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()
            }
            Image(
                painter = rememberAsyncImagePainter(
                    model = R.drawable.ai_garage_gif, // << KENDİ GIF KAYNAĞINIZLA DEĞİŞTİRİN
                    imageLoader = imageLoader
                ),
                contentDescription = "Animasyonlu Arka Plan",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop // Veya FillBounds, Fit, FillHeight, FillWidth vb.
            )

            /*
            // Seçenek 2: Video Arka Planı (Yorum satırında, kullanıma hazır)
            // Kullanmak için raw klasörünüze bir video dosyası ekleyin (örn: R.raw.background_video)
            // ve aşağıdaki videoUri'yi güncelleyin.
            // Ayrıca build.gradle dosyanıza ExoPlayer bağımlılıklarını eklediğinizden emin olun.
            val exoPlayer = remember {
                ExoPlayer.Builder(context).build().apply {
                    // Örnek URI: raw klasöründen bir video için
                    val videoFileUri = "android.resource://${context.packageName}/${R.raw.your_background_video}" // << KENDİ VİDEO KAYNAĞINIZLA DEĞİŞTİRİN
                    // Örnek URI: internetten bir video için (AndroidManifest.xml'e internet izni eklemeyi unutmayın)
                    // val videoWebUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

                    val mediaItem = MediaItem.fromUri(videoFileUri) // veya videoWebUri
                    setMediaItem(mediaItem)
                    repeatMode = ExoPlayer.REPEAT_MODE_ONE // Videoyu sürekli döngüye al
                    playWhenReady = true // Hazır olduğunda otomatik oynat
                    volume = 0f // Arka plan videosu için sesi kapatabilirsiniz
                    prepare()
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    exoPlayer.release() // Kaynakları serbest bırak
                }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Oynatma kontrollerini gizle (arka plan için)
                        // Aspect ratio ayarları için PlayerView'a özel ayarlar gerekebilir
                        // veya dışındaki Box/Modifier ile yönetilebilir.
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            */

            /*
            // Seçenek 3: Statik Resim Arka Planı (Yorum satırında)
            // Kullanmak için drawable klasörünüze bir resim dosyası ekleyin (örn: R.drawable.static_background)
            // ve aşağıdaki painterResource'u güncelleyin.
            Image(
                painter = painterResource(id = R.drawable.static_background), // << KENDİ STATİK RESİM KAYNAĞINIZLA DEĞİŞTİRİN
                contentDescription = "Ana Ekran Arka Planı",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            */

            // --- Ana İçerik (Arka planın üzerinde) ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding), // Scaffold'dan gelen padding (TopAppBar vb. için)
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Hoş geldin! Yakında burada efektler, öneriler ve daha fazlası olacak!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f), // Okunabilirlik için
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), // Metin arkasına hafif saydam bir kutu
                            shape = RoundedCornerShape(12.dp) // Köşeleri biraz daha yuvarlak
                        )
                        .padding(16.dp) // İçeriğe padding
                )
            }
        }
    }
}
