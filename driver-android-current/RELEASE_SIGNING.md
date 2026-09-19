# Firma de bundles release — Driver Android

## Por qué este doc existe

Google Play Console rechaza cualquier `.aab` con el error:

> "Todos los bundles subidos deben estar firmados."

porque hasta ahora el proyecto no tenía ningún `signingConfig` configurado en `app/build.gradle.kts`. Esto se resuelve una sola vez por máquina / por desarrollador.

## Estrategia: Play App Signing

Esta app usa **Play App Signing**. Hay dos claves distintas:

| Clave | Quién la guarda | Para qué sirve |
|-------|-----------------|----------------|
| **Upload key** | Nosotros | Firma el AAB que subimos a Play. |
| **App signing key** | Google | Google re-firma el AAB con esta antes de distribuirlo. |

Si perdés la upload key, Google te ayuda a resetearla. Si usáramos una sola clave propia y la perdemos, la app queda muerta para siempre.

## Setup (una sola vez por máquina)

### 1. Generar la keystore de upload

Desde la raíz del repo (`driver-android/`), correr:

```bash
keytool -genkey -v \
  -keystore app/upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

`keytool` va a pedir dos passwords (pueden ser iguales):

- **store password**: protege la keystore completa
- **key password**: protege la clave individual (alias `upload`)

También pide datos del certificador (nombre, organización, ciudad). Datos reales, no placeholders — quedan en el certificado.

**Respaldar `app/upload-keystore.jks` y los passwords en un lugar seguro** (gestor de passwords del equipo). Sin esto no se puede actualizar la app en el futuro.

### 2. Crear `app/keystore.properties`

Copiar la plantilla:

```bash
cp app/keystore.properties.template app/keystore.properties
```

Editar y completar:

```properties
storeFile=upload-keystore.jks
keyAlias=upload
storePassword=<el password que elegiste>
keyPassword=<el password que elegiste>
```

Este archivo está en `.gitignore` — NUNCA se commitea.

### 3. Verificar que el bundle se firma

```bash
./gradlew :app:bundleRelease
```

El AAB queda en `app/build/outputs/bundle/release/app-release.aab`.

Verificar la firma:

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

Tiene que decir "jar verified" y listar el alias `upload` con el certificado que generaste.

## Subir a Google Play por primera vez

Antes de subir el primer bundle, hay que **opt-in a Play App Signing** desde Google Play Console:

1. Play Console → Tu app → **Setup** → **App signing**
2. Dejar que Google genere la app signing key (o subir una existente si es una migración)
3. Pedirle a Google el **certificado de la upload key** (es el que valida que tu upload key coincide con la que Google espera)
4. En la máquina: extraer el certificado de la keystore local y comparar fingerprints

Comando para ver el SHA-256 del certificado de upload:

```bash
keytool -list -v -keystore app/upload-keystore.jks -alias upload
```

Subir ese fingerprint a Play Console cuando lo pida.

## Si algo falla

| Error | Causa | Fix |
|-------|-------|-----|
| `Keystore was tampered with, or password was incorrect` | Password mal en `keystore.properties` | Verificar los passwords |
| `Keystore file not set for signing config release` | `storeFile` no apunta a un archivo existente | Verificar la ruta relativa |
| Google Play: "El bundle no está firmado con la upload key esperada" | Subiste con una keystore distinta a la que Google tiene registrada | Regenerar la upload key en Play Console y resubir |
| `alias not found` | `keyAlias` en `keystore.properties` no coincide con el alias de la keystore | Verificar con `keytool -list -keystore app/upload-keystore.jks` |
| "Tu aplicación no admite tamaños de página de memoria de 16 kB" | Alguna `.so` native compilada con alineamiento 4 KB | Ver `16KB_PAGES.md` o actualizar librerías con native code (CameraX 1.3.x → 1.4.x) |

## Regenerar el AAB firmado

Cada vez que quieras subir un bundle nuevo:

```bash
./gradlew :app:bundleRelease
```

Y subí `app/build/outputs/bundle/release/app-release.aab` a Google Play Console.
