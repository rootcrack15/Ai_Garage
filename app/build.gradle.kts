// Dosya: app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // Açıklama: Kotlin Compose Compiler plugin'i artık Compose versiyonunu otomatik yönetiyor
}

android {
    namespace = "com.rootcrack.aigarage"
    compileSdk = 35 // Açıklama: Kararlı API seviyesine düşürüldü

    defaultConfig {
        applicationId = "com.rootcrack.aigarage"
        minSdk = 24
        targetSdk = 35 // Açıklama: compileSdk ile eşitlendi
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Açıklama: AdMob ID'sini build type'a göre enjekte ediyoruz
        manifestPlaceholders["admobAppId"] = project.findProperty("ADMOB_APP_ID_DEBUG") ?: ""
    }
    
    signingConfigs {
        create("release") {
            // Açıklama: Güvenlik - Keystore bilgileri artık gradle.properties'den okunuyor
            storeFile = file(project.findProperty("KEYSTORE_FILE") as String)
            storePassword = project.findProperty("KEYSTORE_PASSWORD") as String
            keyAlias = project.findProperty("KEY_ALIAS") as String
            keyPassword = project.findProperty("KEY_PASSWORD") as String
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = project.findProperty("ADMOB_APP_ID_DEBUG") ?: ""
        }
        release {
            // Açıklama: Performans - Release build için optimizasyonlar açıldı
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["admobAppId"] = project.findProperty("ADMOB_APP_ID_RELEASE") ?: ""
        }
    }
    
    compileOptions {
        // Açıklama: Modern Java API desteği için 17'ye yükseltildi
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        mlModelBinding = true
    }
    // Açıklama: composeOptions bloğu kaldırıldı - Kotlin Compose plugin otomatik yönetiyor

    packaging {
        resources {
            pickFirsts += "META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Açıklama: 16KB page size uyumluluğu için native libs alignment
        jniLibs {
            useLegacyPackaging = false
        }
    }
    
    // Açıklama: Modern API - TensorFlow Lite modellerinin sıkıştırılmasını önlemek için
    androidResources {
        noCompress += "tflite"
    }
    
    // Açıklama: Performans - APK boyutunu düşürmek için ABI split
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
}

dependencies {
    // AndroidX Core ve Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose) // Açıklama: Compose ViewModel desteği

    // Compose - Açıklama: BOM kullanarak version yönetimi optimize edildi
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation) // Açıklama: Animasyonlar için
    implementation(libs.androidx.compose.foundation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coil (Image Loading) - Açıklama: Versiyon tutarlılığı için tek sürüm
    val coilVersion = "2.7.0"
    implementation("io.coil-kt:coil-compose:$coilVersion")
    implementation("io.coil-kt:coil-gif:$coilVersion")

    // CameraX - Açıklama: Güncel kararlı sürüm kullanılıyor
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // DataStore - Açıklama: Preferences için
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Permissions - Açıklama: Modern permission handling için
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // CameraX missing dependencies - Açıklama: ListenableFuture için gerekli
    implementation("com.google.guava:guava:32.1.3-android")
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    
    // Firebase Auth - Açıklama: Google Auth için
    implementation("com.google.firebase:firebase-auth:22.3.1")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")

    // Google Services & Firebase
    implementation("com.google.android.gms:play-services-ads:24.5.0")
    implementation("com.google.firebase:firebase-ai:17.0.0") {
        // Çakışan TensorFlow Lite API'sini dışla
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }

    // TensorFlow Lite - Açıklama: Mevcut kararlı sürümler (16KB page size uyumlu)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")

    // Network & JSON - Açıklama: API çağrıları için gerekli
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    // Test Bağımlılıkları
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}