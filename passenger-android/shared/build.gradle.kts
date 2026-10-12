// Modulo KMP: Android (commonMain + androidMain) e iOS (iosMain).
//
// El target iOS se declara igual que en la app del conductor
// (driver-android-current/shared): framework estatico "Shared" y engine HTTP
// Darwin en iosMain. NOTA: compilar/linkear los targets iOS requiere macOS;
// en Windows/Linux quedan declarados y el build de Android sigue funcionando.
//
// Compose Multiplatform (Etapa 1 iOS): la UI (login, sesion, home) vive en
// commonMain y la consumen las dos apps. Se expone con `api` para que la app
// Android compile contra ella.
//
// Contiene: HTTP client (Ktor), DTOs, modelos de dominio, repositorios y UI.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = libs.versions.jvmTarget.get()
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Core
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Fechas multiplataforma. `api` (no implementation): los modelos de
            // dominio exponen Instant/LocalDate, asi que la app Android tambien
            // tiene que verlos en su classpath. En JVM son los mismos tipos que
            // java.time, por eso el codigo Android existente no cambia.
            api(libs.kotlinx.datetime)

            // HTTP - Ktor API (multiplatform)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            // Logging
            implementation(libs.kermit)

            // Multiplatform Settings (Fase 4 — DataStore replacement)
            implementation(libs.multiplatform.settings)

            // Compose Multiplatform (Etapa 1 iOS). `api` para que la app Android
            // compile contra la UI que vive en este modulo.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)
        }

        androidMain.dependencies {
            // Engine HTTP nativo de Android (OkHttp)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            // Engine HTTP nativo de iOS (Darwin)
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // MapSettings: Settings en memoria para probar SessionStore sin Android.
            implementation(libs.multiplatform.settings.test)
            // MockEngine: respuestas HTTP simuladas para probar el grafo sin backend.
            implementation(libs.ktor.client.mock)
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