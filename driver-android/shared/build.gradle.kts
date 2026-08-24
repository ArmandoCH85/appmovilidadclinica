// Modulo KMP (Fase 1 de la migracion a multiplatform).
// Target actual: Android (source set commonMain + androidMain).
// iOS target se agrega en Fase 5 (requiere macOS para validar build).
//
// Contiene: HTTP client (Ktor), DTOs, modelos de dominio, repositorios.
// Por ahora NO contiene UI (Fase 3) ni storage local (Fase 4) — el app
// Android sigue usando Retrofit/CameraX/MLKit durante la transicion.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
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
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

            // HTTP - Ktor API (multiplatform)
            implementation("io.ktor:ktor-client-core:3.0.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
            implementation("io.ktor:ktor-client-logging:3.0.3")

            // Logging
            implementation("co.touchlab:kermit:2.0.5")

            // Multiplatform Settings (Fase 4 — DataStore replacement)
            implementation("com.russhwolf:multiplatform-settings:1.1.1")
        }

        androidMain.dependencies {
            // Engine HTTP nativo de Android (OkHttp)
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
    }
}

android {
    namespace = "com.appmovilidadclinica.driver.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}