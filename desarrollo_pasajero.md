# Desarrollo — Módulo Pasajero (App Android nativa)

> Este documento es la única fuente de verdad necesaria para continuar el desarrollo de la app Android del Módulo Pasajero del sistema de transporte corporativo `appmovilidadclinica`. Está escrito para que un agente de IA lo lea y continúe el desarrollo sin necesitar contexto adicional de la conversación en la que se generó. Todo lo que se afirma acá fue verificado contra el código real del backend (Go), no inventado.

---

## 1. Objetivo del documento

Dar continuidad al desarrollo de `passenger-android/`, un proyecto Android nativo (Kotlin, Clean Architecture + MVVM) ya scaffoldeado (61 archivos) contra el backend real del sistema. El proyecto **no fue compilado ni probado en un emulador/dispositivo todavía** — fue generado por otro agente de IA sin acceso a Android Studio ni a un toolchain de build. Quien continúe debe:

1. Abrir el proyecto en Android Studio (o compilar vía Gradle/OpenCode), resolver lo que el compilador marque.
2. Completar lo que la sección 12 (Trabajo pendiente) lista como faltante.
3. Respetar las convenciones de la sección 10 — no son sugerencias, son las reglas que ya sigue el 100% del código existente.

---

## 2. Contexto del proyecto

`appmovilidadclinica` es un sistema de transporte corporativo para un cliente (clínica): buses que llevan trabajadores entre paraderos y la sede. Ya existen:

- **Backend Go** (`backend/`) — modular monolith: módulos `auth`, `trips`, `booking`, `driver`, `admin`. MariaDB, lógica de negocio pesada en stored procedures y triggers.
- **Panel web admin** (`admin/`) — Vue 3 + PrimeVue 4, para el rol ADMIN (gestión de rutas, vehículos, usuarios, viajes, etc.).
- **App Android de pasajero** (`passenger-android/`, este documento) — para el rol WORKER: buscar viajes, reservar un asiento, generar QR de la reserva, cancelar. **Es lo único que falta terminar.**

No existe (todavía) una app de conductor — el rol DRIVER opera hoy vía endpoints REST bajo `/api/driver/*` sin cliente móvil propio.

**Backend en producción:** `https://sitechfactura.site/api/` (VPS: 1 vCPU / 1.9GB RAM, Ubuntu 24.04, MariaDB 11.4.5, nginx + Let's Encrypt).

---

## 3. Alcance del módulo pasajero

### Dentro de alcance
1. **Autenticación** — login con documento+contraseña, sesión JWT de 24h.
2. **Consulta** — búsqueda de viajes por fecha/dirección/origen/destino, ver disponibilidad de asientos.
3. **Reserva** — elegir 1 asiento, confirmar, recibir un código QR.
4. **QR** — generar y mostrar el QR de la reserva confirmada (on-device, sin llamar al backend de nuevo).
5. **Confirmación de abordaje manual** — botón de contingencia si falla la lectura del QR por el chofer. **Depende de un endpoint de backend que no existe todavía** (ver sección 5).
6. **Cancelación** — cancelar una reserva propia.

### Fuera de alcance (explícito)
- Notificaciones push (la columna `fcm_token` ya existe en `users` pero no se usa acá).
- Modo offline / sincronización diferida.
- Pantallas de administración (ya cubiertas por el panel web).
- Tests automatizados más allá de lo que exista hoy (hoy: ninguno — ver sección 12).

---

## 4. Backend — contratos EXISTENTES (verificados contra el código Go real)

Todos los endpoints van bajo `https://sitechfactura.site/api/`. Autenticación: header `Authorization: Bearer <token>` en toda ruta salvo `POST /auth/login` y `GET /health`.

### 4.1 Autenticación

**`POST /api/auth/login`** (público)
```json
// Request
{ "document_number": "string", "password": "string" }

// Response 200
{
  "token": "string",
  "user": {
    "id": 0, "employee_code": "string", "document_number": "string",
    "full_name": "string", "role": "ADMIN|DRIVER|WORKER",
    "department": "string|null", "phone": "string|null", "active": true
  }
}
```
- Credenciales inválidas → **401**, mensaje genérico (no distingue si falló el documento o la contraseña).
- El backend **deja loguearse a ADMIN/DRIVER también** — la app debe verificar `role == "WORKER"` después del login y bloquear la navegación si no lo es (mensaje: "Esta app es para trabajadores. Usá el panel admin o la app de conductor." — implementado en `LoginUseCase.kt`).
- JWT: HS256, `exp` a 24h desde el login, **sin refresh token**. Claims: `user_id`, `role`, `full_name`, `employee_code`, `iat`, `exp`.

**`GET /api/auth/me`** (requiere JWT) — devuelve `{id, employee_code, full_name, role}` armado desde los claims del token, no toca la BD. No usado hoy en el código Android (la sesión se guarda completa en el login).

### 4.2 Búsqueda de viajes

**`GET /api/trips?date=YYYY-MM-DD&direction=IDA|VUELTA&origin={stop_id}&destination={stop_id}`**
- `origin`/`destination` acá son **`transport_stops.id`** (parada física).
- Response: array de objetos (nunca `null`, `[]` si no hay resultados):
```json
[{
  "trip_id": 0, "trip_code": "string", "route_code": "string", "route_name": "string",
  "direction": "IDA|VUELTA",
  "origin_order": 0, "origin_name": "string", "origin_departure_at": "2026-07-15T08:00:00Z",
  "destination_order": 0, "destination_name": "string", "destination_arrival_at": "2026-07-15T08:40:00Z",
  "vehicle_code": "string", "plate": "string",
  "booking_opens_at": "...", "booking_closes_at": "...",
  "booking_state": "NOT_OPEN|OPEN|CLOSED",
  "available_seats": 0
}]
```
- `booking_state` lo calcula el backend (comparando contra `CURRENT_TIMESTAMP` del servidor) — **la app nunca lo recalcula localmente**, evita desincronización de reloj.
- Solo devuelve viajes `status='PUBLISHED'`.

**`GET /api/trips/{id}`** — cabecera + cronograma completo:
```json
{
  "trip": {
    "id": 0, "trip_code": "string", "source": "GENERATED|MANUAL",
    "route_id": 0, "service_date": "2026-07-15",
    "scheduled_start_at": "...", "scheduled_end_at": "...",
    "booking_opens_at": "...", "booking_closes_at": "...",
    "vehicle_id": 0, "driver_id": 0, "seat_capacity_snapshot": 0,
    "no_show_tolerance_minutes": 0,
    "status": "DRAFT|PUBLISHED|BOARDING|IN_PROGRESS|COMPLETED|CANCELLED"
  },
  "stops": [{
    "id": 0, "stop_id": 0, "stop_order": 0,
    "scheduled_arrival_at": "...", "scheduled_departure_at": "...",
    "status": "PENDING|ARRIVED|DEPARTED|SKIPPED",
    "stop_name": "string", "stop_type": "SEDE|PARADERO"
  }]
}
```
- El campo `stops[].id` es el **`trip_stop_time_id`** — se necesita para el siguiente paso. Este endpoint es el paso obligatorio entre buscar un viaje y pedir sus asientos.
- 404 si el viaje no existe.

**`GET /api/trips/{id}/seats?origin={trip_stop_time_id}&destination={trip_stop_time_id}`**
- ⚠️ **Acá `origin`/`destination` son `trip_stop_time_id`, NO `stop_id`** (distinto del endpoint de búsqueda). Se obtienen del array `stops` de `GET /trips/{id}`.
```json
[{ "trip_seat_id": 0, "seat_number": 0, "seat_label": "string", "availability": "AVAILABLE|OCCUPIED_IN_REQUESTED_RANGE|BLOCKED" }]
```
- Si origen ≥ destino en orden → 409 del backend. La app valida esto client-side ANTES de llamar (ver `ListSeatsUseCase.kt`).

### 4.3 Reservas

**`POST /api/reservations`** (`worker_id` sale del JWT, no va en el body)
```json
// Request
{ "trip_id": 0, "trip_seat_id": 0, "origin_trip_stop_time_id": 0, "destination_trip_stop_time_id": 0 }

// Response 201
{ "reservation_id": 0, "reservation_code": "R-XXXXXXXXXXXXXXXXXXXXXXXX", "qr_token": "uuid-crudo-36-chars", "status": "CONFIRMED" }
```
- **`qr_token` es un UUID crudo que el backend entrega UNA SOLA VEZ.** En la base de datos solo se guarda `SHA256(qr_token)` — el backend NUNCA puede volver a devolverlo. **Si la app pierde este dato antes de persistirlo, el QR de esa reserva se pierde para siempre.** Por eso la persistencia a Room ocurre como primera línea dentro de `ReservationsRepositoryImpl.confirm()`, antes de cualquier otra cosa.
- 409 si: el trabajador ya tiene una reserva activa en ese viaje, el asiento está ocupado en el tramo pedido, fuera de la ventana de reserva, el rol no es WORKER activo, la regla direccional (IDA debe terminar en SEDE, VUELTA debe empezar en SEDE) no se cumple, etc. El mensaje que viene en `error.message` ya está en español y es literal del stored procedure — mostrarlo tal cual, no reinterpretar.
- Regla "1 reserva activa por trabajador por viaje": hay un backstop de índice único en la base de datos además del chequeo en Go — no se puede burlar reintentando rápido.

**`POST /api/reservations/{id}/cancel`** — sin body → **204**.
- ⚠️ **El backend NO valida que quien cancela sea el dueño de la reserva.** Cualquier JWT válido con un `reservation_id` puede cancelar la reserva de otro trabajador. La app mitiga esto SOLO mostrando el botón de cancelar en reservas que pertenecen al usuario logueado (filtro client-side, no es una garantía de seguridad real). Si esto importa para el negocio, es un fix de backend pendiente, no algo que la app pueda resolver sola.

**`POST /api/reservations/verify-qr`** — pensado para el CONDUCTOR, no para el pasajero. La app de pasajero no lo usa (no tiene sentido: el pasajero no escanea su propio QR).
```json
{ "token": "string" } // el texto crudo escaneado
```

### 4.4 Confirmación de abordaje por el CHOFER (no por el pasajero)

Bajo `/api/driver/*`, exclusivo rol DRIVER y solo si es el chofer asignado a ese viaje (`driver_id` del JWT == `trip_instances.driver_id`):
- `POST /driver/reservations/{id}/board`
- `POST /driver/reservations/{id}/no-show`
- `POST /driver/reservations/{id}/alight`

**Ninguno de estos tres endpoints sirve para el pasajero.** Ver sección 5.1 para el endpoint que SÍ hace falta.

---

## 5. Backend — contratos NUEVOS requeridos (NO EXISTEN todavía)

Estos dos contratos están especificados abajo, y el código Android **ya está escrito contra ellos** (`ReservationsApi.selfCheckin()`, `StopsApi.list()`) — contra el backend real de hoy, esas dos llamadas van a devolver 404/403. Hay que implementarlos en el backend Go antes de que esas dos funciones (self check-in, y los selectores de origen/destino en la búsqueda) funcionen de punta a punta.

### 5.1 `POST /api/reservations/{id}/self-checkin`

No existe ningún endpoint hoy donde el WORKER pueda auto-confirmar su propio abordaje (verificado: solo `/driver/*` marca `BOARDED`, y exige rol DRIVER + ownership del viaje). Contrato propuesto:

```json
// Request: sin body (o {} vacío)
// Response 200
{ "reservation_id": 0, "status": "BOARDED", "boarded_at": "2026-07-15T08:05:00Z" }
```

Validaciones sugeridas para la implementación en Go (mismo espíritu que los SPs de `driver`):
- `worker_id` de la reserva == `user_id` del JWT (ownership real — a diferencia de `cancel`, acá si importa).
- `reservations.status == 'CONFIRMED'` (si no, 409).
- Ventana de tiempo: solo permitido cerca del horario de salida de la parada de origen (la app ya implementa una ventana de ±30 min client-side en `MyReservationDetailViewModel.canSelfCheckin` — el backend debería validar lo mismo server-side, no confiar solo en el cliente).
- Implementación posible: SP hermano de `sp_mark_reservation_boarded` que no exija `driver_id` coincidente, o agregarle un modo "self" a ese mismo SP.

### 5.2 `GET /api/stops` (catálogo de paradas para WORKER)

Hoy el único listado de paradas es `GET /admin/stops`, que exige rol ADMIN (`requireAdmin()` en `backend/internal/modules/admin/service.go`, verificado — devuelve 403 a cualquier otro rol). Un WORKER no tiene forma de poblar los selectores de origen/destino de la pantalla de búsqueda.

```json
// Response 200 — sin paginar (catálogo chico, a diferencia de /admin/stops que sí pagina)
[{ "id": 0, "code": "string", "name": "string", "stop_type": "SEDE|PARADERO" }]
```

Recomendado: endpoint nuevo bajo `/api` (no bajo `/admin`), público para cualquier JWT válido, solo lectura — para no tocar el guard de `/admin/stops` que ya existe y es correcto para ese caso de uso.

---

## 6. Modelo de datos relevante (MariaDB, `backend/migrations/0001_schema.up.sql`)

Tablas que la app consume (columnas relevantes, no el DDL completo — ver el archivo real para constraints/FKs):

- **`users`**: `id, employee_code, document_number, password_hash, full_name, role (ADMIN|DRIVER|WORKER), department, phone, preferred_stop_id, fcm_token, active`.
- **`trip_instances`**: `id, trip_code, route_id, service_date, scheduled_start_at, scheduled_end_at, booking_opens_at, booking_closes_at, vehicle_id, driver_id, seat_capacity_snapshot, no_show_tolerance_minutes, status`.
- **`trip_stop_times`**: `id, trip_id, route_stop_id, stop_id, stop_order, scheduled_arrival_at, scheduled_departure_at, status`.
- **`trip_seats`**: `id, trip_id, vehicle_seat_id, seat_number, seat_label, is_blocked, block_reason`.
- **`reservations`**: `id, reservation_code, qr_token_hash (CHAR(64), el token NUNCA se guarda en claro), booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status (CONFIRMED|BOARDED|COMPLETED|NO_SHOW|CANCELLED), confirmed_at, boarded_at, completed_at, no_show_at`.

Nota: `booking_group_uuid` lo genera el backend server-side en cada `POST /reservations` — **no hay forma hoy de que el cliente le pase el mismo UUID a una reserva IDA y su VUELTA correspondiente para agruparlas**. Si el negocio necesita agrupar ida+vuelta visualmente, es otro cambio de backend pendiente (no implementado, no diseñado todavía).

---

## 7. Stack tecnológico (ya aplicado en el proyecto)

| Capa | Elección | Por qué |
|---|---|---|
| Lenguaje | Kotlin 2.0.21 | Estándar Android nativo moderno |
| UI | Jetpack Compose + Material 3 | Recomendación oficial para proyectos nuevos |
| Arquitectura | Clean Architecture (domain/data/presentation) + MVVM | Ver sección 8 |
| DI | Hilt 2.52 | Estándar de facto sobre Dagger para Android |
| Networking | Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization | 100% Kotlin, sin reflection (a diferencia de Gson/Moshi) |
| Async | Coroutines + Flow | Estándar Kotlin |
| Fecha/hora | **`java.time.*`** (NO kotlinx-datetime) | `minSdk=26` ya incluye `java.time` nativo sin desugaring — la dependencia extra no aportaba nada (ver sección 10.2) |
| Persistencia sesión | DataStore Preferences | Reemplazo oficial de SharedPreferences |
| Persistencia reservas | Room (una sola entidad, `ReservationEntity`) | El `qr_token` no se puede volver a pedir al backend — necesita persistencia confiable con UPDATE atómico por fila, no un blob JSON |
| QR | ZXing `core` (NO `zxing-android-embedded`) | Solo generar, no escanear — `zxing:core` es Java puro, sin dependencia de `android.*` |
| Navegación | Navigation Compose 2.8+ (rutas tipadas `@Serializable`) | Sin strings de ruta a mano, argumentos verificados en compilación |
| Min SDK | 26 (Android 8.0) | Cobertura >95% del parque activo, sin soporte legacy innecesario |

**Deliberadamente NO agregado todavía** (ver sección 12): JUnit5/MockK/Turbine (sin tests escritos aún, se agregan con el primer test real).

---

## 8. Arquitectura — Clean Architecture + MVVM

```
presentation/   → Compose UI + ViewModel (por feature). Conoce domain, NUNCA data.
domain/         → Kotlin puro. CERO dependencia de Android/Retrofit/Room (excepción documentada: ZXing core, ver GenerateQrUseCase.kt).
data/           → Implementación concreta (Retrofit, Room, DataStore). Conoce domain, implementa sus interfaces.
di/             → Módulos Hilt que conectan todo.
```

**Regla de dependencia**: `presentation` → `domain` ← `data`. Los ViewModels inyectan **interfaces de `domain/repository/`**, nunca `*RepositoryImpl` directo, nunca `*Api`/`*Dao` directo.

**Regla sobre use cases (importante, ver sección 10.1)**: NO crear un use case por cada método de repository "por las dudas". Un use case solo se justifica si tiene lógica real (validación, orquestación de 2+ repos, transformación). Si un use case solo hace `= repository.metodo(...)` sin agregar nada, el ViewModel debe inyectar el repository directo. Hoy sobreviven exactamente 3 use cases: `LoginUseCase` (valida rol WORKER), `ListSeatsUseCase` (valida origen<destino antes de pegarle al backend), `GenerateQrUseCase` (encode ZXing). Todos los demás se eliminaron en una auditoría explícita — no volver a agregar ese patrón sin pensarlo.

---

## 9. Estructura de archivos actual (inventario completo, 61 archivos)

```
passenger-android/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/libs.versions.toml              # version catalog — TODAS las versiones de deps viven acá
├── app/
│   ├── build.gradle.kts                   # namespace com.appmovilidadclinica.passenger, minSdk 26, compileSdk 35
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml            # network_security_config fuerza HTTPS, allowBackup=false (por el qr_token)
│       ├── res/values/{strings,themes}.xml, res/xml/network_security_config.xml
│       └── java/com/appmovilidadclinica/passenger/
│           ├── PassengerApp.kt            # @HiltAndroidApp
│           ├── MainActivity.kt            # setContent { PassengerTheme { PassengerNavGraph() } }
│           │
│           ├── domain/                    # Kotlin puro
│           │   ├── model/
│           │   │   ├── User.kt            # User, UserRole (ADMIN|DRIVER|WORKER)
│           │   │   ├── Trip.kt            # TripDirection, BookingState, TripSearchResult, TripStop(Status), TripDetail, TripStatus
│           │   │   ├── Seat.kt            # SeatAvailability, TripSeat
│           │   │   ├── Reservation.kt     # ReservationStatus, Reservation, ReservationRequest
│           │   │   └── Stop.kt            # StopType, Stop
│           │   ├── repository/            # 4 interfaces, cada una con 1 sola impl en data/
│           │   │   ├── AuthRepository.kt
│           │   │   ├── TripsRepository.kt
│           │   │   ├── ReservationsRepository.kt  (+ ReservationTripContext)
│           │   │   └── StopsRepository.kt
│           │   ├── usecase/                # SOLO 3 archivos — ver sección 8
│           │   │   ├── LoginUseCase.kt
│           │   │   ├── ListSeatsUseCase.kt
│           │   │   └── GenerateQrUseCase.kt
│           │   └── error/
│           │       └── AppError.kt         # sealed class AppError (Unauthorized/Forbidden/NotFound/Conflict/Validation/Network/Unknown) + AppResult<T>
│           │
│           ├── data/
│           │   ├── remote/
│           │   │   ├── AuthApi.kt, TripsApi.kt, ReservationsApi.kt, StopsApi.kt   # interfaces Retrofit
│           │   │   ├── dto/{Auth,Trip,Reservation,Stop,ErrorResponse}Dto.kt        # shapes JSON EXACTOS (kotlinx.serialization)
│           │   │   ├── AuthInterceptor.kt          # agrega "Authorization: Bearer <token>", detecta 401 global
│           │   │   ├── SessionExpiredNotifier.kt   # bus de eventos (SharedFlow) para el 401 global
│           │   │   └── ApiErrorMapper.kt           # HTTP -> AppError, mismo texto que admin/src/messages.ts
│           │   ├── local/
│           │   │   ├── SessionDataStore.kt         # DataStore Preferences: token + datos básicos del user
│           │   │   ├── ReservationEntity.kt, ReservationDao.kt, AppDatabase.kt   # Room, persiste qr_token
│           │   ├── mapper/
│           │   │   ├── AuthMapper.kt, TripMapper.kt, ReservationMapper.kt, StopMapper.kt   # DTO/Entity <-> domain
│           │   └── repository/
│           │       ├── AuthRepositoryImpl.kt        # incluye decode de JWT inline (expiresAtEpochSeconds)
│           │       ├── TripsRepositoryImpl.kt, ReservationsRepositoryImpl.kt, StopsRepositoryImpl.kt
│           │
│           ├── di/
│           │   ├── NetworkModule.kt        # Json, OkHttpClient, Retrofit, las 4 Api — API_BASE_URL como const privado
│           │   ├── DatabaseModule.kt       # DataStore<Preferences>, Room AppDatabase, ReservationDao
│           │   └── RepositoryModule.kt     # @Binds de las 4 interfaces a sus impl
│           │
│           └── presentation/
│               ├── theme/Theme.kt          # verde emerald, mismo tono que admin/src/main.ts (AdminPreset)
│               ├── navigation/
│               │   ├── Screen.kt           # rutas @Serializable: Login, TripSearch, MyReservations, SeatSelection(tripId,originStopId,destinationStopId), MyReservationDetail(reservationId)
│               │   └── NavGraph.kt         # PassengerNavGraph — decide Login vs. resto según sesión, muestra el modal de sesión expirada global
│               ├── common/
│               │   ├── SessionViewModel.kt     # sesión, countdown de expiración, logout forzado por 401
│               │   ├── SessionComponents.kt    # SessionExpiredDialog, SessionExpiryBanner (T-2min)
│               │   └── QrBitmap.kt             # BitMatrix (ZXing) -> Bitmap (única conversión Android-específica del flujo QR)
│               ├── auth/
│               │   ├── LoginViewModel.kt, LoginScreen.kt
│               ├── tripsearch/
│               │   ├── TripSearchViewModel.kt, TripSearchScreen.kt   # form + resultados
│               ├── seatselection/
│               │   ├── SeatSelectionViewModel.kt, SeatSelectionScreen.kt   # grilla de asientos
│               └── myreservation/
│                   ├── MyReservationsViewModel.kt, MyReservationsScreen.kt        # lista (Room)
│                   └── MyReservationDetailViewModel.kt, MyReservationDetailScreen.kt   # QR + self-checkin + cancelar
```

---

## 10. Convenciones obligatorias

### 10.1 Arquitectura
- No agregar un use case que solo delega (`= repository.metodo(...)` sin lógica). Ver sección 8.
- Todo repository nuevo: interfaz en `domain/repository/`, implementación en `data/repository/`, bind en `di/RepositoryModule.kt` con `@Binds`.
- Toda llamada de red pasa por `safeApiCall`/`safeApiCallUnit` (`data/remote/ApiErrorMapper.kt`) — nunca un `try/catch` suelto en un repository.
- `presentation` nunca importa nada de `data.*` directo (ni DTOs, ni Retrofit, ni Room) — solo tipos de `domain`.

### 10.2 Fecha y hora
- **`java.time.*`, nunca `kotlinx.datetime`.** `minSdk=26` lo soporta nativo. Si algún día se baja el `minSdk` por debajo de 26, ahí sí hay que evaluar desugaring o volver a kotlinx-datetime — no antes.

### 10.3 Idioma — español neutral formal ("usted"), SIN voseo ni tuteo

Todo texto visible al usuario (labels, botones, mensajes de error, diálogos) usa el registro formal "usted", igual que el panel admin (`admin/src/messages.ts`), NUNCA voseo Rioplatense ("Ingresá", "Elegí", "tenés") ni tuteo ("Ingresa", "tienes"). Ejemplos ya establecidos en el código:

| Situación | Texto correcto | NO usar |
|---|---|---|
| Instrucción | "Ingrese su número de documento y contraseña" | "Ingresá..." / "Ingresa..." |
| Campo vacío | "Complete el documento y la contraseña." | "Completá..." |
| Sin conexión | "No se pudo conectar con el servidor. Verifique su conexión." | "Revisá tu conexión" |
| Sesión expirada | "Su sesión expiró. Inicie sesión nuevamente para continuar." | "Tu sesión..." / "Iniciá..." |
| Confirmación destructiva | "¿Confirma que desea cancelar esta reserva? Esta acción no se puede deshacer." | "¿Seguro que querés...?" |
| Error genérico | "Ocurrió un error inesperado. Intente nuevamente." | "...Intentá..." |
| 403 | "No tiene permisos para realizar esta acción." | "No tenés permiso..." |

`data/remote/ApiErrorMapper.kt::fallbackMessage()` es la copia textual de `ERROR_BY_STATUS`/`ERROR_FALLBACK_DEFAULT` de `admin/src/messages.ts` — si se agrega un código HTTP nuevo, buscar primero si el panel admin ya tiene el texto para ese caso y copiarlo igual, no inventar uno nuevo.

### 10.4 Manejo de errores
- Todo método suspend que llama al backend devuelve `AppResult<T>` (`domain/error/AppError.kt`), nunca lanza una excepción hacia el ViewModel.
- El ViewModel mapea `AppError` a texto de UI en un `when` local (ver `messageFor()`/`errorMessageFor()` en los ViewModels existentes) — no reutilizar `error.message` crudo salvo para `AppError.Conflict` (ese texto ya viene en español desde el backend, listo para mostrar).

### 10.5 QR y persistencia
- El `qr_token` de una reserva se persiste en Room como **primera línea** dentro de `ReservationsRepositoryImpl.confirm()`, antes de cualquier otra operación — no reordenar esto.
- Nunca loguear `qr_token` ni el JWT, ni siquiera en el interceptor de logging de debug (`NetworkModule.kt` ya lo limita a `BuildConfig.DEBUG`, pero igual no imprimir esos campos explícitamente en ningún `Log.d`/`println` nuevo que se agregue).

---

## 11. Estado actual — qué funciona y qué es un placeholder

| Feature | Estado del código Android | Depende de backend nuevo |
|---|---|---|
| Login | Completo, funcional contra el backend real | No |
| Búsqueda de viajes | Completa, funcional | Sí — el selector de origen/destino necesita `GET /api/stops` (sección 5.2) |
| Selección de asiento | Completa, funcional | No |
| Confirmar reserva + QR | Completo, funcional | No |
| Cancelar reserva | Completo, funcional | No (pero ver el hueco de seguridad de la sección 4.3) |
| Confirmación manual de abordaje | Código completo, botón visible con ventana de tiempo | Sí — `POST /reservations/{id}/self-checkin` (sección 5.1) no existe, la llamada da 404 hoy |
| Countdown de sesión / logout forzado | Completo, funcional | No |

**Nada de esto fue compilado.** El siguiente paso obligatorio de quien continúe es abrir el proyecto y resolver errores de compilación si los hay (no se puede garantizar que no los haya — fue escrito sin acceso a un compilador Kotlin).

---

## 12. Trabajo pendiente

### Backend (bloqueante para 2 features)
- [ ] Implementar `POST /api/reservations/{id}/self-checkin` (contrato en sección 5.1).
- [ ] Implementar `GET /api/stops` (contrato en sección 5.2).
- [ ] (Opcional, no bloqueante) Agregar validación de ownership a `POST /reservations/{id}/cancel`.

### Android — antes de considerar el proyecto usable
- [ ] Abrir en Android Studio, resolver errores de compilación.
- [ ] Generar assets de ícono de launcher (`@mipmap/ic_launcher` está referenciado en el `AndroidManifest.xml` pero no existe como archivo — usar Asset Studio de Android Studio).
- [ ] Probar el flujo completo contra el backend real: login → buscar → reservar → ver QR → cancelar.
- [ ] Revisar la UI de `TripSearchScreen.kt` — el date picker hoy es un texto estático (`Text("Fecha: ${state.date}")`), falta conectar un `DatePickerDialog` de Material3 real.
- [ ] Escribir al menos un test cuando se agregue lógica nueva no trivial (recién ahí sumar JUnit5/MockK/Turbine al `build.gradle.kts` — ver sección 10.1 de por qué no están ya).
- [ ] Evaluar cifrado del `SessionDataStore` (hoy el JWT se guarda en texto plano en DataStore — `androidx.security.crypto` o Keystore quedó fuera del alcance inicial, documentado como próximo hardening en `SessionDataStore.kt`).

### Deuda técnica aceptada (no son bugs, son decisiones documentadas)
- Room para una sola entidad (`ReservationEntity`) conviviendo con DataStore — se evaluó consolidar todo en DataStore y se decidió NO hacerlo: el `UPDATE` atómico de Room es más correcto que un read-modify-write de JSON para las transiciones de estado (`CONFIRMED → BOARDED → COMPLETED`).
- 4 repository interfaces con una sola implementación cada una — convención estándar de testabilidad en Android/Hilt, se decidió mantener.

---

## 13. Cómo abrir y correr el proyecto

```bash
cd passenger-android
# Abrir en Android Studio (Hedgehog+ recomendado, AGP 8.7.2 / Kotlin 2.0.21)
# o, vía CLI/OpenCode:
./gradlew assembleDebug   # (nota: no se generó el gradlew wrapper — correr `gradle wrapper` primero si hace falta)
```

El proyecto apunta a `https://sitechfactura.site/api/` en duro (`di/NetworkModule.kt`, constante `API_BASE_URL`) — no hay variante de staging todavía (ver sección 7, tabla de deps deliberadamente no agregadas).

---

## 14. Referencias cruzadas al resto del repo

- `Documentacion/arquitectura_sistema.md` — arquitectura completa del backend (capas, decisiones de seguridad, flujo end-to-end).
- `Documentacion/diccionario_datos_transporte_mvp.md` — diccionario de las 22 tablas del schema (ojo: tiene una afirmación desactualizada sobre cancelación voluntaria — sí existe, agregada en `0002_cancel_sps.up.sql`).
- `Documentacion/explicacion_sistema.md` — documento conceptual con nombres de tabla en español antiguo que NO coinciden con el schema real — usar solo para entender reglas de negocio, no como referencia técnica.
- `transporte_mvp.postman_collection.json` — colección Postman, verificada al día contra el código Go en las secciones Trips/Booking.
- `admin/src/messages.ts` — fuente de verdad del tono de idioma (español neutral formal) para cualquier texto nuevo.
- `backend/migrations/0001_schema.up.sql`, `0002_cancel_sps.up.sql`, `0003_active_reservation_guard.up.sql` — schema completo y stored procedures/triggers reales.
