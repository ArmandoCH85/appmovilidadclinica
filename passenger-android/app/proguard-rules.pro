# Reglas minimas — Retrofit/OkHttp/Room/Hilt ya traen sus propias reglas
# consumer-proguard empaquetadas en sus AARs, no hace falta repetirlas aca.

# kotlinx.serialization necesita conservar los @Serializable de los DTOs
# para reflection de metadata en release (R8 agresivo puede romper esto).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.appmovilidadclinica.passenger.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.appmovilidadclinica.passenger.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# === Hilt / Dagger ===
# Hilt requiere que se conserven las clases anotadas con @AndroidEntryPoint,
# @HiltAndroidApp, @HiltViewModel y los EntryPoint/AssistedFactory.
# Sin esto, el AAB release crashea al abrir la Activity (ClassNotFoundException
# o NoSuchMethodError en runtime).
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }
-keep,allowobfuscation,allowshrinking class * extends androidx.lifecycle.ViewModel
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class com.appmovilidadclinica.passenger.PassengerApp { *; }
-keep class com.appmovilidadclinica.passenger.MainActivity { *; }

# === Ktor / Ktor Multiplatform ===
# El expect/actual de httpClientEngineFactory() se rompe en R8 sin reglas
# explicitas. Conservamos las factories y engines.
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.client.** { *; }
-keep class io.ktor.utils.io.** { *; }
-keep class kotlinx.coroutines.** { *; }

# === Kotlin Coroutines + Flow ===
# Flow y StateFlow necesitan sus Companion para no romperse.
-keepclassmembers class kotlinx.coroutines.flow.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# === Kermit ===
-keep class co.touchlab.kermit.** { *; }

# === Multiplatform Settings ===
# Settings store usa reflection en Android para SharedPreferences.
-keep class com.russhwolf.settings.** { *; }
