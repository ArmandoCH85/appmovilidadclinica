# iosApp — app iOS de pasajeros/cuidadores (Compose Multiplatform)

La UI **no** vive acá: vive en `passenger-android/shared/src/commonMain` y la
comparten Android e iOS. Este proyecto Xcode es solo el *contenedor* que monta
el framework `Shared` (generado por el módulo Gradle `:shared`).

## Estado

| Pieza | Estado |
|---|---|
| `shared` con Compose Multiplatform + targets iOS | ✅ hecho (rama `main`) |
| `MainViewController()` (entrada Kotlin para iOS) | ✅ hecho (`shared/src/iosMain/.../MainViewController.kt`) |
| Fuentes Swift (`iOSApp.swift`, `ContentView.swift`) e `Info.plist` | ✅ acá |
| Proyecto Xcode (`iosApp.xcodeproj`) | ⬜ **falta crearlo una vez en un Mac** (requiere Xcode) |

## Crear el proyecto Xcode (una sola vez, ~5 minutos)

1. **Xcode → File → New → Project… → iOS → App**
   - Product Name: **`iosApp`**
   - Interface: **SwiftUI**, Language: **Swift**
   - Guardar dentro de `passenger-android/iosApp/` (esta carpeta), de modo que
     quede `passenger-android/iosApp/iosApp.xcodeproj`.
   - Cuando Xcode ofrezca crear `iOSApp.swift`/`ContentView.swift`, **borralos o
     sobreescribilos**: los de este repo ya están en `iosApp/iosApp/` y son los
     que hay que usar.
2. **Bundle Identifier**: `com.sitech.clinica.empleados` (el mismo que Android),
   o el que corresponda a la cuenta de Apple. Ajustar también en
   `Configuration/Config.xcconfig`.
3. **Build Settings del target** → agregar:
   ```
   FRAMEWORK_SEARCH_PATHS = $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
   OTHER_LDFLAGS          = $(inherited) -framework Shared
   ```
   (Opcional: `Configuration/Config.xcconfig` se puede referenciar desde el
   proyecto para tomar `BUNDLE_ID` y `APP_NAME`.)
4. **Build Phases → + → New Run Script Phase**, moverlo **antes** de
   "Compile Sources", y pegar:
   ```sh
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```
   Ese task compila el framework `Shared` para la arquitectura/destino que pide
   Xcode (simulador o dispositivo) y lo deja listo para enlazar.
   - En "Output Files" agregar (para que Xcode no lo saltee):
     `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)/Shared.framework`
5. **Firmar**: en *Signing & Capabilities* elegir el Team (o dejar "Automatically
   manage signing"). Para simulador no hace falta Team.
6. **Run** ▶️.

## Notas

- Compilar iOS **requiere macOS**: en Windows/Linux el módulo `:shared` declara
  los targets pero no los compila.
- El engine HTTP en iOS es **Darwin** (`shared/src/iosMain/.../HttpClientEngineFactory.kt`)
  y la sesión se guarda en **NSUserDefaults**
  (`shared/src/iosMain/.../PlatformSettings.ios.kt`).
- Si Xcode dice `no such module 'Shared'`, casi siempre es que el Run Script del
  paso 4 no corrió o que falta el `FRAMEWORK_SEARCH_PATHS` del paso 3.

## Archivos

```
iosApp/
├── Configuration/Config.xcconfig   # TEAM_ID / BUNDLE_ID / APP_NAME
└── iosApp/
    ├── iOSApp.swift                # @main (SwiftUI)
    ├── ContentView.swift           # monta MainViewControllerKt.MainViewController()
    └── Info.plist                  # versión 0.3.0 (20260927), portrait
```
