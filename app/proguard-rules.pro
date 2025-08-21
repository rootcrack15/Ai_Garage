# Dosya: app/proguard-rules.pro
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Açıklama: Kotlin ve Compose için gerekli keep kuralları
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Compose specific rules
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class androidx.compose.** { *; }

# Navigation Component
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keep class org.tensorflow.lite.task.** { *; }
-dontwarn org.tensorflow.lite.task.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Açıklama: Model sınıfları için genel kural
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Açıklama: Enum sınıfları korunuyor
-keepclassmembers enum * { *; }

# Açıklama: Parcelable sınıfları
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Açıklama: ViewModel sınıfları
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Açıklama: Repository sınıfları
-keep class *.repository.** { *; }

# Açıklama: Crash raporları için stack trace korunuyor
-keepattributes SourceFile,LineNumberTable
-keep class com.rootcrack.aigarage.** { *; }