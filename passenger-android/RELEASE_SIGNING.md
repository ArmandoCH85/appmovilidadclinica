# Firma de bundles release — Passenger Android

## Por que este doc existe

Google Play Console rechaza cualquier `.aab` con el error:

> "Todos los bundles subidos deben estar firmados."

porque hasta ahora el proyecto no tenia ningun `signingConfig` configurado en `app/build.gradle.kts`. Esto se resuelve una sola vez por maquina / por desarrollador.

## Estrategia: Play App Signing

Esta app usa **Play App Signing**. Hay dos claves distintas:

| Clave | Quien la guarda | Para que sirve |
|-------|-----------------|----------------|
| **Upload key** | Nosotros | Firma el AAB que subimos a Play. |
| **App signing key** | Google | Google re-firma el AAB con esta antes de distribuirlo. |

Si perdes la upload key, Google te ayuda a resetearla. Si usaramos una sola clave propia y la perdemos, la app queda muerta para siempre.

## Setup (una sola vez por maquina)

### 1. Generar la keystore de upload

Desde la raiz del repo (`passenger-android/`), correr:

```bash
keytool -genkey -v \
  -keystore app/upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

`keytool` va a pedir dos passwords (pueden ser iguales):

- **store password**: protege la keystore completa
- **key password**: protege la clave individual (alias `upload`)

Tambien pide datos del certificador (nombre, organizacion, ciudad). Datos reales, no placeholders — quedan en el certificado.

**Respaldar `app/upload-keystore.jks` y los passwords en un lugar seguro** (gestor de passwords del equipo). Sin esto no se puede actualizar la app en el futuro.

### 2. Crear `app/keystore.properties`

Copiar la plantilla:

```bash
cp app/keystore.properties.template app/keystore.properties
```

Editar y completar:

```properties
storeFile=app/upload-keystore.jks
keyAlias=upload
storePassword=<el password que elegiste>
keyPassword=<el password que elegiste>
```

Este archivo esta en `.gitignore` — NUNCA se commitea.

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
2. Dejar que Google genere la app signing key (o subir una existente si es una migracion)
3. Pedirle a Google el **certificado de la upload key** (es el que valida que tu upload key coincide con la que Google espera)
4. En la maquina: extraer el certificado de la keystore local y comparar fingerprints

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
| Google Play: "El bundle no esta firmado con la upload key esperada" | Uploadaste con una keystore distinta a la que Google tiene registrada | Regenerar la upload key en Play Console y resubir |
| `alias not found` | `keyAlias` en `keystore.properties` no coincide con el alias de la keystore | Verificar con `keytool -list -keystore app/upload-keystore.jks` |

## Regenerar el AAB firmado

Cada vez que quieras subir un bundle nuevo:

```bash
./gradlew :app:bundleRelease
```

Y subi `app/build/outputs/bundle/release/app-release.aab` a Google Play Console.
