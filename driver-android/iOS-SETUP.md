# Driver iOS Setup

App iOS del conductor — consume el framework `Shared` (KMP) que vive en `driver-android/shared/`.

## Pre-requisitos

- macOS 14+ (Sonoma) o macOS 15+ (Sequoia)
- Xcode 15+ con iOS 13+ SDK
- JDK 17 (`brew install --cask temurin@17` o Adoptium)
- Kotlin 2.0.21+ (lo trae Gradle, no requiere instalación manual)
- Git

## Paso 1: compilar el framework Shared

Desde la raíz del repo:

```bash
cd driver-android
./gradlew :shared:linkDebugFrameworkIosX64
```

Salida esperada: `driver-android/shared/build/xcode-frameworks/Shared.xcframework`

Si necesitás también iOS Arm64 (dispositivos) y Simulator Arm64 (Apple Silicon):

```bash
./gradlew :shared:linkDebugFrameworkIosArm64
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## Paso 2: crear el proyecto Xcode

1. Abrir Xcode → File → New → Project → iOS → App
2. Configurar:
   - Product Name: `DriverApp-iOS`
   - Bundle Identifier: `com.sitech.clinica.conductor.ios` (o lo que recomiende Apple)
   - Interface: Storyboard (lo vamos a borrar)
   - Language: Swift
3. Borrar `Main.storyboard` y el `ViewController.swift` generado.

## Paso 3: linkear `Shared.xcframework`

1. Arrastrar la carpeta `Shared.xcframework` desde Finder al Project Navigator.
2. Marcar "Copy items if needed" + target `DriverApp-iOS`.
3. Build Settings → "Frameworks, Search Paths" → no requiere cambios.
4. General → Frameworks, Libraries, and Embedded Content → Embed: "Embed Without Signing".

## Paso 4: MainViewController

Reemplazar el `ViewController.swift` por:

```swift
import SwiftUI
import UIKit
import Shared

class MainViewController: UIViewController {
    private var host: UIHostingController<AnyView>?

    override func viewDidLoad() {
        super.viewDidLoad()
        let root = DriverNavGraph()
        host = UIHostingController(rootView: AnyView(root))
        addChild(host!)
        view.addSubview(host!.view)
        host!.view.frame = view.bounds
        host!.didMove(toParent: self)
    }
}
```

(Para que esto compile, `DriverNavGraph` debe vivir en `commonMain`. Ver Fase 3 del plan de migración — actualmente el navigation graph aún vive en `:app/presentation/navigation/` en Android, hay que moverlo a `:shared/commonMain/navigation/` para que sea visible desde iOS.)

## Paso 5: Info.plist — permisos

Agregar las claves siguientes en `Info.plist` aunque los bindings actuales sean stubs en iOS (queda listo para Fase 7):

```xml
<key>NSCameraUsageDescription</key>
<string>Necesitamos la cámara para escanear el código QR del pasajero.</string>

<key>NSLocationWhenInUseUsageDescription</key>
<string>Necesitamos su ubicación para registrar el recorrido del viaje.</string>

<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>Necesitamos su ubicación para registrar el recorrido del viaje.</string>
```

## Paso 6: build & run

⌘B → ⌘R.

## Limitaciones actuales (Fase 1-5)

| Feature | Android | iOS |
|---|---|---|
| Login | ✅ | ✅ (cuando Fase 2 storage esté integrada con Koin startup en iOS) |
| Dashboard | ✅ | ❌ UI aún en `:app`, hay que migrar a `commonMain` (Fase 3) |
| Trip detail | ✅ | ❌ idem |
| Reporte de incidente | ✅ | ❌ idem |
| Profile | ✅ | ❌ idem |
| Scanner QR | ✅ CameraX+MLKit | ❌ stub (binding `ScannerQrService` retorna error) |
| GPS | ✅ FusedLocationProvider | ❌ stub (`LocationService.isAvailable() == false`) |
| Notificaciones locales | ✅ NotificationCompat | ❌ stub (no-op) |

La migración de UI Compose Multiplatform (Fase 3) es la que va a permitir que iOS muestre pantallas reales. Sin esa fase, el `Shared.framework` solo tiene data layer, y la UI queda vacía.

## Próxima fase (Fase 7)

Bindings nativos iOS:

- `ScannerQrService`: AVFoundation + Vision (`VNDetectBarcodesRequest`).
- `LocationService`: CoreLocation (`CLLocationManager`).
- `NotificationService`: UserNotifications (`UNUserNotificationCenter`).

Esas implementaciones viven en `:shared/src/iosMain/.../platform/` (stubs actuales están en `Stub*Service.kt`).

## Troubleshooting

**"Building for iOS, but the linked and embedded framework 'Shared' was built for macOS"**
→ El framework se linkeó con la SDK de macOS. Forzá iOS: en Xcode, Build Settings → "Build Active Architecture Only" = No; "Supported Platforms" = iOS.

**"Undefined symbols: _OBJC_CLASS_$_Kt..."**
→ Falta Kotlin runtime. En Build Phases → Link Binary With Libraries, agregar `libswiftFoundation` y verificar que KMP embed las librerías nativas.

**"Shared.xcframework not found"**
→ Re-ejecutar `./gradlew :shared:linkDebugFrameworkIosX64`. La ruta es relativa al working dir del comando.
