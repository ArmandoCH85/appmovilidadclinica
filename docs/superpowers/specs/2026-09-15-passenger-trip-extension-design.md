# Extensión de viaje del pasajero y bajada automática — Design

Fecha: 2026-09-15. Estado: propuesto, pendiente de revisión.

## 1. Contexto

Hoy el viaje del pasajero se "sigue" con un **semáforo por parada** en la app
(`MyReservationDetailScreen.kt:294`: `PENDING` rojo, `ARRIVED` amarillo,
`DEPARTED` verde, `SKIPPED` gris). Ese semáforo se mueve cuando el conductor
marca llegada/salida (`POST /api/driver/trip-stops/{id}/arrival|departure`),
pero:

- Marcar llegada/salida **solo** escribe en `trip_stop_times`
  (`sp_mark_trip_stop_arrival`, `0001_schema.up.sql:1903`). No toca reservas ni
  asientos. No hay triggers.
- La **bajada del pasajero es 100% manual del conductor**
  (`POST /api/driver/reservations/{id}/alight` →
  `sp_mark_reservation_alighted`, `0001_schema.up.sql:2245`).
- **No existe tiempo real**: sin WebSocket, SSE, FCM ni polling. La app solo
  refresca al volver a la pantalla (`RESUMED`) o con pull-to-refresh.
  `users.fcm_token` existe pero está muerta (ningún código la lee/escribe).
- El asiento se modela **por tramos** (`trip_seat_segments`:
  `AVAILABLE | RESERVED | OCCUPIED | USED | BLOCKED`). La disponibilidad se
  calcula por rango *half-open* `[origin_order, destination_order)`
  (`sp_list_trip_seats`, `0001_schema.up.sql:1447`). Un asiento liberado en un
  tramo queda libre para los tramos siguientes.
- Regla vigente: **1 reserva activa por worker por viaje**, garantizada por el
  índice único `(trip_id, active_worker_id)` (migración `0003`). El pasajero
  `COMPLETED/CANCELLED/NO_SHOW` produce `NULL` y no bloquea.
- **"Reserva en espera" / lista de espera NO existe.** El diccionario lo dice
  explícito (`Documentacion/diccionario_datos_transporte_mvp.md:18`: "No se
  incluyen en el MVP: … lista de espera"). Grep de
  `waitlist|standby|reserva en espera` en todo el repo: cero resultados. En
  este diseño, "reserva en espera" = **la reserva de OTRO pasajero para el
  tramo siguiente**, no una entidad nueva.

## 2. Objetivo

1. Cuando el bus **sale del paradero anterior** al destino del pasajero, el
   sistema le avisa en la app.
2. En ese aviso se evalúa su asiento para el tramo siguiente:
   - **Libre** → puede extender el viaje; elige nuevo destino (dropdown con los
     paraderos que faltan marcar) y confirma (mismo asiento u otro libre).
   - **Tomado por otro** → se le avisa "el asiento ya se asignó a otra persona"
     (sin nombre) y se le ofrecen **otros asientos libres**; si no hay ninguno,
     no se ofrece extensión.
3. Al confirmar, **se actualiza la misma reserva** al nuevo destino y se
   reservan los tramos extra. **No se vuelve a pedir abordaje** (ya está
   arriba del bus).
4. Al **llegar** el bus al destino del pasajero, el sistema lo **baja solo** y
   libera el asiento. Si el pasajero extendió, la bajada se dispara en el
   nuevo destino.
5. El semáforo se actualiza **en vivo**.
6. Los cambios se ven en el reporte **"Disponibilidad de asientos"** del admin.

## 3. No-objetivos (v1)

- **Push (FCM) y WebSocket.** Se usa polling; push queda como fase 2 sin
  rehacer nada (el polling pasa a ser respaldo).
- **Saltar paraderos** (`SKIPPED`): el enum existe pero nada lo setea. Fuera de
  alcance.
- **Cambios en la app del conductor**: ya marca llegada/salida.
- **Pagos o diferencias de tarifa**: el MVP no tiene pagos.
- **Lista de espera real**: descartada (ver §1).
- **Reporte nuevo aparte**: se enriquece el existente.

## 4. Decisiones de diseño

| Decisión | Elegido | Alternativas descartadas |
|---|---|---|
| Canal en vivo | **Polling cada 20s** mientras la reserva está `BOARDED` | Push (mucha infra: Firebase + emisor + tokens) · WebSocket (hub + reconexión, y no sirve con app cerrada) |
| Cerebro | **Backend** calcula elegibilidad y expone el estado | App calcula (lógica duplicada y frágil, choca con el guard de `0003`) |
| Extensión | **Actualiza la reserva existente** | Crear reserva nueva (choca con el guard, ensucia reportes) · Tabla propia de extensiones (over-engineering) |
| Bajada automática | **En la LLEGADA** al paradero destino | En la salida (más tarde, el pasajero ya se fue) |
| Sin respuesta | **Se comporta como "no"** → baja solo en su destino | Reintentar el aviso hasta el destino |
| Viaje mal cerrado | Al completar el viaje, **cerrar como `COMPLETED`** las reservas `BOARDED` que quedaron | Dejarlas colgadas para admin |

## 5. Backend

### 5.1 Migración

El enum de `reservation_events.event_type` **ya es modificado por
`0002_cancel_sps.up.sql:20-24`** (`CONFIRMED, BOARDED, ALIGHTED, NO_SHOW,
SEGMENTS_RELEASED, CANCELLED`). Como las migraciones se aplican en orden
alfabético y `0002` corre después de `0001`, agregar `EXTENDED` **solo en
`0001` no alcanza**. Nueva migración `0006_trip_extension.up.sql` que:

1. `ALTER TABLE reservation_events MODIFY COLUMN event_type ENUM(... , 'EXTENDED')`.
2. Crea `sp_extend_reservation` (ver §5.3).
3. (Opcional) extiende `vw_trip_segment_seat_availability` para reportes.

Y se **modifica `sp_mark_trip_stop_arrival` en `0001_schema.up.sql`** para
inlinear la bajada automática (ver §5.4). Se inlinea en lugar de llamar a otro
SP porque MySQL no soporta transacciones anidadas — mismo criterio ya
documentado en `0002_cancel_sps.up.sql:6-10`.

### 5.2 `GET /api/reservations/{id}/journey` (JWT, rol WORKER)

Endpoint liviano para el polling. Devuelve el estado que la pantalla de detalle
necesita, en una sola llamada:

```json
{
  "reservation_id": 1,
  "reservation_status": "BOARDED",
  "trip_id": 5,
  "trip_status": "IN_PROGRESS",
  "last_departed_stop_order": 3,
  "destination_stop_order": 4,
  "stops": [
    { "trip_stop_time_id": 41, "stop_name": "PARADERO_2", "stop_order": 3,
      "status": "DEPARTED", "actual_arrival_at": "…", "actual_departure_at": "…" }
  ],
  "can_extend": true,
  "extension": {
    "current_seat_free": true,
    "remaining_stops": [
      { "trip_stop_time_id": 44, "stop_name": "SEDE_A", "stop_order": 5 }
    ]
  }
}
```

Reglas:

1. La reserva debe existir y pertenecer al `worker_id` del JWT; si no, **404**
   (no filtrar existencia ajena).
2. `stops` sale de `trip_stop_times` ordenado por `stop_order`.
3. `last_departed_stop_order` = `MAX(stop_order)` con `status = 'DEPARTED'`
   (`NULL` si aún no salió ninguno).
4. `can_extend` = `true` **solo si** se cumple todo:
   - `reservation.status = 'BOARDED'`
   - `trip.status = 'IN_PROGRESS'`
   - `destination_stop_order = last_departed_stop_order + 1` (está "a un
     paradero de bajar")
   - existe al menos un paradero restante (`stop_order > destination_stop_order`
     y `status <> 'DEPARTED'`)
   - existe al menos un asiento libre en el tramo al paradero **inmediato
     siguiente**
5. `remaining_stops` = paradas del viaje con `stop_order > destination_stop_order`
   y `status <> 'DEPARTED'` (solo los que faltan marcar).
6. `current_seat_free` = el asiento actual está `AVAILABLE` en el tramo al
   paradero inmediato siguiente. `next_stop_order` = el menor `stop_order`
   mayor a `destination_stop_order` entre las paradas no `DEPARTED`.
7. La elegibilidad **no se persiste**: es derivada, se calcula en cada poll.

### 5.3 `POST /api/reservations/{id}/extend` (JWT, rol WORKER)

```
Body: { "new_destination_trip_stop_time_id": <n>, "trip_seat_id": <n>? }
→ 200 { "reservation_id": 1, "destination_stop_order": 6, "trip_seat_id": 33,
        "seat_label": "12", "status": "BOARDED" }
```

`trip_seat_id` es opcional; si no viene, se usa el asiento actual.

Reglas (SP `sp_extend_reservation`, transaccional con `EXIT HANDLER`):

1. Reserva existe, pertenece al caller y `status = 'BOARDED'` → si no, 403/409.
2. Viaje `IN_PROGRESS`.
3. `new_destination` pertenece al viaje, `stop_order > destination_stop_order` y
   `status <> 'DEPARTED'` → si no, 400.
4. Asiento pertenece al viaje, no bloqueado, y `AVAILABLE` en
   `[destination_stop_order, new_destination_stop_order)` → si no, **409**
   ("el asiento ya se asignó a otra persona").
5. Actualiza `reservations.destination_trip_stop_time_id` y
   `destination_stop_order`.
6. Inserta los tramos nuevos en `reservation_segments` y marca
   `trip_seat_segments` como **`OCCUPIED`** (no `RESERVED`: el pasajero ya está
   arriba del bus).
7. Inserta `reservation_events (event_type = 'EXTENDED', actor_user_id = caller,
   details = {"from_stop_order": n, "to_stop_order": m, "trip_seat_id": x})`.
8. Si el asiento cambió, los tramos viejos `[origin, destination)` quedan
   `USED` en el asiento anterior (ya se viajaron) y no se tocan.

### 5.4 Bajada automática en la llegada

Se extiende `sp_mark_trip_stop_arrival` (`0001_schema.up.sql:1903`): después de
marcar la llegada, recorre **set-based** las reservas del viaje con
`destination_trip_stop_time_id = p_trip_stop_time_id` y `status = 'BOARDED'`, y
para cada una:

- `reservations.status = 'COMPLETED'`, `completed_at = CURRENT_TIMESTAMP`.
- `trip_seat_segments` `OCCUPIED → USED` para los tramos de esa reserva.
- `reservation_segments` `OCCUPIED → USED`.
- Inserta evento `ALIGHTED` con `details = {"auto": true}`.

Notas:
- Los pasajeros que **extendieron** ya no matchean (su destino cambió), así que
  no se los baja.
- Los `CONFIRMED` (nunca subieron) **no** se tocan: los maneja el job de
  no-show existente (`jobs/noshow_checker.go`).
- La acción manual del conductor (`alight`) sigue existiendo para casos raros.

### 5.5 Cierre al completar el viaje

El cierre hoy es un `UPDATE` en Go (`driver/repository.go:371-392`, sin SP). Se
extiende para que, al pasar el viaje a `COMPLETED`, cierre en la misma
transacción las reservas `BOARDED` que quedaron: `status = 'COMPLETED'`, tramos
a `USED` y evento `ALIGHTED` (`details = {"auto": true, "reason": "trip_completed"}`).

### 5.6 Errores

- `GET /journey`: 403 si no es tu reserva, 404 si no existe.
- `POST /extend`: 400 (destino inválido/ya marcado, o igual/anterior),
  403 (ajena), 404 (no existe), 409 (asiento tomado / estado inválido),
  422 (validación de campos).
- Idempotencia: extender al mismo destino ya seteado → 400 (no-op explícito).

## 6. App pasajero

- **Polling**: en `MyReservationDetailViewModel`, mientras la reserva esté
  `BOARDED` y el viaje `IN_PROGRESS`, consultar `GET /reservations/{id}/journey`
  cada **20s**, atado a `repeatOnLifecycle(STARTED)` (se pausa en background y
  se corta en `COMPLETED/CANCELLED`). Un fallo de red no rompe: reintenta en el
  próximo ciclo.
- **Semáforo en vivo**: `MyReservationDetailScreen` ya dibuja el semáforo por
  parada; ahora se alimenta del poll (mismos `TripStopStatus`).
- **Cartel de extensión**: tarjeta destacada arriba en el detalle cuando
  `can_extend = true`. Al tocarla abre un **bottom sheet** (`ModalBottomSheet`)
  con:
  1. Dropdown de `remaining_stops` (default: el inmediato siguiente).
  2. Grilla de asientos libres para el tramo `[destino actual, destino elegido]`
     — `origin` = `destination_trip_stop_time_id` de la reserva, `destination`
     = el paradero elegido. Se reusa `GET /trips/{id}/seats?origin=&destination=`
     y el componente de grilla existente. El asiento actual se marca aparte; si
     está tomado, se muestra el mensaje "el asiento ya se asignó a otra persona"
     y solo quedan seleccionables los libres.
  3. Botón "Confirmar extensión" (deshabilitado sin asiento elegido).
- **Al confirmar**: `POST /reservations/{id}/extend` → toast, cerrar el sheet,
  refrescar el detalle. En 409, mostrar el mensaje del backend y refrescar la
  grilla.
- **Capa data**: `KtorReservationsApi.getJourney(id)` y `.extend(id, body)`;
  `ReservationsRepository.getJourney(...)` / `.extend(...)`; DTOs
  `JourneyStateDto`, `ExtendRequestDto`.
- **UX**: simple y funcional, sin pantallas nuevas: todo vive en el detalle de
  la reserva.

## 7. Reportes (admin)

El reporte idóneo es **"Disponibilidad de asientos"** (`ReportsView.vue`,
`GET /api/admin/reports/seat-availability`), que ya lee
`vw_trip_segment_seat_availability` (vista viva sobre `trip_seat_segments`), por
lo que los cambios de estado **ya se reflejan solos**. Para que el cambio del
pasajero **se vea**:

- Columna **"Extendida"** (`sí/no`) derivada de la existencia de un evento
  `EXTENDED` para esa reserva.
- Columna **"Destino original → actual"**.
- Filtro **"solo extendidas"**.

Requiere extender `vw_trip_segment_seat_availability` (o el query del
repositorio admin) con el join a `reservation_events`.

Cómo se deriva el "destino original": la extensión **actualiza** la reserva, así
que el destino original no queda en `reservations`. Se toma el
`from_stop_order` del **primer** evento `EXTENDED` de esa reserva y se resuelve
el nombre contra `trip_stop_times`/`transport_stops`. "Actual" = el
`destination_stop_order` vigente de la reserva.

## 8. Flujo de datos

```
Pasajero reserva (ya existe)
        │
Conductor marca LLEGADA/SALIDA en cada paradero
        │
SALIDA del paradero N-1  ──►  GET /journey: can_extend = true
        │                              │
        │                    App (poll ≤20s) muestra el cartel
        │                              │
        │              ┌───────────────┴────────────────┐
        │        asiento libre                    asiento tomado
        │              │                                 │
        │     dropdown + mismo asiento        dropdown + otros libres
        │              └───────────────┬────────────────┘
        │                       POST /extend
        │                              │
        │                  reserva estirada + evento EXTENDED
        │                              │
LLEGADA al (nuevo) destino ──► bajada AUTOMÁTICA (COMPLETED, asiento libre)
                                       │
                              Reporte de asientos lo refleja
```

## 9. Reglas de borde

| Caso | Comportamiento |
|---|---|
| Destino = último paradero | No hay siguiente → `can_extend = false`. Igual baja solo al llegar. |
| Pasajero `CONFIRMED` (no subió) | No se ofrece extensión; lo maneja el job de no-show. |
| Pasajero `COMPLETED` / `CANCELLED` | No se hace nada. |
| Extiende varias veces | Permitido; en cada paradero se repite y el destino se corre. |
| Cambia de asiento | El viejo queda `USED` en su tramo; el nuevo se ocupa en el tramo nuevo. |
| Asiento tomado entre el aviso y la confirmación (race) | `POST /extend` → 409; la app avisa y refresca la grilla. |
| Sin red en el poll | No rompe; reintenta al ciclo siguiente. |
| Conductor nunca marca la llegada | Al completar el viaje, las `BOARDED` se cierran como `COMPLETED`. |
| El bus salta un paradero (`SKIPPED`) | No soportado; fuera de alcance. |

## 10. Testing

- **Backend** (estilo del repo, mocks a mano como `booking/service_test.go`):
  elegibilidad (`can_extend` verdadero/falso por cada condición), extensión
  feliz, asiento tomado → 409, destino ya marcado → 400, reserva ajena → 404,
  rol inválido → 403. Los SPs se validan con un script SQL de prueba contra la
  base (el repo no testea SPs en CI): extensión, bajada automática y cierre al
  completar.
- **App pasajero**: hoy **no tiene tests**. Se agregan tests de ViewModel para
  el polling, la elegibilidad, el manejo del 409 y el corte del poll al
  completarse. (Es la lógica nueva y frágil.)
- **Conductor**: sin cambios, sin tests nuevos.
- **Admin**: verificación manual del reporte enriquecido.

## 11. Rollout

- Desplegar el backend **antes o junto** con la app (la app contra backend viejo
  recibe 404 en los endpoints nuevos).
- La migración `0006` se aplica sola al reiniciar (el schema se recrea en cada
  arranque; coordinar con datos existentes igual que siempre).
- Rama de trabajo actual: `feat/user-extra-fields`.
