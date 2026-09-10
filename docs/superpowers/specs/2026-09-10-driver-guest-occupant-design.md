# Registro de pasajero sin app por el conductor — Design

Fecha: 2026-09-10. Estado: propuesto, pendiente de revisión.

## 1. Contexto

El conductor necesita asignar un asiento a una persona que está físicamente
presente pero no tiene la app (sin usuario ni reserva). Hoy es imposible:
toda reserva exige un `worker_id` real (`reservations.worker_id`), el
endpoint de confirmación toma el worker del JWT, y el app del conductor no
tiene mapa de asientos (tiene detalle del viaje con lista de pasajeros, QR e
incidentes). Los asientos en el backend son por tramo
(origen→destino dentro del viaje).

Decisiones del cliente: solo nombre y apellido (sin DNI, "muy invasivo"),
sin crear usuario; flujo desde mapa de asientos; unicidad "mismo nombre +
mismo viaje" (case-insensitive); el conductor elige parada de subida y
bajada.

## 2. Objetivo

El conductor, desde el detalle de su viaje, elige tramo (subida/bajada), ve
el mapa con disponibilidad de ese tramo, toca un asiento libre, ingresa
nombre y apellido, y el asiento queda ocupado a nombre del invitado. El
invitado cuenta para disponibilidad y aparece en la lista de pasajeros.

## 3. No-objetivos (v1)

- Crear usuario para el invitado (no podrá usar la app después con esos datos).
- DNI o cualquier documento del invitado.
- Editar o liberar al invitado (si se equivoca, lo resuelve el admin en BD; fase 2).
- Reporte separado de invitados en el admin.
- Reutilizar el mapa del pasajero por código compartido (son módulos
  distintos: `passenger-android` vs `driver-android` KMP; se replica el
  patrón visual, no el código).

## 4. Backend

Nueva tabla `guest_occupants` (agregar a `migrations/0001_schema.up.sql`,
que el sistema recrea en cada arranque — procedimiento establecido):

```
guest_occupants (
  id, trip_id, trip_seat_id,
  origin_trip_stop_time_id, destination_trip_stop_time_id,
  first_name, last_name,
  registered_by_user_id,   -- el conductor que lo registró
  status,                  -- nace 'BOARDED' (está físicamente ahí)
  created_at
)
```

Nuevo endpoint en el módulo `driver`:

```
POST /api/driver/guest-occupants   (requiere JWT, rol DRIVER)
Body: { trip_id, trip_seat_id, origin_trip_stop_time_id,
        destination_trip_stop_time_id, first_name, last_name }
→ 201 { "id": <n> }
```

Reglas del servicio (mismo estilo que el resto del módulo):

1. `requireDriver` + conductor asignado al viaje (`ensureAssigned`).
2. Nombres requeridos, 2–100 caracteres cada uno.
3. Tramo válido: ambas paradas pertenecen al viaje y origen < destino.
4. Asiento pertenece al vehículo del viaje.
5. Asiento libre en el tramo: sin reserva `CONFIRMED/BOARDED` solapada y sin
   otro invitado solapado → 409 "asiento ocupado".
6. Mismo nombre + apellido ya registrado en el viaje (case-insensitive)
   → 409 "ese pasajero ya está registrado en este viaje".
7. Inserta con `status = 'BOARDED'`, `registered_by_user_id` = conductor.

La disponibilidad de asientos (`GET /api/trips/{id}/seats`) debe excluir los
tramos ocupados por invitados (union con reservas) — verificar en
implementación si ese query vive en el módulo trips y extenderlo, o
resolverlo en el servicio driver.

La lista de pasajeros del conductor (`GET
/api/driver/trips/{id}/passengers`) incluye a los invitados marcados como
tales (p. ej. campo `guest: true` + nombre), para que el conductor los vea.

Tests: éxito, asiento ocupado → 409, nombre duplicado → 409, conductor no
asignado → 403, rol no-DRIVER → 403. Mock a mano.

## 5. App conductor (KMP `driver-android/shared`)

- **Entrada**: botón "Ocupar asiento" en `TripDetailScreen` (sección
  pasajeros).
- **Nueva ruta** `Route.SeatMap(tripId)` + `SeatMapScreen`:
  1. Selectores de subida/bajada (de los `TripStop` ya cargados; default:
     primera parada con llegada pendiente → última).
  2. Mapa de asientos para ese tramo (grilla, patrón visual del pasajero:
     libre / ocupado / seleccionado; datos de disponibilidad del backend).
  3. Tap en libre → diálogo "Pasajero sin app": nombre + apellido →
     confirmar → toast + volver al detalle (que refresca pasajeros).
- **Capa data**: llamadas en `DriverApi`/`DriverRepository` existentes
  (Koin, mismo estilo que `IncidentScreen`/`IncidentViewModel`).
- **Validaciones cliente**: tramo elegido, asiento elegido, nombre y apellido
  no vacíos; errores inline; sin perder lo escrito ante error del backend
  (409 muestra el mensaje tal cual).

## 6. Flujo de datos

Detalle viaje → "Ocupar asiento" → elige tramo → mapa (disponibilidad con
reservas + invitados) → tap asiento → nombre/apellido → `POST
/api/driver/guest-occupants` (Bearer automático) → 201 → toast + back +
refresh de pasajeros. El admin ve la ocupación vía disponibilidad y lista de
pasajeros.

## 7. Testing

- Backend: unit tests del servicio + curl con token DRIVER (201, 409 por
  duplicado y por asiento ocupado).
- App: QA manual en emulador (mapa, diálogo, errores, refresh). Sin tests UI
  (el driver tampoco tiene esa infraestructura).

## 8. Rollout

Desplegar el backend ANTES o junto con la app (si no: 404). Migración
incluida en `0001_schema.up.sql`, que se aplica sola al reiniciar (pero
recrea el schema: coordinar con datos existentes igual que siempre).
Rama de trabajo: `feat/user-extra-fields`.
