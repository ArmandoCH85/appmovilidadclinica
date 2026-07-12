# Desarrollo Módulo Conductor — App Android

## Índice

1. [Gaps de Backend](#1-gaps-de-backend)
   - [1.1 Campos de licencia no expuestos](#11-campos-de-licencia-no-expuestos)
   - [1.2 Licencias en login response (alternativa a /driver/profile)](#12-licencias-en-login-response-alternativa-a-driverprofile)
   - [1.3 verify-qr sin guard de rol](#13-verify-qr-sin-guard-de-rol)
2. [SDD — Propuesta: App Android Módulo Conductor](#2-sdd--propuesta-app-android-módulo-conductor)
   - [2.1 Contexto y alcance](#21-contexto-y-alcance)
   - [2.2 Stack tecnológico](#22-stack-tecnológico)
   - [2.3 Gaps de backend detectados](#23-gaps-de-backend-detectados)
3. [SDD — Specs](#3-sdd--specs)
   - [3.1 Autenticación](#31-autenticación)
   - [3.2 Dashboard del día](#32-dashboard-del-día)
   - [3.3 Detalle de viaje y lista de pasajeros](#33-detalle-de-viaje-y-lista-de-pasajeros)
   - [3.4 Cronograma de paradas y marcación de llegada](#34-cronograma-de-paradas-y-marcación-de-llegada)
   - [3.5 Escaneo QR](#35-escaneo-qr)
   - [3.6 Reporte de incidencias](#36-reporte-de-incidencias)
   - [3.7 Perfil y sesión](#37-perfil-y-sesión)
4. [SDD — Diseño Técnico](#4-sdd--diseño-técnico)
   - [4.1 Stack tecnológico detallado](#41-stack-tecnológico-detallado)
   - [4.2 Arquitectura de capas](#42-arquitectura-de-capas)
   - [4.3 Manejo de errores](#43-manejo-de-errores)
   - [4.4 Autenticación — interceptor + expiración proactiva](#44-autenticación--interceptor--expiracion-proactiva)
   - [4.5 Flujo de datos por feature](#45-flujo-de-datos-por-feature)
   - [4.6 Retrofit API interfaces](#46-retrofit-api-interfaces)
   - [4.7 DTOs vs Domain Models](#47-dtos-vs-domain-models)
   - [4.8 Navegación](#48-navegación)
   - [4.9 Configuración de red](#49-configuración-de-red)
   - [4.10 Permisos Android](#410-permisos-android)
   - [4.11 Estrategia offline](#411-estrategia-offline)
5. [APIs del Backend — Referencia para la App](#5-apis-del-backend--referencia-para-la-app)
   - [5.1 Autenticación](#51-autenticación)
   - [5.2 Endpoints del módulo Driver](#52-endpoints-del-módulo-driver)
   - [5.3 Endpoints del módulo Booking](#53-endpoints-del-módulo-booking)
   - [5.4 Estructuras de datos](#54-estructuras-de-datos)
   - [5.5 Mapa de estados](#55-mapa-de-estados)
   - [5.6 Códigos de error HTTP](#56-códigos-de-error-http)

---

## 1. Gaps de Backend

### 1.1 Campos de licencia no expuestos

**Problema**: La tabla `users` tiene `driver_license_number`, `driver_license_category`, `driver_license_expires_on` pero ningún struct Go los incluye ni hay queries que los lean/escriban. El seed data ya los carga para el conductor demo (user_id=2).

**Archivos a modificar:**

#### `backend/internal/modules/admin/repository.go`

**Struct `User` (línea 59):** Agregar:
```go
DriverLicenseNumber     *string `json:"driver_license_number,omitempty"`
DriverLicenseCategory   *string `json:"driver_license_category,omitempty"`
DriverLicenseExpiresOn  *string `json:"driver_license_expires_on,omitempty"`
```

**Struct `UserCreateParams` (línea 74):** Agregar (todos opcionales):
```go
DriverLicenseNumber     *string `json:"driver_license_number,omitempty" validate:"omitempty,max=50"`
DriverLicenseCategory   *string `json:"driver_license_category,omitempty" validate:"omitempty,max=20"`
DriverLicenseExpiresOn  *string `json:"driver_license_expires_on,omitempty" validate:"omitempty"`
```

**Struct `UserUpdateParams` (línea 89):** Mismos 3 campos que CreateParams.

**ListUsers SELECT (línea 690):** Cambiar de:
```sql
SELECT id, employee_code, document_number, full_name, role,
       department, phone, preferred_stop_id, active
```
a:
```sql
SELECT id, employee_code, document_number, full_name, role,
       department, phone, preferred_stop_id,
       driver_license_number, driver_license_category, driver_license_expires_on,
       active
```

**ListUsers Scan (línea 706):** Agregar 3 `sql.NullString` para los nuevos campos y asignarlos vía `nullableStr()`.

**CreateUser INSERT (línea 730):** Cambiar de:
```sql
INSERT INTO users (employee_code, document_number, password_hash, full_name,
       role, department, phone, preferred_stop_id, active)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
```
a:
```sql
INSERT INTO users (employee_code, document_number, password_hash, full_name,
       role, department, phone, preferred_stop_id,
       driver_license_number, driver_license_category, driver_license_expires_on,
       active)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
```

**CreateUser ExecArgs (línea 733):** Agregar `p.DriverLicenseNumber, p.DriverLicenseCategory, p.DriverLicenseExpiresOn`.

**CreateUser return (línea 742):** Agregar los 3 campos al `User{}`.

**UpdateUser — sin password (línea 756):** Agregar al SET:
```sql
driver_license_number = ?, driver_license_category = ?, driver_license_expires_on = ?,
```

**UpdateUser — con password (línea 763):** Mismo cambio en el SET.

#### `backend/internal/modules/admin/service.go`

Sin cambios de lógica — los campos pasan directamente al repo (no se hashean como password). Solo asegurarse que `CreateUser` y `UpdateUser` hagan passthrough.

#### `backend/internal/modules/admin/service_test.go`

Actualizar `mockAdminRepo` para que los stubs acepten los nuevos campos.

---

### 1.2 Licencias en login response (alternativa a /driver/profile)

**Problema**: el conductor necesita ver sus datos de licencia en el perfil. Pero ya tiene `full_name`, `employee_code`, `role` en el JWT, y `department`, `phone` en el login response. Crear un endpoint entero (`/driver/profile`) para sumar solo 3 campos de licencia es over-engineering.

**Solución minimal**: agregar los campos de licencia al struct `auth.User` que ya se devuelve en `POST /api/auth/login`. El conductor recibe los datos de licencia en el login sin round-trip extra. `today_trip_count` se obtiene de `dashboard.trips.size()` — no necesita endpoint.

#### `backend/internal/modules/auth/repository.go`

**Struct `User` (línea 16):** Agregar:
```go
DriverLicenseNumber     *string `json:"driver_license_number,omitempty"`
DriverLicenseCategory   *string `json:"driver_license_category,omitempty"`
DriverLicenseExpiresOn  *string `json:"driver_license_expires_on,omitempty"`
```

**GetUserByDocument SELECT (línea 49):** Agregar al SELECT:
```sql
driver_license_number, driver_license_category, driver_license_expires_on
```

**GetUserByDocument Scan (línea 59):** Agregar 3 `sql.NullString` y asignar vía `nullableStr()`.

---

### 1.3 verify-qr sin guard de rol

**Problema**: `POST /api/reservations/verify-qr` no valida el rol del caller. Cualquier JWT válido (WORKER incluido) puede verificar un QR ajeno.

#### `backend/internal/modules/booking/service.go`

En `VerifyQR` (línea 99), agregar al inicio:
```go
role, err := authctx.RoleFromContext(ctx)
if err != nil {
    return Reservation{}, apperror.UnauthorizedError{Reason: "token sin rol"}
}
if role != "DRIVER" && role != "ADMIN" {
    return Reservation{}, apperror.ForbiddenError{Reason: "solo el rol DRIVER o ADMIN pueden verificar QR"}
}
```

---

## 2. SDD — Propuesta: App Android Módulo Conductor

### 2.1 Contexto y alcance

**Contexto**
El sistema de transporte corporativo (appmovilidadclinica) tiene backend Go completo (auth/trips/booking/driver/admin), panel web admin, y app de pasajero (WORKER) en desarrollo. Falta la app móvil para el rol DRIVER.

**Alcance (in scope)**
1. **Auth**: login con document_number + password (mismo endpoint `/api/auth/login`). JWT 24h sin refresh. Decodificar `exp` localmente para aviso proactivo de expiración. Logout manual.
2. **Dashboard del día**: `GET /api/driver/trips?date=` — lista de viajes asignados del día ordenados por hora, con datos de ruta, vehículo, placa, estado.
3. **Lista de pasajeros por viaje**: `GET /api/driver/trips/{id}/passengers` — pasajeros CONFIRMED/BOARDED con nombre, asiento, parada de subida/bajada, estado.
4. **Escaneo QR**: cámara + lector QR (CameraX + ML Kit). El pasajero muestra su QR → `POST /api/reservations/verify-qr` → acciones board/no-show/alight.
5. **Marcación de paradas**: `POST /api/driver/trip-stops/{id}/arrival` — marcar llegada a cada parada del recorrido.
6. **Gestión de pasajeros**: board (`POST /driver/reservations/{id}/board`), no-show (`POST /driver/reservations/{id}/no-show`), alight (`POST /driver/reservations/{id}/alight`).
7. **Reporte de incidencias**: `POST /api/driver/trips/{id}/incidents` — tipo (BREAKDOWN/DELAY/ACCIDENT/OTHER) + descripción.
8. **Perfil del conductor**: datos de sesión (login response) + licencias en el login. `today_trip_count` se deriva del dashboard.

**Fuera de alcance (explícito)**
- Modo offline / sincronización diferida
- Pantallas de administración
- Multi-idioma
- GPS / tracking en tiempo real
- Edición de perfil

### 2.2 Stack tecnológico

| Capa | Elección | Por qué |
|---|---|---|
| Lenguaje | Kotlin 2.0+ | Estándar Android nativo moderno |
| UI | Jetpack Compose + Material 3 | Declarativo, Google recomienda para proyectos nuevos |
| Arquitectura | Clean Architecture + MVVM | Separa reglas de negocio de framework Android |
| DI | Hilt | Estándar de facto sobre Dagger |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Retrofit estándar; kotlinx.serialization 100% Kotlin |
| Async | Coroutines + Flow | Estándar Kotlin |
| Persistencia local | DataStore (Preferences) | Solo token/sesión; el conductor no persiste QR |
| QR Scanning | CameraX + ML Kit Barcode Scanning | Escaneo en vivo oficial de Google |
| Navegación | Navigation Compose | Estándar apps Compose |
| Fecha/hora | java.time (desugaring nativo API 26+) | Min SDK 26 ya trae java.time completo, cero dependencias extra |
| Testing | JUnit5 + MockK + Turbine | Estándar Kotlin moderno |
| Min SDK | API 26 (Android 8.0) | Cobertura >95% |

### 2.3 Gaps de backend detectados

1. **Campos de licencia no expuestos** — `driver_license_number`, `driver_license_category`, `driver_license_expires_on` existen en BD pero no en structs Go (admin CRUD + auth login response)
2. **verify-qr sin guard de rol** — cualquier JWT válido puede verificar QR ajeno

---

## 3. SDD — Specs

### 3.1 Autenticación

- El conductor ingresa `document_number` + `password`, la app llama `POST /api/auth/login`
- Éxito (200): persistir `token` + datos de `user` en DataStore. Navegar a Dashboard
- Rol distinto de DRIVER (ADMIN/WORKER): permitir login (backend no distingue en `/login`) pero bloquear navegación con mensaje "Esta app es para conductores"
- Error 401: "Documento o contraseña incorrectos"
- Sesión: JWT 24h sin refresh. Decodificar `exp` localmente para countdown T-2min. Al expirar: logout forzado + navegación a Login con mensaje "Sesión expirada"
- Logout manual desde el perfil

#### Escenarios

**Login exitoso**: document_number + password correctos → 200 → guardar token + user → navegar a Dashboard
**Login fallido**: credenciales inválidas → 401 → mensaje genérico
**Rol incorrecto**: login con ADMIN o WORKER → login OK (backend lo permite) → bloqueo con mensaje
**Expiración de sesión**: token expira → banner T-2min → logout forzado → Login con mensaje

### 3.2 Dashboard del día

- `GET /api/driver/trips?date=YYYY-MM-DD` (default today)
- Loading state mientras carga
- Sin resultados (`[]`): estado vacío "No tienes viajes asignados para hoy"
- Cada tarjeta de viaje muestra:
  - `route_name` + `direction` (IDA/VUELTA)
  - `trip_code`
  - `scheduled_start_at` - `scheduled_end_at` (formato hora)
  - `vehicle_code` + `plate`
  - `seat_capacity_snapshot`
  - `status` con badge de color: DRAFT (gris), PUBLISHED (azul), BOARDING (naranja), IN_PROGRESS (verde), COMPLETED (gris oscuro), CANCELLED (rojo tachado)
  - Total de pasajeros CONFIRMED/BOARDED
- Ordenado por `scheduled_start_at` ASC. CANCELLED al final con menos énfasis
- Selector de fecha + flechas "día anterior / día siguiente"
- Pull-to-refresh

#### Escenarios

**Carga exitosa con datos**: GET 200 → tarjetas de viaje visibles, ordenadas por hora
**Sin viajes**: GET 200 `[]` → estado vacío "No tienes viajes asignados para hoy"
**Error de red**: GET falla → mensaje "Sin conexión" + botón reintentar
**Cambio de fecha**: seleccionar otra fecha → recargar lista

### 3.3 Detalle de viaje y lista de pasajeros

- Al tocar una tarjeta: navegar a detalle que carga `GET /api/driver/trips/{id}/passengers`
- Cabecera del viaje: `trip_code`, `route_name`, dirección, horarios, vehículo
- Lista de pasajeros agrupada por `origin_stop_order` ascendente
- Cada pasajero: `worker_full_name`, `seat_label`, `origin_stop_name` → `destination_stop_name`, `status`, tiempo desde `confirmed_at`/`boarded_at`
- Sin pasajeros: estado vacío "No hay pasajeros en este viaje"
- Al tocar pasajero CONFIRMED: acciones Board o No-Show
- Al tocar pasajero BOARDED: acción Alight

#### Escenarios

**Carga exitosa**: GET 200 → lista de pasajeros con nombre, asiento, paradas
**Sin pasajeros**: GET 200 `[]` → "No hay pasajeros en este viaje"
**Board exitoso**: POST 204 → toast "Pasajero abordado" → actualizar status en lista
**No-Show exitoso**: POST 204 → toast "No presentado registrado"
**Alight exitoso**: POST 204 → toast "Bajada registrada"
**Error 409**: estado incorrecto → mostrar mensaje del backend tal cual

### 3.4 Cronograma de paradas y marcación de llegada

- Desde el detalle del viaje, el conductor ve el cronograma completo de paradas
- Cada parada: `stop_name`, `stop_order`, `scheduled_arrival_at`, `scheduled_departure_at`, `status` (PENDING/ARRIVED/DEPARTED/SKIPPED)
- Botón "Marcar llegada" en paradas PENDING
- Confirmación con diálogo antes de ejecutar
- Éxito (204): actualizar estado local a ARRIVED, ocultar botón

#### Escenarios

**Marcación exitosa**: POST 204 → parada marcada, botón deshabilitado
**Parada ya marcada**: parada con status ARRIVED → botón oculto, hora real visible
**Error 403**: conductor no asignado → mensaje de error

### 3.5 Escaneo QR

- Botón flotante "Escanear QR" desde el detalle del viaje
- Abre cámara en modo escaneo QR (CameraX + ML Kit, portrait)
- Al detectar QR: feedback háptico, llama `POST /api/reservations/verify-qr`
- Éxito: overlay con datos: `worker_full_name`, `reservation_code`, `origin_stop_name` → `destination_stop_name`, `seat_label`, `status`
- Error 404: toast "QR inválido", cámara sigue abierta
- Acciones post-scan: Board / No-Show / Alight según status

#### Escenarios

**Escaneo exitoso + Board**: QR detectado → verify-qr OK → overlay → Board → 204 → toast
**Escaneo exitoso + No-Show**: QR detectado → verify-qr OK → No-Show → 204 → toast
**QR inválido**: QR detectado → verify-qr 404 → toast, cámara sigue
**Cámara sin permiso**: solicitar permiso → si denegado, mostrar rationale con botón de configuración

### 3.6 Reporte de incidencias

- Botón "Reportar incidencia" desde el detalle del viaje
- Formulario: `incident_type` (selector con 4 opciones + icono) + `description` (multilínea, max 1000, contador)
- Validación client-side: tipo requerido, descripción no vacía ≤ 1000 chars
- Confirmación antes de enviar
- Éxito (201): toast "Incidencia reportada (#N)", limpiar formulario, volver al detalle

#### Escenarios

**Reporte exitoso**: formulario completo → 201 → toast de confirmación
**Validación falla**: campo vacío o muy largo → error local, no submit
**Error 422**: backend rechaza → mostrar error del backend

### 3.7 Perfil y sesión

- Pantalla de perfil desde menú del dashboard
- Datos desde almacenamiento local (login response + claims JWT + dashboard local): `full_name`, `employee_code`, `role`, `department`, `phone`, `driver_license_number`, `driver_license_category`, `driver_license_expires_on`, `today_trip_count`
- Versión de la app
- Botón "Cerrar sesión" con confirmación → limpiar DataStore → navegar a Login

#### Escenarios

**Perfil cargado**: datos de login response + claims → perfil completo visible
**Cerrar sesión**: confirmar → limpiar sesión → Login

---

## 4. SDD — Diseño Técnico

### 4.1 Stack tecnológico detallado

| Capa | Elección | Por qué |
|---|---|---|
| Lenguaje | Kotlin 2.0+ | Estándar Android nativo moderno |
| UI | Jetpack Compose + Material 3 | Declarativo, Google recomienda para proyectos nuevos, evita XML/View system legado |
| Arquitectura | Clean Architecture (domain/data/presentation) + MVVM, **sin capa usecase** | ViewModel → Repository directo. La capa usecase era YAGNI (todos los casos de uso eran wrappers de un método). Se ahorran ~9 archivos. |
| DI | Hilt | Estándar de facto sobre Dagger para Android, integra directo con ViewModel/Compose |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Retrofit es el estándar; kotlinx.serialization en vez de Gson/Moshi porque el proyecto es 100% Kotlin y evita reflection |
| Async | Coroutines + Flow | Estándar Kotlin, reemplaza callbacks/RxJava |
| Persistencia local | DataStore (Preferences) para token/sesión | El conductor no necesita persistir QR (solo escanea, no guarda tokens como el pasajero). Room se omite (YAGNI). |
| QR Scanning | CameraX + ML Kit Barcode Scanning (`com.google.mlkit:barcode-scanning`) | ML Kit es la solución oficial de Google para escaneo en vivo sin cámara propia. Mejor que ZXing para escaneo en tiempo real con preview de cámara. No se necesita generación de QR (solo lectura). |
| Navegación | Navigation Compose | Estándar para apps 100% Compose |
| Fecha/hora | java.time (`java.time.Instant`, `LocalDate`, `DateTimeFormatter`) | Min SDK API 26 = java.time completo sin desugaring. Cero dependencias extra. Reemplaza kotlinx-datetime (stdlib). |
| Testing | JUnit5 + MockK + Turbine (para Flow) | Estándar Kotlin moderno |
| Min SDK | API 26 (Android 8.0) | Cobertura >95% del parque activo real en 2026 |

### 4.2 Arquitectura de capas

```
presentation/                → Compose UI + ViewModel (por feature)
  ├─ login/
  │   └─ LoginViewModel.kt
  ├─ dashboard/              → Lista de viajes del día
  │   └─ DashboardViewModel.kt
  ├─ tripdetail/             → Detalle del viaje + pasajeros + cronograma
  │   └─ TripDetailViewModel.kt
  ├─ qrscan/                 → Cámara + escaneo + acciones post-scan
  │   └─ QrScanViewModel.kt
  ├─ incident/               → Reporte de incidencia
  │   └─ IncidentViewModel.kt
  ├─ profile/                → Perfil y ajustes
  └─ common/                 → Componentes compartidos, tema, navegación raíz

domain/                      → Kotlin puro, CERO dependencia Android/Retrofit
  ├─ model/                  → DriverTrip, Passenger, TripStop, Reservation, Incident, DriverProfile
  └─ repository/             → Interfaces: AuthRepository, DriverRepository

data/                        → Implementación concreta
  ├─ remote/
  │   ├─ AuthApi, DriverApi, BookingApi   → interfaces Retrofit
  │   ├─ dto/                             → shapes EXACTOS de respuesta JSON del backend
  │   └─ AuthInterceptor                  → inyecta "Authorization: Bearer <token>"
  ├─ local/
  │   └─ SessionDataStore                 → token + user + exp del JWT
  ├─ mapper/                              → DTO → domain model
  └─ repository/                          → implementación de interfaces domain

di/                          → Módulos Hilt (NetworkModule, DatabaseModule, RepositoryModule)
```

**Regla de dependencia**: `presentation` conoce `domain`. `data` conoce `domain`. `domain` no conoce a nadie (ni Retrofit, ni DataStore, ni Android SDK). Los ViewModels de `presentation` invocan `repository` directo (no hay capa usecase).

### 4.3 Manejo de errores

Todas las llamadas de red devuelven `Result<T, AppError>` (sealed class propia, no excepciones crudas hacia el ViewModel):

```kotlin
sealed class AppError {
    data class Unauthorized(val message: String) : AppError()      // 401 → forzar logout
    data class Forbidden(val message: String) : AppError()          // 403 → no asignado
    data class NotFound(val message: String) : AppError()           // 404
    data class Conflict(val message: String) : AppError()           // 409 → mostrar tal cual
    data class Validation(val field: String?, val message: String) : AppError()  // 422
    data class Network(val message: String) : AppError()            // sin conexión / timeout
    data class Unknown(val message: String) : AppError()            // 500 / no mapeado
}
```

Un único `DriverApiErrorMapper` en `data/remote/` traduce la respuesta `{"error":{"code","message"}}` del backend a este sealed class, mismo patrón que el panel admin y la app pasajero.

### 4.4 Autenticación — interceptor + expiración proactiva

- `AuthInterceptor` (OkHttp interceptor): lee token de `SessionDataStore` en cada request, agrega `Authorization: Bearer <token>`. Si no hay token, la request sale sin header (rutas públicas: login/health).
- Al recibir 401: el interceptor emite evento global (`SharedFlow<SessionExpiredEvent>`) que la UI raíz observa para forzar logout + navegación a Login — mismo patrón que el panel admin.
- Decodificación local del JWT (solo el payload, sin verificar firma) para leer `exp` y mostrar countdown/aviso T-2min.

### 4.5 Flujo de datos por feature

**Dashboard**: `DashboardViewModel` → `DriverRepository.getTrips(date)` → `DriverApi.getTrips(date)` (GET /api/driver/trips?date=) → mapper DTO→domain → StateFlow.

**Pasajeros**: `TripDetailViewModel` → `DriverRepository.getPassengers(tripId)` → `DriverApi.getPassengers(tripId)` → StateFlow.

**Marcar llegada**: `TripDetailViewModel` → `DriverRepository.markArrival(tripStopTimeId)` → `DriverApi.markArrival(tripStopTimeId)` → 204 → actualizar estado local de la parada.

**Escaneo QR**: CameraX + ML Kit detectan código → callback con string token → `QrScanViewModel` → `BookingApi.verifyQr(token)` → `Reservation` → ViewModel expone el resultado. Acciones posteriores (board/no-show/alight) son llamadas separadas a `DriverApi`.

**Board/No-Show/Alight**: `TripDetailViewModel` → `DriverRepository.{markBoarded,markNoShow,markAlighted}()` → `DriverApi.{board,noShow,alight}(reservationId)` → 204 → refrescar lista de pasajeros.

**Incidencia**: `IncidentViewModel` → `DriverRepository.reportIncident(tripId, type, description)` → `DriverApi.reportIncident(tripId, body)` → 201 `{id}`.

**Perfil**: `ProfileViewModel` lee datos de `SessionDataStore` (login response persistido) + cuenta viajes del dashboard local. Sin llamada de red.

### 4.6 Retrofit API interfaces

**DriverApi no tiene `/driver/profile`** — el perfil se sirve desde datos locales (login response + claims JWT).

```kotlin
// data/remote/DriverApi.kt
interface DriverApi {
    @GET("driver/trips")
    suspend fun getTrips(@Query("date") date: String): List<DriverTripDto>

    @GET("driver/trips/{id}/passengers")
    suspend fun getPassengers(@Path("id") tripId: Long): List<PassengerDto>

    @POST("driver/trip-stops/{id}/arrival")
    suspend fun markArrival(@Path("id") tripStopTimeId: Long): Response<Unit>  // 204

    @POST("driver/reservations/{id}/board")
    suspend fun boardPassenger(@Path("id") reservationId: Long): Response<Unit>  // 204

    @POST("driver/reservations/{id}/no-show")
    suspend fun markNoShow(@Path("id") reservationId: Long): Response<Unit>  // 204

    @POST("driver/reservations/{id}/alight")
    suspend fun alightPassenger(@Path("id") reservationId: Long): Response<Unit>  // 204

    @POST("driver/trips/{id}/incidents")
    suspend fun reportIncident(@Path("id") tripId: Long, @Body body: IncidentRequestDto): IncidentResponseDto  // 201
}

// data/remote/BookingApi.kt
interface BookingApi {
    @POST("reservations/verify-qr")
    suspend fun verifyQr(@Body body: VerifyQrRequestDto): ReservationDto
}

// data/remote/AuthApi.kt
interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @GET("me")
    suspend fun me(): MeResponseDto
}
```

### 4.7 DTOs vs Domain Models

```kotlin
// DTO — replica exacta del JSON del backend
@Serializable
data class DriverTripDto(
    val id: Long,
    @SerialName("trip_code") val tripCode: String,
    @SerialName("route_id") val routeId: Long,
    @SerialName("route_code") val routeCode: String,
    @SerialName("route_name") val routeName: String,
    val direction: String,
    @SerialName("service_date") val serviceDate: String,
    @SerialName("scheduled_start_at") val scheduledStartAt: String,  // ISO-8601
    @SerialName("scheduled_end_at") val scheduledEndAt: String,
    @SerialName("vehicle_id") val vehicleId: Long,
    @SerialName("vehicle_code") val vehicleCode: String,
    val plate: String,
    @SerialName("seat_capacity_snapshot") val seatCapacity: Int,
    val status: String
)

// Domain model — Kotlin puro, fechas tipadas
data class DriverTrip(
    val id: Long,
    val tripCode: String,
    val routeName: String,
    val direction: Direction,  // enum IDA / VUELTA
    val scheduledStartAt: Instant,
    val scheduledEndAt: Instant,
    val vehicleCode: String,
    val plate: String,
    val seatCapacity: Int,
    val status: TripStatus  // enum
)
```

### 4.8 Navegación

```
NavHost(startDestination = "login")
  "login"              → LoginScreen
  "dashboard"          → DashboardScreen (lista de viajes del día)
  "trip/{tripId}"      → TripDetailScreen (pasajeros + cronograma + botones de acción)
  "trip/{tripId}/incident" → IncidentReportScreen
  "profile"            → ProfileScreen
```

Transiciones:
- `login` → `dashboard` (login exitoso)
- `dashboard` → `trip/{id}` (tap en tarjeta de viaje)
- `trip/{id}` → `trip/{id}/incident` (botón reportar incidencia)
- `trip/{id}` → escáner QR (overlay/dialog con cámara, no ruta Nav separada)
- `dashboard`/`trip/{id}` → `profile` (menú)

### 4.9 Configuración de red

```kotlin
const val BASE_URL = "https://sitechfactura.site/api/"
// timeout: 10s connect, 15s read/write — el VPS es 1vCPU/1.9GB
// interceptor de logging SOLO en build debug (nunca loguear el JWT ni el qr_token)
// AuthInterceptor extrae token de SessionDataStore y lo inyecta en cada request
```

### 4.10 Permisos Android

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
```

CAMERA es required para escaneo QR. Se solicita en runtime la primera vez que el conductor toca "Escanear QR". Si fue denegado, mostrar rationale con botón de configuración.

### 4.11 Estrategia offline

- El core de la app (marcar llegada, board, no-show, alight, reportar incidencia) NO funciona offline — requiere comunicación con el backend. No hay cola de operaciones offline en MVP.
- La lista de viajes del día se cachea en memoria (ViewModel) pero se refresca siempre desde el backend (pull-to-refresh).
- Si no hay conexión al iniciar: mostrar error "Sin conexión a internet" con botón de reintento.

---

## 5. APIs del Backend — Referencia para la App

### 5.1 Autenticación

#### `POST /api/auth/login`

**Request:**
```json
{
  "document_number": "90000002",
  "password": "password"
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 2,
    "employee_code": "CON-001",
    "document_number": "90000002",
    "full_name": "Conductor Demo",
    "role": "DRIVER",
    "department": "Transporte",
    "phone": "999000002",
    "driver_license_number": "LIC-DEMO-001",
    "driver_license_category": "A-IIb",
    "driver_license_expires_on": "2028-04-15",
    "active": true
  }
}
```

**Nota**: los campos `driver_license_*` se agregan como parte del fix — antes no estaban en la respuesta.

#### `GET /api/me`

**Response (200):**
```json
{
  "id": 2,
  "employee_code": "CON-001",
  "full_name": "Conductor Demo",
  "role": "DRIVER"
}
```

### 5.2 Endpoints del módulo Driver

| # | Método | Path | Request | Response | Código |
|---|--------|------|---------|----------|--------|
| 1 | GET | `/driver/trips?date=YYYY-MM-DD` | Query: date opcional (default hoy) | `[]DriverTrip` | 200 |
| 2 | GET | `/driver/trips/{id}/passengers` | Path: trip_id | `[]Passenger` | 200 |
| 3 | POST | `/driver/trip-stops/{id}/arrival` | Path: trip_stop_time_id | sin body | 204 |
| 4 | POST | `/driver/reservations/{id}/board` | Path: reservation_id | sin body | 204 |
| 5 | POST | `/driver/reservations/{id}/no-show` | Path: reservation_id | sin body | 204 |
| 6 | POST | `/driver/reservations/{id}/alight` | Path: reservation_id | sin body | 204 |
| 7 | POST | `/driver/trips/{id}/incidents` | Path: trip_id, Body: `IncidentParams` | `{"id": N}` | 201 |

**No hay `GET /driver/profile`** — los datos de perfil se obtienen del login response + claims JWT.

### 5.3 Endpoints del módulo Booking

| # | Método | Path | Request | Response | Código |
|---|--------|------|---------|----------|--------|
| 1 | POST | `/reservations` | `ConfirmRequest` | `ConfirmResponse` | 201 |
| 2 | POST | `/reservations/{id}/cancel` | Path: reservation_id | sin body | 204 |
| 3 | POST | `/reservations/verify-qr` | `{"token": "uuid"}` | `Reservation` | 200 |

### 5.4 Estructuras de datos

```json
// DriverTrip
{
  "id": 123,
  "trip_code": "T-RUTA01-20260415",
  "route_id": 1,
  "route_code": "RUTA01",
  "route_name": "Sede Centro - Paradero Norte",
  "direction": "IDA",
  "service_date": "2026-04-15",
  "scheduled_start_at": "2026-04-15T07:00:00Z",
  "scheduled_end_at": "2026-04-15T07:45:00Z",
  "vehicle_id": 1,
  "vehicle_code": "BUS-001",
  "plate": "ABC-123",
  "seat_capacity_snapshot": 20,
  "status": "PUBLISHED"
}

// Passenger
{
  "reservation_id": 456,
  "reservation_code": "R-abc123",
  "worker_id": 5,
  "worker_full_name": "María García",
  "seat_number": 3,
  "seat_label": "3A",
  "origin_stop_order": 1,
  "origin_stop_name": "Paradero Norte",
  "destination_stop_order": 3,
  "destination_stop_name": "Sede Centro",
  "status": "CONFIRMED",
  "confirmed_at": "2026-04-14T10:30:00Z",
  "boarded_at": null
}

// Reservation (verify-qr response)
{
  "id": 456,
  "reservation_code": "R-abc123",
  "trip_id": 123,
  "worker_id": 5,
  "trip_seat_id": 30,
  "origin_trip_stop_time_id": 100,
  "destination_trip_stop_time_id": 102,
  "status": "CONFIRMED",
  "confirmed_at": "2026-04-14T10:30:00Z"
}

// IncidentParams (request body)
{
  "incident_type": "DELAY",
  "description": "Demora de 15 minutos por tráfico en la Av. Principal"
}

// IncidentResponse
{ "id": 789 }
```

### 5.5 Mapa de estados

```
Trip Status: DRAFT → PUBLISHED → BOARDING → IN_PROGRESS → COMPLETED
                                               ↘ CANCELLED

Reservation: CONFIRMED → BOARDED → COMPLETED
                ↓           ↓
            NO_SHOW     CANCELLED

TripStopTime: PENDING → ARRIVED → DEPARTED → SKIPPED
```

### 5.6 Códigos de error HTTP

| Código | Tipo | Cuándo ocurre |
|--------|------|---------------|
| 400 | ValidationError | Path param no es entero, body JSON inválido |
| 401 | UnauthorizedError | Token faltante, inválido o expirado |
| 403 | ForbiddenError | Rol no es DRIVER, o conductor no asignado al viaje |
| 404 | NotFoundError | Entidad no existe (viaje, parada, reserva) |
| 409 | ConflictError | SP rechaza la operación (estado incorrecto, tolerancia no vencida, asiento ocupado) |
| 422 | ValidationError | Campos del body no pasan validación |
| 500 | InternalError | Error inesperado de BD o interno |

---

*Documento generado como parte del SDD (Spec-Driven Development) para el Módulo Conductor Android.*
*Backend: Go 1.23+ / MariaDB 10.6+*
*Mobile: Kotlin 2.0+ / Jetpack Compose / Material 3*

*Auditado con ponytail: eliminado fcm_token (YAGNI), eliminado endpoint /driver/profile (datos disponibles sin round-trip extra), eliminada capa usecase (YAGNI), reemplazado kotlinx-datetime por java.time (stdlib).*
