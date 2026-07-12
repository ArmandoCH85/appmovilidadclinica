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
