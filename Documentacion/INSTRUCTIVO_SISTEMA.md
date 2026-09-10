# Instructivo del Sistema de Transporte Corporativo

**Proyecto:** appmovilidadclinica
**Web:** https://movilidad.sitech.site/
**Backend API:** https://sitechfactura.site/api/ (mismo backend, distinto vhost Nginx)
**Stack:** Go + MariaDB + Vue 3 + Android nativo
**Servidor:** VPS RackNerd (Ubuntu 24.04, 1 vCPU, 1.9 GB RAM)
**Fecha:** Septiembre 2026

---

## Índice

1. [Visión general](#1-visión-general)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Panel Web de Administración](#3-panel-web-de-administración)
4. [App del Pasajero (Android)](#4-app-del-pasajero-android)
5. [App del Conductor (Android)](#5-app-del-conductor-android)
6. [Flujos end-to-end](#6-flujos-end-to-end)
7. [Reglas de negocio clave](#7-reglas-de-negocio-clave)
8. [Usuarios y roles](#8-usuarios-y-roles)
9. [Acceso al sistema](#9-acceso-al-sistema)
10. [Operaciones técnicas frecuentes](#10-operaciones-técnicas-frecuentes)

---

## 1. Visión general

El sistema **appmovilidadclinica** es una plataforma integral para gestionar el transporte corporativo de los trabajadores de una clínica. Permite:

- **A los trabajadores (WORKER):** buscar viajes, reservar asientos por tramos, generar un QR de su reserva.
- **A los conductores (DRIVER):** ver su hoja de ruta, marcar llegada a paraderos, escanear QR de pasajeros, registrar abordajes/no-shows/bajadas, reportar incidencias.
- **A los administradores (ADMIN):** configurar rutas, vehículos, plantillas de viaje, usuarios, monitorear la operación desde un panel web.

El sistema opera con **3 componentes principales** que se conectan a un único backend Go:

```
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│  Panel Web Admin    │  │  App Pasajero       │  │  App Conductor      │
│  Vue 3 + PrimeVue   │  │  Android (Kotlin)   │  │  Android (Kotlin)   │
│  movilidad.sitech   │  │  passenger-android  │  │  driver-android     │
└──────────┬──────────┘  └──────────┬──────────┘  └──────────┬──────────┘
           │                        │                        │
           └────────────────────────┼────────────────────────┘
                                    │ HTTPS + JWT
                                    ▼
                    ┌───────────────────────────────┐
                    │   Nginx (TLS, reverse proxy)  │
                    │   sitechfactura.site          │
                    └───────────────┬───────────────┘
                                    │ proxy_pass :8080
                                    ▼
                    ┌───────────────────────────────┐
                    │   Backend Go 1.25             │
                    │   (modular monolith)          │
                    │   systemd: appmovilidadclinica│
                    └───────────────┬───────────────┘
                                    │ database/sql + SPs
                                    ▼
                    ┌───────────────────────────────┐
                    │   MariaDB 11.4                │
                    │   transporte_corporativo_mvp  │
                    └───────────────────────────────┘
```

---

## 2. Arquitectura del sistema

### 2.1 Backend (Go)

- **Tipo:** Modular monolith en Go 1.25, binario estático (~11 MB).
- **Layout:** `/opt/appmovilidadclinica/backend/` en el VPS.
- **Módulos internos:**
  - `auth` — login JWT, claims (user_id, role, full_name, employee_code).
  - `trips` — búsqueda, generación automática (job cada 6h, horizonte 30 días).
  - `booking` — reservas, verificación de QR, cancelación.
  - `driver` — hoja de ruta, marcaje de llegada, board/no-show/alight, incidencias.
  - `admin` — CRUD de catálogos (rutas, vehículos, usuarios, plantillas).
- **Lógica de negocio pesada:** vive en **stored procedures** de MariaDB. Go es capa fina que valida reglas que las SPs no pueden expresar fácilmente (ej: "una reserva activa por viaje por trabajador").
- **Migraciones:** se aplican automáticamente al arrancar (`RunMigrations` en cada boot). Son idempotentes (DROP + CREATE).

### 2.2 Base de datos (MariaDB 11.4)

- 22 tablas en `transporte_corporativo_mvp`.
- Stored procedures clave:
  - `sp_search_trips(origen, destino, fecha)` — búsqueda atómica con disponibilidad por tramos.
  - `sp_confirm_reservation(...)` — crea reserva, devuelve `qr_token` (UUID crudo, una sola vez).
  - `sp_mark_trip_stop_arrival(trip_stop_time_id)` — conductor marca llegada a paradero.
  - `sp_mark_reservation_boarded/no_show/alighted` — transiciones de estado.
  - `sp_generate_trip_instance(template_id, fecha)` — generador de viajes.
- Tuning: `innodb_buffer_pool_size=256M`, `max_connections=50`.

### 2.3 Nginx (reverse proxy)

- Dos vhosts comparten el mismo backend:
  - `sitechfactura.site` — API (`/api/*` → `:8080`).
  - `movilidad.sitech.site` — **panel admin** (sirve el bundle Vue estático desde `/admin/dist`) + mismas rutas `/api/*`.
- TLS con Let's Encrypt (renovación automática vía certbot).

### 2.4 Servicio systemd

- Unidad: `appmovilidadclinica.service`.
- Arranque automático tras reboot.
- Logs en `/var/log/appmovilidadclinica/server.log`.

---

## 3. Panel Web de Administración

### 3.1 Acceso

- **URL:** https://movilidad.sitech.site/
- **Login:** documento + contraseña (mismo endpoint `/api/auth/login` que usan las apps).
- **Rol requerido:** `ADMIN`. Si entrás con rol DRIVER o WORKER, el panel te bloquea.

### 3.2 Stack

- **Vue 3.5** (Composition API, `<script setup>`).
- **PrimeVue 4.2** + `@primeuix/themes` (preset emerald — mismo verde que la app pasajero).
- **Vue Router 4** (SPA con `try_files $uri $uri/ /index.html`).
- **Vite 6** (build, `dist/` se sirve estático).
- **TypeScript 5.7** (strict).
- **vueuse** (utilidades reactivas).
- **xlsx** (exportación de reportes).

### 3.3 Configuración de red

`admin/.env`:
```
VITE_API_BASE_URL=/api
```
**Ruta relativa, no absoluta.** El mismo bundle funciona en `sitechfactura.site` y en `movilidad.sitech.site` porque ambos nginx vhosts proxyean `/api/*` al backend.

### 3.4 Funcionalidades principales

El panel admin permite gestionar toda la operación:

#### Gestión de catálogos
- **Usuarios** (`/users`): crear, editar, activar/desactivar. Roles: ADMIN, DRIVER, WORKER. Campos: documento, código de empleado, nombre, departamento, teléfono, licencia (solo DRIVER), parada preferida (solo WORKER).
- **Vehículos** (`/vehicles`): flota con código, placa, capacidad de asientos. Cada vehículo tiene sus `vehicle_seats` (asientos físicos con número y etiqueta, ej: "3A").
- **Paradas** (`/stops`): catálogo de `transport_stops` con `code`, `name`, `stop_type` (SEDE o PARADERO).
- **Rutas** (`/routes`): definición de `transport_routes` (IDA/VUELTA), sus `route_stops` (orden) y `route_segments` (tramos).

#### Configuración de horarios
- **Matriz de tiempos** (`/travel-time-profiles`): perfiles de tiempo de viaje entre segmentos, aplicados por prioridad.
- **Calendarios de servicio** (`/service-calendars`): qué días opera el sistema, con excepciones (feriados).
- **Plantillas de viaje** (`/trip-templates`): combinación de ruta + calendario + vehículo + conductor + horarios + ventana de reserva. Define los viajes recurrentes.

#### Monitoreo
- **Viajes** (`/trips`): lista de `trip_instances` generados (DRAFT, PUBLISHED, BOARDING, IN_PROGRESS, COMPLETED, CANCELLED). Ver cronograma, pasajeros confirmados, incidencias.
- **Reservas** (`/reservations`): todas las reservas del sistema.
- **Generación** (`/generation-runs`): corridas del job generador automático (qué viajes se crearon, cuándo).
- **Incidencias** (`/incidents`): reportes de conductores (BREAKDOWN, DELAY, ACCIDENT, OTHER).

### 3.5 Flujo de uso típico del admin

1. **Setup inicial (una vez):**
   - Crear SEDES y PARADEROS en `/stops`.
   - Crear VEHÍCULOS con sus asientos en `/vehicles`.
   - Crear RUTAS con sus paradas en `/routes`.
   - Definir MATRIZ DE TIEMPOS en `/travel-time-profiles`.
   - Crear USUARIOS con sus roles en `/users`.

2. **Operación diaria:**
   - Revisar `/trips` para ver viajes generados (el job corre cada 6h).
   - Verificar incidencias reportadas por conductores.
   - Atacar problemas manualmente (cancelar un viaje, reasignar conductor, etc.).
   - Exportar reportes (XLSX) para auditoría.

3. **Operación semanal/mensual:**
   - Ajustar plantillas de viaje según demanda.
   - Agregar nuevos feriados al calendario.
   - Renovar licencias de conductores próximas a vencer.

### 3.6 Build local

```bash
cd /root/appmovilidadclinica/admin
npm install
npm run build       # genera dist/ con bundle estático
npm run dev         # dev server con HMR (Vite)
```

El deploy copia `dist/` al VPS (ruta servida por Nginx).

---

## 4. App del Pasajero (Android)

### 4.1 Acceso

- **Package:** `com.appmovilidadclinica.passenger`
- **Path:** `/root/appmovilidadclinica/passenger-android/`
- **Instalación:** APK firmado (a generar con `./gradlew assembleRelease`). No hay aún en Play Store.
- **Login:** documento + contraseña. Endpoint `POST /api/auth/login`.

### 4.2 Stack

- **Kotlin 2.0.21** + **Jetpack Compose** + **Material 3**.
- **Clean Architecture** + MVVM (3 capas: `presentation/`, `domain/`, `data/`).
- **Hilt** (DI).
- **Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization** (networking, sin reflection).
- **Coroutines + Flow** (async).
- **Room** (persistencia de reservas — necesario porque el `qr_token` se entrega una sola vez).
- **DataStore Preferences** (sesión/token).
- **ZXing core** (generación de QR — solo encoding, no escaneo).
- **Navigation Compose 2.8** (rutas tipadas `@Serializable`).
- **Min SDK:** 26 (Android 8.0).

### 4.3 Funcionalidades

1. **Login** (`/login`)
   - Documento + contraseña.
   - Validación: solo permite rol WORKER (ADMIN/DRIVER ven mensaje "Esta app es para trabajadores").
   - JWT de 24h, persistido en DataStore.

2. **Búsqueda de viajes** (`/trips/search`)
   - Formulario: fecha + dirección (IDA/VUELTA) + origen + destino.
   - Filtros combinados: `GET /api/trips?date=&direction=&origin=&destination=`.
   - Lista de viajes disponibles con horario, vehículo, placa, asientos libres.
   - Estados de reserva: `NOT_OPEN` (aún no abre la ventana), `OPEN` (puede reservar), `CLOSED` (ventana cerrada).
   - Pull-to-refresh.

3. **Selección de asiento** (`/trips/{id}/seats`)
   - Carga `GET /api/trips/{id}` (cabecera + cronograma) y `GET /api/trips/{id}/seats?origin=&destination=`.
   - Grilla visual de asientos: AVAILABLE (verde), OCCUPIED_IN_REQUESTED_RANGE (rojo), BLOCKED (gris).
   - Validación client-side: origen < destino en orden de paradas (si no, error sin llamar al backend).

4. **Confirmación de reserva** (`POST /api/reservations`)
   - Body: `{trip_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id}`.
   - Backend devuelve `{reservation_id, reservation_code, qr_token, status: "CONFIRMED"}`.
   - **Crítico:** el `qr_token` (UUID crudo) se persiste en Room **antes que cualquier otra cosa** — el backend nunca lo vuelve a entregar.
   - Navega a pantalla de detalle con el QR.

5. **Mis reservas** (`/my-reservations`)
   - Lista de reservas del usuario, desde Room (offline-friendly).
   - Tap → detalle.

6. **Detalle de reserva** (`/my-reservations/{id}`)
   - **QR visible** (generado on-device con ZXing desde el `qr_token`).
   - Datos: código de reserva, ruta, fecha, horario, asiento, paradas.
   - **Botón "Confirmar abordaje"** (self check-in): ventana de ±30 minutos alrededor del horario de salida de la parada de origen. **Este endpoint aún no existe en backend** (`POST /api/reservations/{id}/self-checkin` está documentado pero pendiente de implementar).
   - **Botón "Cancelar reserva"**: solo aparece para reservas propias (filtro client-side).

7. **Sesión**
   - Banner de aviso T-2min antes de expirar.
   - Logout forzado al recibir 401 (vía `SessionExpiredNotifier` SharedFlow).

### 4.4 Build

```bash
cd /root/appmovilidadclinica/passenger-android
gradle wrapper                                # si no hay wrapper
./gradlew assembleDebug                       # APK debug
./gradlew assembleRelease                     # APK release (firmar aparte)
```

El proyecto apunta a `https://sitechfactura.site/api/` hardcoded en `di/NetworkModule.kt` (constante `API_BASE_URL`).

### 4.5 Estado actual

- ✅ Login, búsqueda, selección de asiento, reserva + QR, cancelación, countdown de sesión: **funcionales**.
- ⚠️ Self check-in manual: código escrito, **falta endpoint backend** (`POST /reservations/{id}/self-checkin`).
- ⚠️ Selector de origen/destino en búsqueda: código escrito, **falta endpoint backend** (`GET /api/stops` para WORKER).
- ⚠️ No compilado aún (generado por IA sin toolchain). Falta abrir en Android Studio, resolver errores y generar el ícono de launcher.

---

## 5. App del Conductor (Android)

### 5.1 Acceso

- **Package:** `com.appmovilidadclinica.driver` (proyectado).
- **Path:** `/root/appmovilidadclinica/driver-android/`
- **Login:** documento + contraseña, **rol DRIVER**. La app bloquea otros roles con mensaje "Esta app es para conductores".

### 5.2 Stack

Idéntico al pasajero (Kotlin + Compose + Hilt + Retrofit + CameraX + ML Kit). Cambia:

- **ML Kit Barcode Scanning** (`com.google.mlkit:barcode-scanning`) + **CameraX**: escaneo QR en vivo.
- **Sin Room** (DataStore alcanza para token; el conductor no persiste QRs).
- **Sin ZXing** (solo lee, no genera).

### 5.3 Funcionalidades

1. **Login** (`/login`)
   - Igual al pasajero. Bloquea roles != DRIVER.

2. **Dashboard del día** (`/dashboard`)
   - `GET /api/driver/trips?date=YYYY-MM-DD` (default hoy).
   - Tarjetas ordenadas por horario de salida: ruta + dirección + vehículo + placa + estado.
   - Badge de color por estado (DRAFT gris, PUBLISHED azul, BOARDING naranja, IN_PROGRESS verde, COMPLETED gris oscuro, CANCELLED rojo).
   - Selector de fecha con flechas "día anterior / día siguiente" + pull-to-refresh.
   - Sin viajes → "No tienes viajes asignados para hoy".

3. **Detalle de viaje** (`/trip/{id}`)
   - Cabecera + lista de pasajeros (`GET /api/driver/trips/{id}/passengers`).
   - Pasajeros agrupados por parada de origen (orden ascendente).
   - Cada pasajero: nombre, asiento (ej: "3A"), origen → destino, estado, tiempo desde confirmación.
   - Acciones por pasajero según estado:
     - CONFIRMED → botones **Board** o **No-Show**.
     - BOARDED → botón **Alight** (bajada).
   - Cronograma de paradas con botón **"Marcar llegada"** en cada PENDING.
   - Botón flotante **"Escanear QR"** (abre cámara).

4. **Marcaje de llegada** (`POST /api/driver/trip-stops/{id}/arrival`)
   - Por cada paradero en estado PENDING.
   - Confirmación con diálogo.
   - 204 → actualiza estado local a ARRIVED.

5. **Escaneo QR** (overlay con cámara, no ruta Nav separada)
   - CameraX + ML Kit en preview portrait.
   - Al detectar código: feedback háptico + llamada a `POST /api/reservations/verify-qr`.
   - Overlay con datos del pasajero (nombre, código, asiento, paradas).
   - Acciones post-scan según estado: Board / No-Show / Alight.
   - Error 404 → toast "QR inválido", cámara sigue abierta.
   - Permiso CAMERA solicitado en runtime la primera vez.

6. **Reporte de incidencias** (`/trip/{id}/incident`)
   - Formulario: tipo (BREAKDOWN / DELAY / ACCIDENT / OTHER, con icono) + descripción (multilínea, ≤1000 chars, contador).
   - Validación client-side.
   - `POST /api/driver/trips/{id}/incidents` → 201 `{id}` → toast "Incidencia reportada (#N)".

7. **Perfil** (`/profile`)
   - Datos del login response (full_name, employee_code, role, department, phone) + licencias (`driver_license_number`, `driver_license_category`, `driver_license_expires_on`).
   - Total de viajes del día (derivado del dashboard).
   - Botón "Cerrar sesión".

8. **Sesión**
   - Idéntico al pasajero: JWT 24h, countdown T-2min, logout forzado por 401.

### 5.4 Estado actual

- Diseño completo (SDD detallado en `desarrollo_conductor.md`).
- **No implementado todavía** — el módulo `driver-android/` solo tiene el scaffoldeado inicial.
- Backend listo para soportar todas las features (endpoints `/api/driver/*` están operativos).
- **Gaps de backend detectados** (a cerrar antes de empezar la app):
  1. Campos de licencia no expuestos en `users` struct (admin CRUD + auth login response) — el seed ya los carga, pero Go no los lee.
  2. `verify-qr` sin guard de rol — cualquier JWT válido puede verificar QRs ajenos (debe restringirse a DRIVER/ADMIN).

### 5.5 Endpoints que consume

| Método | Path | Función |
|---|---|---|
| POST | `/api/auth/login` | Login |
| GET | `/api/driver/trips?date=` | Dashboard |
| GET | `/api/driver/trips/{id}/passengers` | Lista pasajeros |
| POST | `/api/driver/trip-stops/{id}/arrival` | Marcaje llegada |
| POST | `/api/driver/reservations/{id}/board` | Abordaje |
| POST | `/api/driver/reservations/{id}/no-show` | No presentado |
| POST | `/api/driver/reservations/{id}/alight` | Bajada |
| POST | `/api/reservations/verify-qr` | Validar QR |
| POST | `/api/driver/trips/{id}/incidents` | Reportar incidencia |

---

## 6. Flujos end-to-end

### 6.1 Reserva de un trabajador (caso feliz)

```
[1] WORKER abre app pasajero → login con documento+contraseña
        ↓
    POST /api/auth/login → JWT 24h + datos user (rol verificado)
        ↓
[2] WORKER busca viaje del día
        ↓
    GET /api/trips?date=&direction=IDA&origin=stop1&destination=stop2
        ↓
    Backend: sp_search_trips(origen, destino, fecha)
        ↓
    Devuelve viajes PUBLISHED con asientos disponibles por tramo
        ↓
[3] WORKER selecciona asiento
        ↓
    GET /api/trips/{id} → cabecera + cronograma
    GET /api/trips/{id}/seats?origin=&destination= → grilla
        ↓
[4] WORKER confirma reserva
        ↓
    POST /api/reservations {trip_id, trip_seat_id, origin, destination}
        ↓
    Backend valida: 1 reserva activa por worker/viaje + ventana + reglas IDA/VUELTA
        ↓
    sp_confirm_reservation bloquea trip_seat_segments en transacción InnoDB
        ↓
    Devuelve {reservation_id, reservation_code, qr_token (UUID), status: CONFIRMED}
        ↓
[5] App persiste qr_token en Room (CRÍTICO — solo se entrega una vez)
        ↓
[6] WORKER ve QR en pantalla "Mis reservas" (generado on-device con ZXing)
```

### 6.2 Abordaje con QR (caso feliz)

```
[7] DRIVER abre app conductor → login
        ↓
    Ve sus viajes del día en dashboard
        ↓
[8] DRIVER toca un viaje → ve lista de pasajeros + cronograma
        ↓
[9] Llega al primer paradero → "Marcar llegada"
        ↓
    POST /api/driver/trip-stops/{id}/arrival
        ↓
    sp_mark_trip_stop_arrival(actual_arrival_at = NOW)
        ↓
    Se inicia el cronómetro de tolerancia de no-show
        ↓
[10] WORKER sube al bus → muestra QR al conductor
        ↓
[11] DRIVER escanea QR con la app
        ↓
    ML Kit detecta el código → token crudo (UUID)
        ↓
    POST /api/reservations/verify-qr {token}
        ↓
    Backend: SHA-256(token) == qr_token_hash → match
    Devuelve {reservation_id, status: CONFIRMED, ...}
        ↓
[12] App muestra overlay con datos del pasajero + botón "Board"
        ↓
    DRIVER toca "Board"
        ↓
    POST /api/driver/reservations/{id}/board → 204
        ↓
    sp_mark_reservation_boarded → status = BOARDED
        ↓
[13] Al llegar al destino → botón "Alight"
        ↓
    POST /api/driver/reservations/{id}/alight → 204
        ↓
    sp_mark_reservation_alighted → status = COMPLETED
```

### 6.3 No-show (pasajero no se presenta)

```
[8-9] Idéntico al flujo anterior hasta marcaje de llegada
        ↓
[10'] WORKER NO se presenta en la parada dentro de la tolerancia
        ↓
    (Mecanismo 1) El job Go verifica reservas CONFIRMED en esa parada
    cuyo actual_arrival_at + no_show_tolerance_minutes < NOW
        ↓
    sp_mark_reservation_no_show → status = NO_SHOW + libera trip_seat_segments
        ↓
    (Mecanismo 2) El DRIVER marca manualmente desde la app:
    POST /api/driver/reservations/{id}/no-show → 204
```

### 6.4 Cancelación por el pasajero

```
[WORKER] En "Mis reservas" → tap "Cancelar reserva"
        ↓
POST /api/reservations/{id}/cancel → 204
        ↓
Backend libera trip_seat_segments + status = CANCELLED
        ↓
⚠️ El backend NO valida que quien cancela sea el dueño
   (gap de seguridad — mitigado client-side ocultando el botón)
```

### 6.5 Reporte de incidencia

```
[DRIVER] En detalle de viaje → "Reportar incidencia"
        ↓
Completa formulario (tipo + descripción)
        ↓
POST /api/driver/trips/{id}/incidents → 201 {id: N}
        ↓
sp_create_incident → trip_incidents row
        ↓
Admin lo ve en panel web /incidents
```

### 6.6 Generación automática de viajes

```
[Backend] Goroutine ticker cada 6h
        ↓
Inserta trip_generation_runs (status=RUNNING)
        ↓
Recorre trip_templates activas × fechas del horizonte (30 días)
        ↓
fn_service_operates(calendar_id, fecha) → si no opera, skip
        ↓
sp_generate_trip_instance(template_id, fecha, run_id) → idempotente
        ↓
Para cada tramo: fn_select_travel_time_profile por prioridad
        ↓
Genera trip_stop_times, trip_segments, trip_seats, trip_seat_segments
        ↓
Valida solapamientos con vw_schedule_conflicts
        ↓
Publica (status=PUBLISHED) o deja DRAFT según automatic_publish
        ↓
Cierra trip_generation_runs con contadores
```

---

## 7. Reglas de negocio clave

### 7.1 Reserva por tramos (no por viaje completo)

Los asientos se reservan por **segmentos** entre orden de subida y orden de bajada, no para todo el viaje. Esto permite reutilización escalonada:

> Si WORKER A reserva asiento 5 desde Sede (orden 1) hasta Paradero 2 (orden 3), WORKER B verá el asiento 5 ocupado al inicio pero **100% disponible a partir del Paradero 2** en adelante.

### 7.2 Reglas direccionales estrictas

- **IDA:** subida solo en PARADERO → bajada obligatoria en SEDE.
- **VUELTA:** subida obligatoria en SEDE → bajada solo en PARADERO.

El backend rechaza con 409 cualquier combinación que no cumpla. La app pasajero valida esto client-side **antes** de llamar al backend (`ListSeatsUseCase.kt`).

### 7.3 Una reserva activa por viaje por trabajador

Hay backstop de índice único en BD además del chequeo en Go. No se puede burlar con reintentos rápidos.

### 7.4 QR token: una sola entrega

El backend entrega el `qr_token` (UUID crudo) **solo al confirmar la reserva**. En BD solo se guarda `SHA256(qr_token)`. **Si la app pierde este dato antes de persistirlo, el QR se pierde para siempre.** Por eso Room persiste como primera línea.

### 7.5 Sistema anti-retrasos (tolerancia)

- El cronómetro de no-show **NO** inicia con la hora programada.
- Inicia solo cuando el conductor marca `actual_arrival_at` en la parada.
- Si el conductor olvida marcar llegada → modo contingencia: protege todas las reservas de ese paradero (asume demora indefinida).

### 7.6 Ventana de reserva

Cada viaje tiene `booking_opens_at` y `booking_closes_at`. Solo se puede reservar dentro de esa ventana. El backend lo calcula contra `CURRENT_TIMESTAMP` (la app no recalcula localmente para evitar desincronización de reloj).

### 7.7 Estados de reserva

```
CONFIRMED → BOARDED → COMPLETED
    ↓           ↓
NO_SHOW     CANCELLED
```

### 7.8 Estados de viaje

```
DRAFT → PUBLISHED → BOARDING → IN_PROGRESS → COMPLETED
                           ↘ CANCELLED
```

### 7.9 Estados de parada

```
PENDING → ARRIVED → DEPARTED
              ↘ SKIPPED
```

---

## 8. Usuarios y roles

| Rol | Login | Accede a | Puede |
|---|---|---|---|
| **ADMIN** | `/api/auth/login` | Panel web | Gestionar todo el sistema |
| **DRIVER** | `/api/auth/login` | App conductor (próximamente) | Ver viajes, marcar paraderos, validar QR, reportar incidencias |
| **WORKER** | `/api/auth/login` | App pasajero | Buscar viajes, reservar, ver QR, cancelar |

**JWT claims:** `user_id`, `role`, `full_name`, `employee_code`, `iat`, `exp` (24h).

**Usuarios demo** (seed `backend/scripts/seed_demo_data.sql`, password = `password`):
- `90000001` → ADMIN
- `90000002` → DRIVER (con licencia: `LIC-DEMO-001`, categoría `A-IIb`, vence `2028-04-15`)
- `90000003`, `90000004`, `90000005` → WORKER

⚠️ **Los datos demo se borran en cada restart** (schema con `DROP TABLE` + `CREATE TABLE` en cada boot). Para mantenerlos, recargar `seed_demo_data.sql` después de cada restart.

---

## 9. Acceso al sistema

### 9.1 URLs

| Componente | URL |
|---|---|
| Panel web admin | https://movilidad.sitech.site/ |
| API backend | https://sitechfactura.site/api/ |
| Health check | https://sitechfactura.site/api/health |

Ambos vhosts Nginx proxyean `/api/*` al backend Go en `:8080`. El panel web se sirve estáticamente desde `/admin/dist` en `movilidad.sitech.site`.

### 9.2 Login

- Mismo endpoint para las 3 interfaces: `POST /api/auth/login`.
- Body: `{document_number, password}`.
- 401 → credenciales inválidas (mensaje genérico, no distingue si falló doc o pass).
- El backend permite login de cualquier rol — cada cliente valida el rol después (panel web solo ADMIN, app pasajero solo WORKER, app conductor solo DRIVER).

### 9.3 Sesión

- JWT HS256, 24h, **sin refresh token**.
- Header: `Authorization: Bearer <token>`.
- Renovación = login de nuevo.

---

## 10. Operaciones técnicas frecuentes

### 10.1 Ver estado del backend

```bash
systemctl status appmovilidadclinica.service
journalctl -u appmovilidadclinica.service -f
tail -f /var/log/appmovilidadclinica/server.log
```

### 10.2 Rebuild + restart del backend

```bash
cd /opt/appmovilidadclinica/backend
/usr/local/go/bin/go build -ldflags="-s -w" -o bin/server ./cmd/server
chmod 755 bin/server
systemctl restart appmovilidadclinica.service
journalctl -u appmovilidadclinica.service -n 30   # confirmar arranque sin errores
```

### 10.3 Rebuild del admin

```bash
cd /root/appmovilidadclinica/admin
npm install
npm run build
# copiar dist/ al VPS:
rsync -avz dist/ usuario@server:/var/www/movilidad.sitech.site/
```

### 10.4 Recargar datos demo (BD se resetea en cada restart)

```bash
# 1. Backend ya reiniciado (aplica schema nuevo)
mariadb -u appuser -p transporte_corporativo_mvp < /root/appmovilidadclinica/backend/scripts/seed_demo_data.sql

# 2. Probar login
curl -X POST https://sitechfactura.site/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"document_number":"90000001","password":"password"}'
```

### 10.5 Reset completo de BD (destructivo)

```bash
mariadb -uroot -e "DROP DATABASE transporte_corporativo_mvp; CREATE DATABASE transporte_corporativo_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
systemctl restart appmovilidadclinica.service
```

### 10.6 Renovar SSL

```bash
certbot renew --dry-run   # simular
certbot renew             # aplicar (normalmente corre automático vía timer)
```

### 10.7 Build apps Android

```bash
cd /root/appmovilidadclinica/passenger-android
gradle wrapper                                 # si falta
./gradlew assembleRelease                       # APK release
./gradlew installDebug                          # instalar en device conectado

cd /root/appmovilidadclinica/driver-android
./gradlew assembleDebug
```

---

## Anexo A — Estructura del proyecto en disco

```
/root/appmovilidadclinica/
├── admin/                    # Vue 3 SPA — Panel web
│   ├── src/                  # Código fuente
│   ├── dist/                 # Build estático (servido por Nginx)
│   ├── .env                  # VITE_API_BASE_URL=/api
│   └── package.json
├── backend/                  # Go modular monolith
│   ├── cmd/server/main.go    # Entry point
│   ├── internal/
│   │   ├── modules/{auth,trips,booking,driver,admin}
│   │   └── platform/{database,server,jobs}
│   ├── migrations/           # Schema SQL + stored procedures
│   ├── scripts/              # seed_demo_data.sql
│   ├── go.mod, go.sum
│   └── bin/server            # Binario compilado (en deploy: /opt/.../backend/bin/)
├── driver-android/           # App nativa Android para DRIVER (en desarrollo)
├── passenger-android/        # App nativa Android para WORKER (casi completo)
├── ingress/                  # Configs de Ingress/Nginx
├── scripts/                  # Scripts auxiliares de deploy
├── openclawdev/              # Workspace OpenCode (desarrollo IA)
├── Documentacion/            # Documentos de referencia
│   ├── arquitectura_sistema.md
│   ├── explicacion_sistema.md
│   ├── diccionario_datos_transporte_mvp.md
│   ├── servidor.md
│   ├── transporte_corporativo_mvp.sql   # Schema completo
│   └── INSTRUCTIVO_SISTEMA.md           # Este documento
├── DEPLOY.md                 # Guía de despliegue al VPS
├── desarrollo_conductor.md   # SDD detallado de la app DRIVER
└── desarrollo_pasajero.md    # SDD detallado de la app WORKER
```

## Anexo B — Glosario

- **IDA / VUELTA:** dirección del viaje (hacia el trabajo / hacia casa).
- **SEDE / PARADERO:** tipos de parada (oficinas corporativas / paradas en la vía pública).
- **Trip instance:** un viaje específico generado (ej: "Ruta 01 IDA del 2026-09-15 a las 07:00").
- **Trip template:** plantilla recurrente que el generador expande en trip_instances.
- **Trip stop time:** una parada programada dentro de un viaje (con horario estimado de llegada/salida).
- **Trip seat segment:** bloqueo atómico de un asiento en un rango de paradas (la unidad mínima de reserva).
- **Booking group UUID:** agrupa informativamente las reservas IDA+VUELTA de un mismo viaje round-trip (no es atómico).
- **No-show tolerance:** minutos que espera el sistema después de la llegada real del bus antes de marcar NO_SHOW.

---

*Documento generado para uso operativo y de onboarding.*
*Para detalles técnicos profundos ver:*
- `Documentacion/arquitectura_sistema.md` — arquitectura técnica completa
- `Documentacion/diccionario_datos_transporte_mvp.md` — diccionario de las 22 tablas
- `desarrollo_pasajero.md` — SDD de la app WORKER
- `desarrollo_conductor.md` — SDD de la app DRIVER
- `DEPLOY.md` — guía de despliegue