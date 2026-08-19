// Modulo KMP (Fase 1 de la migracion a multiplatform).
// Target actual: Android (source set commonMain + androidMain).
// iOS target se agrega en Fase 5 (requiere macOS para validar build).
//
// Contiene: HTTP client (Ktor), DTOs, modelos de dominio, repositorios.
// Por ahora NO contiene UI (Fase 3) ni storage local (Fase 4) — el app
// Android sigue usando Retrofit/Room/DataStore durante la transicion.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = libs.versions.jvmTarget.get()
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Core
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // HTTP - Ktor API (multiplatform)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            // Logging
            implementation(libs.kermit)
        }

        androidMain.dependencies {
            // Engine HTTP nativo de Android (OkHttp)
            implementation(libs.ktor.client.okhttp)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.appmovilidadclinica.passenger.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}