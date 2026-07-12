// Bloque raiz: solo declara los plugins para que Gradle los resuelva una vez
// y los modulos (app/) los apliquen sin version — evita repetir versiones.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
