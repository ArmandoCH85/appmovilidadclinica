# Semáforo de Paradas — Driver marca, Pasajero ve en tiempo real

**Date:** 2026-08-27
**Status:** Approved
**Author:** Senior Architect
**Scope:** `backend/` (Go), `driver-android/` (Kotlin Multiplatform), `passenger-android/` (Android Compose)

## Goal

El conductor de un viaje en curso marca **llegada y salida** en cada paradero desde la pantalla de detalle. El pasajero, en su detalle de reserva, ve un **semáforo de colores** (🔴 PENDIENTE → 🟡 ARRIVED → 🟢 DEPARTED) que se actualiza automáticamente cada 20 segundos mientras el viaje está activo.

El backend ya tiene el endpoint `/arrival`; este feature agrega `/departure` y conecta el cable visual del semáforo.

## Background

**Hoy (antes del feature):**

| Pieza | Estado |
|---|---|
| `TripStopStatus` enum (`PENDING`/`ARRIVED`/`DEPARTED`/`SKIPPED`) en `:shared` | ✅ existe |
| Endpoint `GET /driver/trips/{id}/stops` | ✅ existe |
| Endpoint `POST /driver/trip-stops/{id}/arrival` (conductor marca llegada) | ✅ existe |
| Botón "Marcar llegada" en `TripDetailScreen` del conductor | ✅ existe |
| Endpoint `POST /driver/trip-stops/{id}/departure` | ❌ **NO existe** |
| Botón "Marcar salida" en `TripDetailScreen` del conductor | ❌ **NO existe** |
| Polling en `MyReservationDetailViewModel` del pasajero | ❌ carga 1 sola vez |
| Visualización con semáforo en `TripStopsTimeline` del pasajero | ❌ usa íconos, no semáforo |

**Decisiones de diseño tomadas con el usuario:**

1. Endpoint DEPARTED: **se agrega** nuevo endpoint en backend Go
2. Refresh strategy del pasajero: **polling cada 20 segundos**, sólo si viaje está `IN_PROGRESS` o `BOARDING`
3. Estado `SKIPPED`: gris tachado (igual que hoy, sin cambio)

## Design

### Cambio 1 — Backend Go: nuevo endpoint `/departure`

**Endpoint:** `POST /api/driver/trip-stops/{tripStopTimeId}/departure`

**Comportamiento:**
- Marca `trip_stop_time` con `actual_departure_at = NOW()` y `status = 'DEPARTED'`
- Solo el conductor asignado al viaje puede llamar (auth + ownership check)
- 200 OK: retorna el `TripStop` actualizado con todos los campos (mirror del shape de `/arrival`)
- 404: `trip_stop_time_id` no existe
- 409 Conflict: ya estaba `DEPARTED` (idempotencia)
- 422 Validation: si el paradero está en `PENDING` (no se puede marcar salida sin haber marcado llegada antes)

**Archivos backend estimados:**
- `backend/internal/modules/trips/handler.go` (o donde viva `markArrival`) — nuevo handler `markDeparture`
- `backend/internal/modules/trips/service.go` — lógica de validación + update
- `backend/internal/modules/trips/router.go` — registro de la ruta

### Cambio 2 — Driver App: agregar botón "Marcar salida"

**Archivos (driver-android/shared):**
- `data/remote/DriverApi.kt:46` — agregar función `markDeparture(tripStopTimeId: Long): HttpResponse`
- `domain/repository/DriverRepository.kt` — agregar `suspend fun markDeparture(tripStopTimeId: Long): Result<Unit>`
- `data/repository/DriverRepositoryImpl.kt` — implementar con safeApiCall
- `ui/screens/tripdetail/TripDetailViewModel.kt` — agregar `fun markDeparture(tripStopTimeId: Long)` (mirror de `markArrival`)
- `ui/screens/tripdetail/TripDetailScreen.kt:491-495` — refactorizar `StopRow`:

```kotlin
when (stop.status) {
    TripStopStatus.PENDING -> Button("Marcar llegada", onMarkArrival)
    TripStopStatus.ARRIVED -> Button("Marcar salida", onMarkDeparture)
    TripStopStatus.DEPARTED, TripStopStatus.SKIPPED -> {} // sin botón
}
```

**Manejo de error 422:** Toast "Primero marca la llegada antes de marcar la salida".

### Cambio 3 — Pasajero: semáforo visual + polling

**Archivos (passenger-android/app):**
- `presentation/myreservation/MyReservationDetailViewModel.kt:72-81` — reemplazar la carga única por polling con `LaunchedEffect`
- `presentation/myreservation/MyReservationDetailScreen.kt:269-309` — modificar `TripStopRow` para pintar círculo con color semáforo

**Lógica del semáforo:**

```kotlin
val semaphoreColor = when (stop.status) {
    TripStopStatus.PENDING  -> Color(0xFFD32F2F)  // rojo
    TripStopStatus.ARRIVED  -> Color(0xFFF9A825)  // amarillo
    TripStopStatus.DEPARTED -> Color(0xFF388E3C)  // verde
    TripStopStatus.SKIPPED  -> Color(0xFF9E9E9E)  // gris (sin cambio)
}
```

**Polling (ViewModel del pasajero):**

```kotlin
LaunchedEffect(state.tripStatus) {
    val isActive = state.tripStatus == TripStatus.IN_PROGRESS ||
                   state.tripStatus == TripStatus.BOARDING
    if (!isActive) return@LaunchedEffect
    while (isActive) {
        delay(20.seconds)
        refreshStops()
    }
}
```

`isActive` se vuelve `false` cuando el `LaunchedEffect` sale del scope (composition disposed). El polling se detiene automáticamente al cerrar la pantalla o cuando el viaje cambia a `COMPLETED`/`CANCELLED`.

**Pull-to-refresh:** se agrega `PullToRefreshBox` (Material 3) como bonus — el usuario puede forzar refresh inmediato.

### Cambio 4 — Edge cases (cubiertos en el código)

| Caso | Manejo |
|---|---|
| Conductor marca salida sin llegada previa | Backend 422 → toast "Primero marca la llegada" |
| Conductor marca 2 veces el mismo estado | Backend 409 → UI ignora silenciosamente (es idempotente) |
| Pasajero abre pantalla con viaje `COMPLETED` | Una sola carga, sin polling |
| Pasajero abre pantalla con viaje `CANCELLED` | Una sola carga + banner "Viaje cancelado" |
| Sin internet en pasajero | Toast "Sin conexión" + sigue mostrando último estado conocido del cache |
| GPS del conductor apagado | El botón funciona igual (acción manual, no depende de GPS) |
| Pasajero rota el dispositivo | El `StateFlow` mantiene el estado actual; el polling sigue funcionando |

## Files Changed

| Módulo | Archivos | Líneas estimadas |
|---|---|---|
| `backend/` Go | 3 archivos (handler, service, router) | ~80 |
| `driver-android/shared/` | 5 archivos (api, repo, impl, VM, Screen) | ~60 |
| `passenger-android/app/` | 2 archivos (VM, Screen) | ~80 |
| **Total** | **10 archivos** | **~220 líneas** |

## Testing

### Backend (Go)

- Unit test del nuevo endpoint con mock de DB:
  - Caso feliz: paradero PENDING → tap → DEPARTED con timestamp
  - 422 si está en PENDING
  - 409 si ya está DEPARTED
  - 404 si tripStopTimeId no existe
  - 403 si conductor no es el asignado al viaje

### Driver App (sin device, validación con checklist)

Checklist manual en device:
1. Tap "Marcar llegada" → backend 200 → paradero cambia a ARRIVED
2. Tap "Marcar salida" en paradero ARRIVED → backend 200 → cambia a DEPARTED
3. Tap "Marcar salida" sin haber marcado llegada → toast "Primero marca la llegada"
4. Verificar que `actualDepartureAt` aparece en la lista después del refresh

### Pasajero App (sin device, validación con checklist)

Checklist manual en device:
1. Abrir pantalla → primer load → todos los paraderos en 🔴 ROJO
2. Esperar 20s → polling dispara → verifica en logcat que se hizo GET /trips/{id}
3. Cambiar paradero a ARRIVED desde otro device (conductor) → esperar 20s → semáforo cambia a 🟡 AMARILLO en pasajero
4. Cambiar a DEPARTED desde otro device → esperar 20s → 🟢 VERDE
5. Pull-to-refresh → actualiza inmediato
6. Viaje `COMPLETED` → polling NO dispara (verificar en logcat)
7. Rotar el dispositivo → estado se mantiene

## Out of Scope

- WebSocket / FCM push para tiempo real (sólo polling por ahora)
- Notificación al pasajero cuando el bus está por llegar a SU parada
- ETA dinámica basada en el último paradero marcado (sólo mostramos el último estado conocido)
- Cambios al admin web (no requiere ver el semáforo)
- Tests automatizados UI (no hay infra de UI tests configurada)

## Risks

| Riesgo | Mitigación |
|---|---|
| Polling cada 20s gasta batería | 20s es razonable para tracking en tiempo real; si es problema, configurable vía constante |
| Polling dispara requests cuando la pantalla está en background | `LaunchedEffect` se cancela al disposal, no hay background polling |
| Backend de Go no soporta transacciones para arrival+departure | Cada endpoint es atómico en su propio SQL UPDATE; no hay race conditions |
| Si conductor marca 2 paraderos en rápida sucesión (llegada paradero 2 + salida paradero 1) | Backend acepta porque valida por `trip_stop_time_id`, no por orden temporal |
| Conflicto de versiones de Go modules | Irrelevante, mismo repo |
| Memoria cache del conductor puede estar stale | El `TripDetailViewModel` ya hace refresh después de `markArrival`; agregamos el mismo patrón para `markDeparture` |

## Done When

- [ ] Endpoint `/departure` agregado y testeado en backend
- [ ] Conductor ve botón "Marcar salida" en paraderos ARRIVED
- [ ] Conductor ve toast de error 422 si intenta marcar salida sin llegada
- [ ] Pasajero ve semáforo con 3 colores (rojo/amarillo/verde) + gris para SKIPPED
- [ ] Pasajero hace polling automático cada 20s cuando viaje está activo
- [ ] Pasajero puede hacer pull-to-refresh manual
- [ ] Polling se detiene automáticamente al cerrar la pantalla o terminar el viaje
- [ ] Sin memory leaks (LaunchedEffect limpio)
- [ ] Checklist de aceptación ejecutado en device por el usuario
- [ ] Bundle firmado para Play Store + APK interno del driver para testing
