plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // Bu zaten Kotlin Compose Compiler plugin'ini içerir.
}

android {
    namespace = "com.rootcrack.aigarage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rootcrack.aigarage"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = file("../aigarage-release.keystore")
            storePassword = "Amilcakmak_15"
            keyAlias = "aigarage_key"
            keyPassword = "Amilcakmak_15"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        mlModelBinding = true
    }
    packagingOptions {
        resources {
            pickFirsts += "META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE.md"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    // AndroidX Core ve Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    // implementation(platform(libs.androidx.compose.bom)) // Yukarida zaten var, tekrarina gerek yok
    implementation("androidx.compose.foundation:foundation:1.6.7")
    // implementation("androidx.compose.foundation:foundation:1.6.0") // Yukaridaki 1.6.7 bunu kapsar, BOM yonetiyorsa tekil versiyon belirtmeye gerek olmayabilir

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.2")

    // Coil (Image Loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    implementation("androidx.camera:camera-extensions:${cameraxVersion}") // Gerekliyse

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:5.1.0")

    // Google Services & Firebase
    implementation("com.google.android.gms:play-services-ads:24.5.0")
    implementation("com.google.firebase:firebase-ai:17.0.0") {
        // Çakışan TensorFlow Lite API'sini dışla
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")

    // Kotlin Reflect (Gerekliyse)
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.20")

    // Diğer
    implementation("com.google.guava:guava:32.1.3-android") // Gerekliyse
    implementation("org.json:json:20250517") // Gerekliyse

    //Auto Mask ImagePicker - TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4") // Sürümü task-vision ile aynı yaptık

    // Opsiyonel: GPU hızlandırması için
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
    // GPU bağımlılığının TFLite çekirdek sürümü ile uyumlu olduğundan emin olun.
    // 2.17.0 çok yeni olabilir, 0.4.4 task-vision için TFLite'ın 2.9.x - 2.12.x gibi bir sürümü daha uyumlu olabilir.
    // Şimdilik 2.10.0'ı deneyebilir veya bu satırı yorumlayabilirsiniz.
    implementation("org.tensorflow:tensorflow-lite-gpu:2.10.0") // Örnek, uyumlu bir sürüm bulun veya yorumlayın

    implementation("androidx.compose.material3:material3:1.3.2")

    implementation("androidx.media3:media3-exoplayer:1.3.1") // En son sürümü kontrol edin
    implementation("androidx.media3:media3-ui:1.3.1") // ExoPlayer UI bileşenleri için

    implementation("io.coil-kt:coil-compose:2.6.0") // En son sürümü kontrol edin
    implementation("io.coil-kt:coil-gif:2.6.0") // GIF desteği için
    // Test Bağımlılıkları
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom)) // Zaten yukarida var, tekrarina gerek yok
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
