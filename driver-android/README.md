# driver-android

App del conductor para la clínica de movilidad.

## Build Android

Pre-requisito: JDK 17 (Temurin/Adoptium).

```bash
gradlew.bat :app:assembleDebug
```

APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Build de tests

```bash
gradlew.bat :shared:testDebugUnitTest
gradlew.bat :app:testDebugUnitTest
```

## Build iOS

Requiere **macOS + Xcode**. Ver [iOS-SETUP.md](./iOS-SETUP.md) para el procedimiento detallado.

```bash
# Compilar el framework KMP compartido
gradlew :shared:linkDebugFrameworkIosX64
```

Salida: `shared/build/xcode-frameworks/Shared.xcframework`.

## Arquitectura

`:shared` es Kotlin Multiplatform. Targets activos:

- `androidTarget` — Android library (AAR)
- `iosX64()` — iOS Simulator (Intel Macs)
- `iosArm64()` — iOS devices (iPhone/iPad)
- `iosSimulatorArm64()` — iOS Simulator (Apple Silicon)

La UI nativa Android hoy vive en `:app/presentation/`. La migración a Compose Multiplatform (todo en `:shared/commonMain/`) está en curso; consultar `docs/plans/2026-08-24-driver-kmp-migration.md`.

### Layout

```
:app (Android thin wrapper)             :shared (KMP)
├── DriverApp.kt (init Koin)             ├── commonMain
├── MainActivity.kt                      │   ├── data/local/SessionStore.kt
├── AndroidManifest.xml                  │   ├── data/repository/  (interfaces)
├── res/                                 │   ├── di/KoinModules.kt (storage)
└── permisos runtime                     │   └── ...
                                          ├── androidMain (Settings = SharedPreferences)
                                          └── iosMain (Settings = NSUserDefaults)
```

### Stack

- Kotlin 2.0.21 + plugin `kotlin.multiplatform`
- Compose 2024.09 BOM (UI native Android actual)
- Ktor 3.0.3 (OkHttp Android, Darwin iOS)
- multiplatform-settings 1.1.1 (SharedPreferences Android, NSUserDefaults iOS)
- Koin 4.0.0 (DI)
- CameraX 1.4.1 + MLKit Barcode 17.3.0 (Android-only QR scanner)

## Spec de migración

`docs/superpowers/specs/2026-08-24-driver-kmp-migration-design.md` — el design original.

`docs/plans/2026-08-24-driver-kmp-migration.md` — el plan de implementación paso a paso.

## Estado actual (feature/driver-kmp-migration)

✅ **Completado**:
- `:shared` tiene 4 targets compilando.
- `iosMain` con Darwin engine de Ktor.
- `SessionStore` multiplatform en `:shared/commonMain` (shared con `:app`).
- `SessionDataStore` wrapper Android eliminado.
- Koin 4.0.0 con `storageModule` + `platformModule` (`expect/actual`).
- `DriverApp` con `startKoin`.

🔲 **Pendiente**:
- Migrar las 7 features Compose (login, dashboard, tripdetail, incident, profile, qrscan, navigation) a `:shared/commonMain/ui/` para que iOS pueda renderizarlas.
- Mover las interfaces de repositorio (`AuthRepository`, `DriverRepository`, `BookingRepository`) de `:app/domain/` a `:shared/commonMain/domain/`.
- Mover `KtorClientFactory` a `:shared/commonMain` con engines por source set.
- Eliminar las deps duplicadas en `:app/build.gradle.kts` que aún transitan de `:shared` con `implementation` directo.
- Implementación nativa de `ScannerQrService`/`LocationService`/`NotificationService` en iOS (Fase 7).
