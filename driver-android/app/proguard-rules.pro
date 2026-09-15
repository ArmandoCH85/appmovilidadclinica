# ProGuard / R8 rules for Driver App (conductor)
# Stack: Koin + Ktor + kotlinx.serialization + Compose KMP + CameraX + MLKit

# --- kotlinx.serialization (tus DTOs via reflection) ---
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclasseswithmembernames class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    <fields>;
    <methods>;
}
-dontnote kotlinx.serialization.AnnotationsKt
# DTOs del shared module
-keep class com.appmovilidadclinica.driver.shared.data.remote.dto.** { *; }

# --- Ktor ---
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keepattributes Signature, InnerClasses, EnclosingMethod

# --- OkHttp / Okio (engine de Ktor en Android) ---
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Koin (DI por reflexión) ---
-keep class org.koin.** { *; }
-keepattributes *Annotation*
-keep class * extends org.koin.core.module.Module { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# --- Compose / Navigation (KMP + Androidx) ---
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-dontwarn androidx.compose.**

# --- CameraX ---
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- MLKit Barcode ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- Play Services Location ---
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --- DataStore / protobuf ---
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# --- javax.inject (usado en RepositoryImpl) ---
-keep class javax.inject.** { *; }

# --- kotlinx.coroutines / datetime ---
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.coroutines.**

# --- Kermit / multiplatform-settings ---
-keep class co.touchlab.kermit.** { *; }
-keep class com.russhwolf.settings.** { *; }
-dontwarn co.touchlab.kermit.**
