# Extensión de viaje del pasajero y bajada automática — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que al llegar el bus al destino del pasajero el asiento se libere y la bajada se marque sola, avisando un paradero antes para que el pasajero pueda extender su viaje.

**Architecture:** El backend es el cerebro: un endpoint de polling (`GET /reservations/{id}/journey`) calcula la elegibilidad y otro (`POST /reservations/{id}/extend`) actualiza la misma reserva a un nuevo destino. La bajada automática se inlinea en `sp_mark_trip_stop_arrival`. La app del pasajero hace polling cada 20s y muestra el cartel de extensión. El conductor no cambia.

**Tech Stack:** Go 1.25 + chi + `database/sql` + MariaDB/MySQL (stored procedures) · Kotlin + Compose + Hilt + Ktor 3 (passenger-android) · Vue 3 + PrimeVue (admin).

## Global Constraints

- Commits en Conventional Commits, sin atribución de IA.
- **Los pasos de commit del plan se ejecutan SOLO si el usuario autoriza commits explícitamente al aprobar la ejecución.** Si no autoriza, saltear los steps "Commit" y dejar los cambios en el working tree.
- Los errores de dominio (`apperror.*`) NUNCA se envuelven con `fmt.Errorf(... %w ...)`: `WriteJSONError` usa type assertion directa, no `errors.As`.
- `ValidationError` mapea a **422**, no 400. No existe `BadRequest` en `apperror`.
- Los repositorios traducen en origen con `dberr.TranslateSP` / `dberr.NotFound` y verifican `spErr != err` antes de envolver.
- Migraciones: el runner aplica `*.up.sql` en orden alfabético en cada arranque; el schema de `0001` es DROP+CREATE. Los ENUM modificados por `0002` corren DESPUÉS de `0001`.
- SPs: `DROP PROCEDURE IF EXISTS` + `DELIMITER $$`, params `p_`, variables `v_`, `EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;`, errores con `SIGNAL SQLSTATE '45000'`, rango de segmentos siempre semiabierto `>= origen AND < destino`.
- MySQL NO soporta transacciones anidadas: no hacer `CALL` de un SP transaccional desde otro SP transaccional.
- Endpoints JSON de listas normalizan nil → `[]`.
- App: la lógica nueva vive en el ViewModel; el Screen solo dibuja y dispara acciones.
- No buildear el proyecto (regla del repo).

---

### Task 1: Migración 0006 — esquema (enum EXTENDED + columna + vista)

**Files:**
- Modify: `backend/migrations/0001_schema.up.sql` (tabla `reservations` ~522; vista `vw_trip_segment_seat_availability` ~2368)
- Create: `backend/migrations/0006_trip_extension_schema.up.sql`
- Create: `backend/migrations/0006_trip_extension_schema.down.sql`

**Interfaces:**
- Produces: `reservations.original_destination_stop_order SMALLINT UNSIGNED NULL`; `reservation_events.event_type` incluye `'EXTENDED'`; vista `vw_trip_segment_seat_availability` con 3 columnas nuevas al final: `reservation_extended` (0/1), `original_destination_name` (NULL), `current_destination_name` (NULL).

- [ ] **Step 1: Agregar la columna a `reservations` en `0001_schema.up.sql`**

Ubicar en el `CREATE TABLE reservations` la línea `destination_stop_order      SMALLINT UNSIGNED NOT NULL,` y agregar debajo:

```sql
    original_destination_stop_order SMALLINT UNSIGNED NULL,
```

- [ ] **Step 2: Reemplazar la vista `vw_trip_segment_seat_availability` en `0001_schema.up.sql`**

Reemplazar el `CREATE VIEW vw_trip_segment_seat_availability AS ...;` completo por:

```sql
CREATE VIEW vw_trip_segment_seat_availability AS
SELECT trip.id AS trip_id,
       trip.trip_code,
       trip.service_date,
       route.direction,
       seat.id AS trip_seat_id,
       seat.seat_number,
       seat.seat_label,
       segment.segment_order,
       from_place.name AS available_or_occupied_from,
       to_place.name AS available_or_occupied_until,
       inventory.state,
       inventory.reservation_id,
       reservation.reservation_code,
       inventory.reserved_at,
       inventory.released_at,
       CASE WHEN reservation.original_destination_stop_order IS NOT NULL
            THEN 1 ELSE 0 END AS reservation_extended,
       original_place.name AS original_destination_name,
       current_place.name AS current_destination_name
  FROM trip_seat_segments inventory
  JOIN trip_seats seat
    ON seat.id = inventory.trip_seat_id
  JOIN trip_instances trip
    ON trip.id = seat.trip_id
  JOIN transport_routes route
    ON route.id = trip.route_id
  JOIN trip_segments segment
    ON segment.id = inventory.trip_segment_id
  JOIN trip_stop_times from_stop
    ON from_stop.id = segment.from_trip_stop_time_id
  JOIN trip_stop_times to_stop
    ON to_stop.id = segment.to_trip_stop_time_id
  JOIN transport_stops from_place
    ON from_place.id = from_stop.stop_id
  JOIN transport_stops to_place
    ON to_place.id = to_stop.stop_id
  LEFT JOIN reservations reservation
    ON reservation.id = inventory.reservation_id
  LEFT JOIN trip_stop_times original_stop
    ON original_stop.trip_id = trip.id
   AND original_stop.stop_order = reservation.original_destination_stop_order
  LEFT JOIN transport_stops original_place
    ON original_place.id = original_stop.stop_id
  LEFT JOIN trip_stop_times current_stop
    ON current_stop.id = reservation.destination_trip_stop_time_id
  LEFT JOIN transport_stops current_place
    ON current_place.id = current_stop.stop_id;
```

- [ ] **Step 3: Crear `0006_trip_extension_schema.up.sql`**

```sql
-- ============================================================================
-- 0006_trip_extension_schema.up.sql
-- Extensión de viaje del pasajero: agrega el evento EXTENDED al ENUM.
-- Requisito previo: 0001..0005 aplicados. Nota: el ENUM de reservation_events
-- ya fue redefinido por 0002, que corre DESPUÉS de 0001 — por eso este ALTER
-- va en un archivo posterior y no en el DDL base.
-- ============================================================================

ALTER TABLE reservation_events
    MODIFY COLUMN event_type ENUM(
        'CONFIRMED', 'BOARDED', 'ALIGHTED', 'NO_SHOW',
        'SEGMENTS_RELEASED', 'CANCELLED', 'EXTENDED'
    ) NOT NULL;
```

- [ ] **Step 4: Crear `0006_trip_extension_schema.down.sql`**

```sql
ALTER TABLE reservation_events
    MODIFY COLUMN event_type ENUM(
        'CONFIRMED', 'BOARDED', 'ALIGHTED', 'NO_SHOW',
        'SEGMENTS_RELEASED', 'CANCELLED'
    ) NOT NULL;
```

- [ ] **Step 5: Verificar que las migraciones aplican**

Run (desde `backend`): `go run ./cmd/server`
Expected: el servidor arranca y loguea el router sin error de migración (si `0006` falla, `RunMigrations` aborta con `migracion 0006...: ...`). Cortar con Ctrl+C.

- [ ] **Step 6: Commit**

```bash
git add backend/migrations/0001_schema.up.sql backend/migrations/0006_trip_extension_schema.up.sql backend/migrations/0006_trip_extension_schema.down.sql
git commit -m "feat(backend): esquema de extension de viaje (evento EXTENDED y vista)"
```

---

### Task 2: Migración 0007 — `sp_extend_reservation`

**Files:**
- Create: `backend/migrations/0007_sp_extend_reservation.up.sql`
- Create: `backend/migrations/0007_sp_extend_reservation.down.sql`

**Interfaces:**
- Consumes: `reservations.original_destination_stop_order` y el evento `EXTENDED` (Task 1).
- Produces: `sp_extend_reservation(p_reservation_id, p_worker_id, p_new_destination_trip_stop_time_id, p_trip_seat_id)` que devuelve `reservation_id, destination_stop_order, trip_seat_id, seat_label, status`.

- [ ] **Step 1: Crear `0007_sp_extend_reservation.up.sql`**

```sql
-- ============================================================================
-- 0007_sp_extend_reservation.up.sql
-- Extiende una reserva BOARDED a un nuevo destino, ocupando los tramos nuevos.
-- p_trip_seat_id = 0 significa "usar el asiento actual".
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_extend_reservation;

DELIMITER $$

CREATE PROCEDURE sp_extend_reservation(
    IN p_reservation_id BIGINT UNSIGNED,
    IN p_worker_id BIGINT UNSIGNED,
    IN p_new_destination_trip_stop_time_id BIGINT UNSIGNED,
    IN p_trip_seat_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_worker_id BIGINT UNSIGNED;
    DECLARE v_trip_id BIGINT UNSIGNED;
    DECLARE v_current_seat_id BIGINT UNSIGNED;
    DECLARE v_current_destination_order SMALLINT UNSIGNED;
    DECLARE v_original_destination_order SMALLINT UNSIGNED;
    DECLARE v_new_destination_order SMALLINT UNSIGNED;
    DECLARE v_new_destination_status VARCHAR(20);
    DECLARE v_trip_status VARCHAR(30);
    DECLARE v_effective_seat_id BIGINT UNSIGNED;
    DECLARE v_seat_blocked TINYINT;
    DECLARE v_expected_segments INT;
    DECLARE v_inventory_rows INT;
    DECLARE v_conflicting_segments INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT reservation.status,
           reservation.worker_id,
           reservation.trip_id,
           reservation.trip_seat_id,
           reservation.destination_stop_order,
           reservation.original_destination_stop_order,
           trip.status
      INTO v_status,
           v_worker_id,
           v_trip_id,
           v_current_seat_id,
           v_current_destination_order,
           v_original_destination_order,
           v_trip_status
      FROM reservations reservation
      JOIN trip_instances trip ON trip.id = reservation.trip_id
     WHERE reservation.id = p_reservation_id
     FOR UPDATE;

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La reserva no existe';
    END IF;

    IF v_worker_id <> p_worker_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La reserva no pertenece al pasajero';
    END IF;

    IF v_status <> 'BOARDED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Solo se puede extender una reserva BOARDED';
    END IF;

    IF v_trip_status <> 'IN_PROGRESS' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El viaje no esta en curso';
    END IF;

    SELECT stop_order, status
      INTO v_new_destination_order, v_new_destination_status
      FROM trip_stop_times
     WHERE id = p_new_destination_trip_stop_time_id
       AND trip_id = v_trip_id;

    IF v_new_destination_order IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El destino elegido no pertenece al viaje';
    END IF;

    IF v_new_destination_order <= v_current_destination_order THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El nuevo destino debe estar despues del destino actual';
    END IF;

    IF v_new_destination_status = 'DEPARTED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El destino elegido ya fue marcado como salido por el conductor';
    END IF;

    SET v_effective_seat_id = CASE
        WHEN p_trip_seat_id IS NULL OR p_trip_seat_id = 0 THEN v_current_seat_id
        ELSE p_trip_seat_id
    END;

    SELECT is_blocked INTO v_seat_blocked
      FROM trip_seats
     WHERE id = v_effective_seat_id
       AND trip_id = v_trip_id;

    IF v_seat_blocked IS NULL OR v_seat_blocked = 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El asiento no pertenece al viaje o esta bloqueado';
    END IF;

    SET v_expected_segments = v_new_destination_order - v_current_destination_order;

    -- Lock no-op sobre el rango nuevo (patrón de sp_confirm_reservation).
    UPDATE trip_seat_segments inventory
    JOIN trip_segments segment
      ON segment.id = inventory.trip_segment_id
     SET inventory.updated_at = CURRENT_TIMESTAMP
   WHERE inventory.trip_seat_id = v_effective_seat_id
     AND segment.trip_id = v_trip_id
     AND segment.segment_order >= v_current_destination_order
     AND segment.segment_order < v_new_destination_order;

    SELECT COUNT(*),
           SUM(CASE WHEN inventory.state <> 'AVAILABLE' THEN 1 ELSE 0 END)
      INTO v_inventory_rows, v_conflicting_segments
      FROM trip_seat_segments inventory
      JOIN trip_segments segment
        ON segment.id = inventory.trip_segment_id
     WHERE inventory.trip_seat_id = v_effective_seat_id
       AND segment.trip_id = v_trip_id
       AND segment.segment_order >= v_current_destination_order
       AND segment.segment_order < v_new_destination_order;

    IF v_inventory_rows <> v_expected_segments THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El inventario por segmentos del asiento esta incompleto';
    END IF;

    IF COALESCE(v_conflicting_segments, 0) > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El asiento ya se asigno a otra persona para ese tramo';
    END IF;

    UPDATE trip_seat_segments inventory
    JOIN trip_segments segment
      ON segment.id = inventory.trip_segment_id
     SET inventory.state = 'OCCUPIED',
         inventory.reservation_id = p_reservation_id,
         inventory.reserved_at = CURRENT_TIMESTAMP,
         inventory.released_at = NULL
   WHERE inventory.trip_seat_id = v_effective_seat_id
     AND segment.trip_id = v_trip_id
     AND segment.segment_order >= v_current_destination_order
     AND segment.segment_order < v_new_destination_order;

    INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status)
    SELECT p_reservation_id, segment.id, 'OCCUPIED'
      FROM trip_segments segment
     WHERE segment.trip_id = v_trip_id
       AND segment.segment_order >= v_current_destination_order
       AND segment.segment_order < v_new_destination_order;

    UPDATE reservations
       SET destination_trip_stop_time_id = p_new_destination_trip_stop_time_id,
           destination_stop_order = v_new_destination_order,
           trip_seat_id = v_effective_seat_id,
           original_destination_stop_order = COALESCE(v_original_destination_order, v_current_destination_order)
     WHERE id = p_reservation_id;

    INSERT INTO reservation_events (
        reservation_id, event_type, trip_stop_time_id, actor_user_id, details
    ) VALUES (
        p_reservation_id, 'EXTENDED', p_new_destination_trip_stop_time_id, p_worker_id,
        CONCAT('Extension del orden ', v_current_destination_order, ' al orden ', v_new_destination_order)
    );

    COMMIT;

    SELECT p_reservation_id AS reservation_id,
           v_new_destination_order AS destination_stop_order,
           v_effective_seat_id AS trip_seat_id,
           seat.seat_label AS seat_label,
           'BOARDED' AS status
      FROM trip_seats seat
     WHERE seat.id = v_effective_seat_id;
END$$

DELIMITER ;
```

- [ ] **Step 2: Crear `0007_sp_extend_reservation.down.sql`**

```sql
DROP PROCEDURE IF EXISTS sp_extend_reservation;
```

- [ ] **Step 3: Verificar que la migración aplica**

Run (desde `backend`): `go run ./cmd/server`
Expected: arranca sin `migracion 0007...`. Cortar con Ctrl+C.

- [ ] **Step 4: Commit**

```bash
git add backend/migrations/0007_sp_extend_reservation.up.sql backend/migrations/0007_sp_extend_reservation.down.sql
git commit -m "feat(backend): sp_extend_reservation"
```

---

### Task 3: Migración 0008 — bajada automática y cierre de viaje

**Files:**
- Create: `backend/migrations/0008_auto_alight.up.sql`
- Create: `backend/migrations/0008_auto_alight.down.sql`

**Interfaces:**
- Consumes: `trip_stop_times`, `reservations`, `trip_seat_segments`, `reservation_segments`, `reservation_events` (Task 1).
- Produces: `sp_mark_trip_stop_arrival(p_trip_stop_time_id, p_driver_id)` (misma firma, ahora además baja solo) y `sp_complete_trip(p_trip_id, p_driver_id)`.

- [ ] **Step 1: Crear `0008_auto_alight.up.sql`**

```sql
-- ============================================================================
-- 0008_auto_alight.up.sql
-- Bajada automática al marcar la llegada al destino del pasajero, y cierre de
-- reservas BOARDED al completar el viaje.
-- Nota: la lógica de bajada se inlinea (no CALL a otro SP) porque MySQL no
-- soporta transacciones anidadas — mismo criterio que 0002.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_mark_trip_stop_arrival;
DROP PROCEDURE IF EXISTS sp_complete_trip;

DELIMITER $$

CREATE PROCEDURE sp_mark_trip_stop_arrival(
    IN p_trip_stop_time_id BIGINT UNSIGNED,
    IN p_driver_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_assigned_driver_id BIGINT UNSIGNED;
    DECLARE v_trip_status VARCHAR(30);
    DECLARE v_trip_id BIGINT UNSIGNED;
    DECLARE v_effective_at DATETIME;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SET v_effective_at = CURRENT_TIMESTAMP;

    SELECT trip.driver_id, trip.status, trip.id
      INTO v_assigned_driver_id, v_trip_status, v_trip_id
      FROM trip_stop_times stop_time
      JOIN trip_instances trip ON trip.id = stop_time.trip_id
     WHERE stop_time.id = p_trip_stop_time_id
     FOR UPDATE;

    IF v_assigned_driver_id IS NULL OR v_assigned_driver_id <> p_driver_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo el conductor asignado puede marcar la llegada';
    END IF;

    IF v_trip_status IN ('COMPLETED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no admite nuevas marcas de llegada';
    END IF;

    UPDATE trip_stop_times
       SET actual_arrival_at = COALESCE(actual_arrival_at, v_effective_at),
           arrival_marked_by_user_id = COALESCE(arrival_marked_by_user_id, p_driver_id),
           status = CASE WHEN status = 'PENDING' THEN 'ARRIVED' ELSE status END
     WHERE id = p_trip_stop_time_id;

    -- Bitácora de bajada automática (antes de cambiar el estado).
    INSERT INTO reservation_events (
        reservation_id, event_type, trip_stop_time_id, actor_user_id, event_at, details
    )
    SELECT r.id, 'ALIGHTED', p_trip_stop_time_id, p_driver_id, v_effective_at,
           'Bajada automatica al llegar al destino'
      FROM reservations r
     WHERE r.trip_id = v_trip_id
       AND r.destination_trip_stop_time_id = p_trip_stop_time_id
       AND r.status = 'BOARDED';

    UPDATE trip_seat_segments
       SET state = 'USED'
     WHERE state = 'OCCUPIED'
       AND reservation_id IN (
           SELECT id FROM reservations
            WHERE trip_id = v_trip_id
              AND destination_trip_stop_time_id = p_trip_stop_time_id
              AND status = 'BOARDED'
       );

    UPDATE reservation_segments
       SET allocation_status = 'USED'
     WHERE allocation_status = 'OCCUPIED'
       AND reservation_id IN (
           SELECT id FROM reservations
            WHERE trip_id = v_trip_id
              AND destination_trip_stop_time_id = p_trip_stop_time_id
              AND status = 'BOARDED'
       );

    UPDATE reservations
       SET status = 'COMPLETED',
           completed_at = v_effective_at
     WHERE trip_id = v_trip_id
       AND destination_trip_stop_time_id = p_trip_stop_time_id
       AND status = 'BOARDED';

    COMMIT;
END$$

CREATE PROCEDURE sp_complete_trip(
    IN p_trip_id BIGINT UNSIGNED,
    IN p_driver_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_status VARCHAR(30);
    DECLARE v_driver_id BIGINT UNSIGNED;
    DECLARE v_effective_at DATETIME;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SET v_effective_at = CURRENT_TIMESTAMP;

    SELECT status, driver_id
      INTO v_status, v_driver_id
      FROM trip_instances
     WHERE id = p_trip_id
     FOR UPDATE;

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El viaje no existe';
    END IF;

    IF v_driver_id <> p_driver_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Solo el conductor asignado puede finalizar el viaje';
    END IF;

    IF v_status <> 'IN_PROGRESS' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El viaje no esta en curso';
    END IF;

    UPDATE trip_instances
       SET status = 'COMPLETED',
           actual_end_at = v_effective_at
     WHERE id = p_trip_id;

    -- Cierra las reservas que quedaron arriba (viaje mal cerrado).
    INSERT INTO reservation_events (
        reservation_id, event_type, actor_user_id, event_at, details
    )
    SELECT r.id, 'ALIGHTED', p_driver_id, v_effective_at,
           'Bajada automatica al finalizar el viaje'
      FROM reservations r
     WHERE r.trip_id = p_trip_id
       AND r.status = 'BOARDED';

    UPDATE trip_seat_segments
       SET state = 'USED'
     WHERE state = 'OCCUPIED'
       AND reservation_id IN (
           SELECT id FROM reservations
            WHERE trip_id = p_trip_id AND status = 'BOARDED'
       );

    UPDATE reservation_segments
       SET allocation_status = 'USED'
     WHERE allocation_status = 'OCCUPIED'
       AND reservation_id IN (
           SELECT id FROM reservations
            WHERE trip_id = p_trip_id AND status = 'BOARDED'
       );

    UPDATE reservations
       SET status = 'COMPLETED',
           completed_at = v_effective_at
     WHERE trip_id = p_trip_id
       AND status = 'BOARDED';

    COMMIT;
END$$

DELIMITER ;
```

- [ ] **Step 2: Crear `0008_auto_alight.down.sql`**

```sql
DROP PROCEDURE IF EXISTS sp_complete_trip;
```

- [ ] **Step 3: Verificar que la migración aplica**

Run (desde `backend`): `go run ./cmd/server`
Expected: arranca sin `migracion 0008...`. Cortar con Ctrl+C.

- [ ] **Step 4: Commit**

```bash
git add backend/migrations/0008_auto_alight.up.sql backend/migrations/0008_auto_alight.down.sql
git commit -m "feat(backend): bajada automatica en llegada y cierre de viaje"
```

---

### Task 4: Driver — `CompleteTrip` usa `sp_complete_trip`

**Files:**
- Modify: `backend/internal/modules/driver/repository.go:369-392`

**Interfaces:**
- Consumes: `sp_complete_trip` (Task 3).

- [ ] **Step 1: Reemplazar el cuerpo de `CompleteTrip`**

```go
// CompleteTrip pasa el viaje a COMPLETED y cierra las reservas BOARDED que
// quedaron arriba. Toda la transicion (estado + liberacion de asientos) vive
// en sp_complete_trip para que sea atomica.
func (r *driverRepository) CompleteTrip(ctx context.Context, tripID, driverID int64) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_complete_trip(?, ?)", tripID, driverID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_complete_trip: %w", err)
	}
	return nil
}
```

- [ ] **Step 2: Ajustar la firma en la interfaz y el servicio**

En `DriverRepository` (interfaz, `repository.go`) cambiar la firma de `CompleteTrip` a `CompleteTrip(ctx context.Context, tripID, driverID int64) error`.

En `driver/service.go`, en `CompleteTrip`, cambiar la última línea `return s.repo.CompleteTrip(ctx, tripID)` por:

```go
	return s.repo.CompleteTrip(ctx, tripID, driverID)
```

- [ ] **Step 3: Verificar que compila y los tests pasan**

Run (desde `backend`): `go build ./... && go test ./internal/modules/driver/...`
Expected: `ok  .../internal/modules/driver`.

- [ ] **Step 4: Commit**

```bash
git add backend/internal/modules/driver/repository.go backend/internal/modules/driver/service.go
git commit -m "feat(driver): finalizar viaje via sp_complete_trip"
```

---

### Task 5: Booking — endpoint `GET /reservations/{id}/journey`

**Files:**
- Modify: `backend/internal/modules/booking/repository.go` (structs, interfaz, impl)
- Modify: `backend/internal/modules/booking/service.go` (interfaz, impl, mensaje de `requireWorker`)
- Modify: `backend/internal/modules/booking/handler.go` (handler + ruta)

**Interfaces:**
- Produces: `JourneyState`, `JourneyStop`, `ExtensionOffer`, `ExtensionStop`; `BookingRepository.GetJourneyState(ctx, reservationID, workerID) (JourneyState, error)`; `BookingService.GetJourney(ctx, reservationID) (JourneyState, error)`; `GET /api/reservations/{id}/journey`.

- [ ] **Step 1: Agregar los tipos y el método a la interfaz en `repository.go`**

Agregar después de `ReservationIdentity`:

```go
// JourneyState es el read-model de GET /reservations/{id}/journey (polling).
type JourneyState struct {
	ReservationID         int64           `json:"reservation_id"`
	ReservationStatus     string          `json:"reservation_status"`
	TripID                int64           `json:"trip_id"`
	TripStatus            string          `json:"trip_status"`
	LastDepartedStopOrder *int            `json:"last_departed_stop_order"`
	DestinationStopOrder  int             `json:"destination_stop_order"`
	Stops                 []JourneyStop   `json:"stops"`
	CanExtend             bool            `json:"can_extend"`
	Extension             *ExtensionOffer `json:"extension,omitempty"`
}

// JourneyStop es una parada del cronograma con su estado real.
type JourneyStop struct {
	TripStopTimeID       int64      `json:"trip_stop_time_id"`
	StopID               int64      `json:"stop_id"`
	StopName             string     `json:"stop_name"`
	StopOrder            int        `json:"stop_order"`
	ScheduledArrivalAt   time.Time  `json:"scheduled_arrival_at"`
	ScheduledDepartureAt time.Time  `json:"scheduled_departure_at"`
	Status               string     `json:"status"`
	ActualArrivalAt      *time.Time `json:"actual_arrival_at,omitempty"`
	ActualDepartureAt    *time.Time `json:"actual_departure_at,omitempty"`
}

// ExtensionOffer describe la oferta de extensión cuando can_extend = true.
type ExtensionOffer struct {
	CurrentSeatFree bool            `json:"current_seat_free"`
	RemainingStops  []ExtensionStop `json:"remaining_stops"`
}

// ExtensionStop es un paradero al que el pasajero puede extender.
type ExtensionStop struct {
	TripStopTimeID int64  `json:"trip_stop_time_id"`
	StopName       string `json:"stop_name"`
	StopOrder      int    `json:"stop_order"`
}
```

Agregar a `BookingRepository`:

```go
	GetJourneyState(ctx context.Context, reservationID, workerID int64) (JourneyState, error)
```

- [ ] **Step 2: Implementar `GetJourneyState` en `repository.go`**

Agregar al final del archivo (importar `apperror`):

```go
// GetJourneyState arma el estado de polling del pasajero: cronograma con su
// semáforo y, si corresponde, la oferta de extensión. La elegibilidad NO se
// persiste: se deriva en cada llamada.
func (r *bookingRepository) GetJourneyState(ctx context.Context, reservationID, workerID int64) (JourneyState, error) {
	const resQ = `
        SELECT worker_id, trip_id, trip_seat_id, destination_trip_stop_time_id,
               destination_stop_order, status
          FROM reservations
         WHERE id = ?`
	var ownerID, tripID, seatID, destStopTimeID int64
	var destOrder int
	var status string
	err := r.db.QueryRowContext(ctx, resQ, reservationID).Scan(
		&ownerID, &tripID, &seatID, &destStopTimeID, &destOrder, &status,
	)
	if err != nil {
		if nfErr := dberr.NotFound(err, "reserva", reservationID); nfErr != err {
			return JourneyState{}, nfErr
		}
		return JourneyState{}, fmt.Errorf("cargando reserva para journey: %w", err)
	}
	if ownerID != workerID {
		return JourneyState{}, apperror.NotFoundError{Entity: "reserva", ID: reservationID}
	}

	var tripStatus string
	if err := r.db.QueryRowContext(ctx,
		`SELECT status FROM trip_instances WHERE id = ?`, tripID).Scan(&tripStatus); err != nil {
		return JourneyState{}, fmt.Errorf("cargando estado del viaje: %w", err)
	}

	const stopsQ = `
        SELECT tst.id, tst.stop_id, ts.name, tst.stop_order,
               tst.scheduled_arrival_at, tst.scheduled_departure_at,
               tst.status, tst.actual_arrival_at, tst.actual_departure_at
          FROM trip_stop_times tst
          JOIN transport_stops ts ON ts.id = tst.stop_id
         WHERE tst.trip_id = ?
         ORDER BY tst.stop_order`
	rows, err := r.db.QueryContext(ctx, stopsQ, tripID)
	if err != nil {
		return JourneyState{}, fmt.Errorf("cargando paradas del journey: %w", err)
	}
	defer rows.Close()

	stops := make([]JourneyStop, 0)
	for rows.Next() {
		var s JourneyStop
		var arr, dep sql.NullTime
		if err := rows.Scan(&s.TripStopTimeID, &s.StopID, &s.StopName, &s.StopOrder,
			&s.ScheduledArrivalAt, &s.ScheduledDepartureAt, &s.Status, &arr, &dep); err != nil {
			return JourneyState{}, fmt.Errorf("escaneando parada del journey: %w", err)
		}
		if arr.Valid {
			t := arr.Time
			s.ActualArrivalAt = &t
		}
		if dep.Valid {
			t := dep.Time
			s.ActualDepartureAt = &t
		}
		stops = append(stops, s)
	}
	if err := rows.Err(); err != nil {
		return JourneyState{}, err
	}

	lastDepartedOrder := -1
	for _, s := range stops {
		if s.Status == "DEPARTED" && s.StopOrder > lastDepartedOrder {
			lastDepartedOrder = s.StopOrder
		}
	}
	var lastDeparted *int
	if lastDepartedOrder >= 0 {
		v := lastDepartedOrder
		lastDeparted = &v
	}

	state := JourneyState{
		ReservationID:         reservationID,
		ReservationStatus:     status,
		TripID:                tripID,
		TripStatus:            tripStatus,
		LastDepartedStopOrder: lastDeparted,
		DestinationStopOrder:  destOrder,
		Stops:                 stops,
	}

	if status != "BOARDED" || tripStatus != "IN_PROGRESS" || lastDeparted == nil ||
		destOrder != *lastDeparted+1 {
		return state, nil
	}

	remaining := make([]ExtensionStop, 0)
	for _, s := range stops {
		if s.StopOrder > destOrder && s.Status != "DEPARTED" {
			remaining = append(remaining, ExtensionStop{
				TripStopTimeID: s.TripStopTimeID,
				StopName:       s.StopName,
				StopOrder:      s.StopOrder,
			})
		}
	}
	if len(remaining) == 0 {
		return state, nil
	}

	seats, err := r.listSeatAvailability(ctx, tripID, destStopTimeID, remaining[0].TripStopTimeID)
	if err != nil {
		return JourneyState{}, err
	}
	anyFree := false
	currentSeatFree := false
	for _, s := range seats {
		if s.availability == "AVAILABLE" {
			anyFree = true
			if s.tripSeatID == seatID {
				currentSeatFree = true
			}
		}
	}
	if !anyFree {
		return state, nil
	}

	state.CanExtend = true
	state.Extension = &ExtensionOffer{CurrentSeatFree: currentSeatFree, RemainingStops: remaining}
	return state, nil
}

type seatAvailability struct {
	tripSeatID   int64
	seatLabel    string
	availability string
}

// listSeatAvailability reusa sp_list_trip_seats para un tramo.
func (r *bookingRepository) listSeatAvailability(ctx context.Context, tripID, originID, destID int64) ([]seatAvailability, error) {
	rows, err := r.db.QueryContext(ctx, "CALL sp_list_trip_seats(?, ?, ?)", tripID, originID, destID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return nil, spErr
		}
		return nil, fmt.Errorf("llamando sp_list_trip_seats: %w", err)
	}
	defer rows.Close()

	out := make([]seatAvailability, 0)
	for rows.Next() {
		var s seatAvailability
		var number int
		if err := rows.Scan(&s.tripSeatID, &number, &s.seatLabel, &s.availability); err != nil {
			return nil, fmt.Errorf("escaneando asientos del journey: %w", err)
		}
		out = append(out, s)
	}
	return out, rows.Err()
}
```

Agregar `"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"` a los imports de `repository.go`.

- [ ] **Step 3: Agregar `GetJourney` al servicio**

En la interfaz `BookingService`:

```go
	GetJourney(ctx context.Context, reservationID int64) (JourneyState, error)
```

Implementación (después de `ListForWorker`):

```go
// GetJourney devuelve el estado de polling del pasajero dueño de la reserva.
func (s *bookingService) GetJourney(ctx context.Context, reservationID int64) (JourneyState, error) {
	workerID, err := requireWorker(ctx)
	if err != nil {
		return JourneyState{}, err
	}
	return s.repo.GetJourneyState(ctx, reservationID, workerID)
}
```

Y neutralizar el mensaje de `requireWorker` (hoy dice "reportar incidencias de pasajero"):

```go
	if role != RoleWORKER {
		return 0, apperror.ForbiddenError{Reason: "solo el rol WORKER puede acceder a las reservas"}
	}
```

- [ ] **Step 4: Agregar el handler y la ruta**

En `handler.go`:

```go
// Journey maneja GET /reservations/{id}/journey — estado de polling del
// pasajero: semáforo del cronograma y oferta de extensión.
func (h *BookingHandler) Journey(w http.ResponseWriter, r *http.Request) {
	reservationID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "id", Reason: "debe ser un entero positivo"})
		return
	}
	res, err := h.svc.GetJourney(r.Context(), reservationID)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	_ = json.NewEncoder(w).Encode(res)
}
```

En `RegisterRoutes`:

```go
	r.Get("/reservations/{id}/journey", h.Journey)
```

- [ ] **Step 5: Verificar que compila**

Run (desde `backend`): `go build ./...`
Expected: sin errores.

- [ ] **Step 6: Commit**

```bash
git add backend/internal/modules/booking/repository.go backend/internal/modules/booking/service.go backend/internal/modules/booking/handler.go
git commit -m "feat(booking): endpoint journey de polling del pasajero"
```

---

### Task 6: Booking — endpoint `POST /reservations/{id}/extend`

**Files:**
- Modify: `backend/internal/modules/booking/repository.go`
- Modify: `backend/internal/modules/booking/service.go`
- Modify: `backend/internal/modules/booking/handler.go`

**Interfaces:**
- Consumes: `sp_extend_reservation` (Task 2).
- Produces: `ExtendParams`, `ExtendResult`, `ExtendRequest`; `BookingRepository.ExtendReservation`; `BookingService.Extend`; `POST /api/reservations/{id}/extend`.

- [ ] **Step 1: Tipos + método de repo**

Agregar a `repository.go`:

```go
// ExtendParams agrupa los campos para sp_extend_reservation.
// TripSeatID = 0 significa "usar el asiento actual".
type ExtendParams struct {
	ReservationID                int64
	WorkerID                     int64
	NewDestinationTripStopTimeID int64
	TripSeatID                   int64
}

// ExtendResult es el result set de sp_extend_reservation.
type ExtendResult struct {
	ReservationID        int64  `json:"reservation_id"`
	DestinationStopOrder int    `json:"destination_stop_order"`
	TripSeatID           int64  `json:"trip_seat_id"`
	SeatLabel            string `json:"seat_label"`
	Status               string `json:"status"`
}
```

Agregar a `BookingRepository`:

```go
	ExtendReservation(ctx context.Context, params ExtendParams) (ExtendResult, error)
```

Implementación:

```go
// ExtendReservation llama a sp_extend_reservation.
func (r *bookingRepository) ExtendReservation(ctx context.Context, params ExtendParams) (ExtendResult, error) {
	var res ExtendResult
	err := r.db.QueryRowContext(ctx, "CALL sp_extend_reservation(?, ?, ?, ?)",
		params.ReservationID, params.WorkerID,
		params.NewDestinationTripStopTimeID, params.TripSeatID,
	).Scan(&res.ReservationID, &res.DestinationStopOrder, &res.TripSeatID,
		&res.SeatLabel, &res.Status)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return ExtendResult{}, spErr
		}
		return ExtendResult{}, fmt.Errorf("llamando sp_extend_reservation: %w", err)
	}
	return res, nil
}
```

- [ ] **Step 2: Request + servicio**

Agregar a `service.go`:

```go
// ExtendRequest es el cuerpo de POST /reservations/{id}/extend.
// trip_seat_id es opcional: si no viene, se mantiene el asiento actual.
type ExtendRequest struct {
	NewDestinationTripStopTimeID int64 `json:"new_destination_trip_stop_time_id" validate:"required,gt=0"`
	TripSeatID                   int64 `json:"trip_seat_id" validate:"omitempty,gt=0"`
}
```

Interfaz `BookingService`:

```go
	Extend(ctx context.Context, reservationID int64, req ExtendRequest) (ExtendResult, error)
```

Implementación:

```go
// Extend estira la reserva del pasajero a un nuevo destino.
func (s *bookingService) Extend(ctx context.Context, reservationID int64, req ExtendRequest) (ExtendResult, error) {
	workerID, err := requireWorker(ctx)
	if err != nil {
		return ExtendResult{}, err
	}
	return s.repo.ExtendReservation(ctx, ExtendParams{
		ReservationID:                reservationID,
		WorkerID:                     workerID,
		NewDestinationTripStopTimeID: req.NewDestinationTripStopTimeID,
		TripSeatID:                   req.TripSeatID,
	})
}
```

- [ ] **Step 3: Handler + ruta**

```go
// Extend maneja POST /reservations/{id}/extend — estira la reserva a un nuevo
// destino conservando (o cambiando) el asiento.
func (h *BookingHandler) Extend(w http.ResponseWriter, r *http.Request) {
	reservationID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "id", Reason: "debe ser un entero positivo"})
		return
	}
	var req ExtendRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	res, err := h.svc.Extend(r.Context(), reservationID, req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	_ = json.NewEncoder(w).Encode(res)
}
```

En `RegisterRoutes`:

```go
	r.Post("/reservations/{id}/extend", h.Extend)
```

- [ ] **Step 4: Verificar que compila**

Run (desde `backend`): `go build ./...`
Expected: sin errores.

- [ ] **Step 5: Commit**

```bash
git add backend/internal/modules/booking/repository.go backend/internal/modules/booking/service.go backend/internal/modules/booking/handler.go
git commit -m "feat(booking): endpoint extend de reserva"
```

---

### Task 7: Booking — tests de servicio

**Files:**
- Modify: `backend/internal/modules/booking/service_test.go`

**Interfaces:**
- Consumes: `GetJourney`, `Extend`, `JourneyState`, `ExtendResult` (Tasks 5-6).

- [ ] **Step 1: Extender el mock con los nuevos campos**

Agregar a `mockBookingRepo`:

```go
	journey       JourneyState
	journeyErr    error
	extend        ExtendResult
	extendErr     error
	extendParams  ExtendParams
```

Y los métodos:

```go
func (m *mockBookingRepo) GetJourneyState(_ context.Context, _, _ int64) (JourneyState, error) {
	return m.journey, m.journeyErr
}

func (m *mockBookingRepo) ExtendReservation(_ context.Context, p ExtendParams) (ExtendResult, error) {
	m.extendParams = p
	return m.extend, m.extendErr
}
```

- [ ] **Step 2: Escribir los tests (fallando primero)**

```go
func TestGetJourney_PropagatesWorkerIDAndState(t *testing.T) {
	repo := &mockBookingRepo{journey: JourneyState{
		ReservationID:     42,
		ReservationStatus: "BOARDED",
		CanExtend:         true,
	}}
	svc := NewService(repo)

	got, err := svc.GetJourney(ctxWithWorker(t, 77), 42)
	require.NoError(t, err)
	assert.Equal(t, int64(42), got.ReservationID)
	assert.True(t, got.CanExtend)
}

func TestGetJourney_NonWorkerRole_ReturnsForbidden(t *testing.T) {
	svc := NewService(&mockBookingRepo{})

	_, err := svc.GetJourney(ctxWithRole(t, 77, "DRIVER"), 42)
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "rol no-WORKER debe mapear a Forbidden")
}

func TestExtend_PassesWorkerAndSeat(t *testing.T) {
	repo := &mockBookingRepo{extend: ExtendResult{
		ReservationID:        42,
		DestinationStopOrder: 6,
		TripSeatID:           33,
		SeatLabel:            "12",
		Status:               "BOARDED",
	}}
	svc := NewService(repo)

	got, err := svc.Extend(ctxWithWorker(t, 77), 42, ExtendRequest{
		NewDestinationTripStopTimeID: 55,
		TripSeatID:                   33,
	})
	require.NoError(t, err)
	assert.Equal(t, int64(33), got.TripSeatID)
	assert.Equal(t, int64(42), repo.extendParams.ReservationID)
	assert.Equal(t, int64(77), repo.extendParams.WorkerID)
	assert.Equal(t, int64(55), repo.extendParams.NewDestinationTripStopTimeID)
	assert.Equal(t, int64(33), repo.extendParams.TripSeatID)
}

func TestExtend_SeatTaken_ReturnsConflict(t *testing.T) {
	repo := &mockBookingRepo{extendErr: apperror.ConflictError{Msg: "El asiento ya se asigno a otra persona para ese tramo"}}
	svc := NewService(repo)

	_, err := svc.Extend(ctxWithWorker(t, 77), 42, ExtendRequest{NewDestinationTripStopTimeID: 55})
	require.Error(t, err)
	var ce apperror.ConflictError
	require.True(t, errors.As(err, &ce), "asiento tomado debe mapear a Conflict")
}
```

- [ ] **Step 3: Correr los tests**

Run (desde `backend`): `go test ./internal/modules/booking/... -run 'TestGetJourney|TestExtend' -v`
Expected: PASS los 4.

- [ ] **Step 4: Commit**

```bash
git add backend/internal/modules/booking/service_test.go
git commit -m "test(booking): cobertura de journey y extend"
```

---

### Task 8: Admin backend — reporte de asientos enriquecido

**Files:**
- Modify: `backend/internal/modules/admin/repository.go` (`SeatAvail` ~403; `GetTripSeatAvailability` ~2264; interfaz ~609)
- Modify: `backend/internal/modules/admin/service.go` (interfaz ~96; método ~735)
- Modify: `backend/internal/modules/admin/handler.go` (`SeatAvailabilityReport` ~1045)

**Interfaces:**
- Consumes: columnas nuevas de la vista (Task 1).
- Produces: `SeatAvail.ReservationExtended int`, `SeatAvail.OriginalDestinationName *string`, `SeatAvail.CurrentDestinationName *string`; filtro `extended` (`true`/`false`/vacío).

- [ ] **Step 1: Extender `SeatAvail` y la query**

Agregar campos al struct `SeatAvail`:

```go
	ReservationExtended     int     `json:"reservation_extended"`
	OriginalDestinationName *string `json:"original_destination_name,omitempty"`
	CurrentDestinationName  *string `json:"current_destination_name,omitempty"`
```

En `GetTripSeatAvailability`, cambiar la query y el escaneo. La firma pasa a `(ctx, tripID int64, state, extended string)`. Query:

```go
	var conds []string
	var fargs []any
	conds = append(conds, "trip_id = ?")
	fargs = append(fargs, tripID)
	if state != "" {
		conds = append(conds, "state = ?")
		fargs = append(fargs, state)
	}
	if extended == "true" {
		conds = append(conds, "reservation_extended = 1")
	} else if extended == "false" {
		conds = append(conds, "reservation_extended = 0")
	}
	where := " WHERE " + strings.Join(conds, " AND ")
	q := `SELECT trip_id, trip_code, service_date, direction, trip_seat_id,
               seat_number, seat_label, segment_order, available_or_occupied_from,
               available_or_occupied_until, state, reservation_id,
               reservation_code, reserved_at, released_at,
               reservation_extended, original_destination_name,
               current_destination_name
          FROM vw_trip_segment_seat_availability` + where + `
         ORDER BY seat_number, segment_order`
```

Escaneo:

```go
	var origName, currName sql.NullString
	if err := rows.Scan(&s.TripID, &s.TripCode, &s.ServiceDate, &s.Direction,
		&s.TripSeatID, &s.SeatNumber, &s.SeatLabel, &s.SegmentOrder,
		&s.AvailableFrom, &s.AvailableUntil, &s.State, &resID, &resCode,
		&resAt, &relAt, &s.ReservationExtended, &origName, &currName); err != nil {
		return nil, fmt.Errorf("escaneando disponibilidad de asientos: %w", err)
	}
	s.OriginalDestinationName = nullableStr(origName)
	s.CurrentDestinationName = nullableStr(currName)
```

- [ ] **Step 2: Actualizar la interfaz del repo y el servicio**

En la interfaz `AdminRepository` y `AdminService`, cambiar la firma de `GetTripSeatAvailability` a `(ctx context.Context, tripID int64, state, extended string)`.

En `adminService.GetTripSeatAvailability`, aceptar `extended string` y pasarlo al repo.

- [ ] **Step 3: Actualizar el handler**

```go
func (h *AdminHandler) SeatAvailabilityReport(w http.ResponseWriter, r *http.Request) {
	tripID, err := strconv.ParseInt(r.URL.Query().Get("trip_id"), 10, 64)
	if err != nil || tripID < 1 {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "trip_id", Reason: "entero positivo requerido"})
		return
	}
	state := r.URL.Query().Get("state")
	extended := r.URL.Query().Get("extended")
	if extended != "" && extended != "true" && extended != "false" {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "extended", Reason: "debe ser true o false"})
		return
	}
	avail, err := h.svc.GetTripSeatAvailability(r.Context(), tripID, state, extended)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{"items": orEmpty(avail, seatAvailSlice)})
}
```

- [ ] **Step 4: Verificar que compila**

Run (desde `backend`): `go build ./...`
Expected: sin errores.

- [ ] **Step 5: Commit**

```bash
git add backend/internal/modules/admin/repository.go backend/internal/modules/admin/service.go backend/internal/modules/admin/handler.go
git commit -m "feat(admin): reporte de asientos con marca de extension"
```

---

### Task 9: Admin frontend — columnas y filtro de extensión

**Files:**
- Modify: `admin/src/types.ts` (`TripSeatAvailability`)
- Modify: `admin/src/components/ReportsView.vue`

**Interfaces:**
- Consumes: `reservation_extended`, `original_destination_name`, `current_destination_name`, `?extended=` (Task 8).

- [ ] **Step 1: Extender el tipo**

En `admin/src/types.ts`, agregar a `TripSeatAvailability`:

```ts
  reservation_extended: number
  original_destination_name?: string
  current_destination_name?: string
```

- [ ] **Step 2: Agregar el filtro `extended` en `ReportsView.vue`**

En el estado del tab de asientos, extender el filtro:

```ts
const EXTENDED_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todas' },
  { value: 'true', label: 'Solo extendidas' },
  { value: 'false', label: 'Solo no extendidas' },
]

const seatFilter = reactive<{ tripId: number | null; state: string; extended: string }>({
  tripId: null,
  state: '',
  extended: '',
})
```

En `searchSeats`, agregar:

```ts
  if (seatFilter.extended) params.set('extended', seatFilter.extended)
```

En el template del tab `seats`, agregar un `<div class="filter">` con un `Select` ligado a `seatFilter.extended` y `:options="EXTENDED_OPTIONS"`, después del filtro de estado.

- [ ] **Step 3: Agregar las columnas a la DataTable**

Después de la columna "Código de reserva":

```vue
            <Column header="Extendida" style="width: 7rem">
              <template #body="{ data }">
                <Tag
                  :value="data.reservation_extended ? 'Sí' : 'No'"
                  :severity="data.reservation_extended ? 'info' : 'secondary'"
                />
              </template>
            </Column>
            <Column header="Destino original → actual">
              <template #body="{ data }">
                {{ formatCell(data.original_destination_name) }} →
                {{ formatCell(data.current_destination_name) }}
              </template>
            </Column>
```

- [ ] **Step 4: Verificar que el admin compila**

Run (desde `admin`): `npm run build`
Expected: build sin errores de TypeScript.

- [ ] **Step 5: Commit**

```bash
git add admin/src/types.ts admin/src/components/ReportsView.vue
git commit -m "feat(admin): columnas de extension en reporte de asientos"
```

---

### Task 10: App — DTOs, API, repositorio y modelos de dominio

**Files:**
- Modify: `passenger-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/passenger/shared/data/remote/dto/ReservationDto.kt`
- Create: `passenger-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/passenger/shared/domain/model/Journey.kt`
- Create: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/mapper/JourneyMapper.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/remote/KtorReservationsApi.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/domain/repository/ReservationsRepository.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/repository/ReservationsRepositoryImpl.kt`

**Interfaces:**
- Produces: `JourneyState`, `ExtensionOffer`, `ExtensionStop` (domain); `ReservationsRepository.getJourney(reservationId): AppResult<JourneyState>` y `extend(reservationId, newDestinationTripStopTimeId, tripSeatId): AppResult<ExtendResult>`; `ExtendResult` domain.

- [ ] **Step 1: DTOs en `ReservationDto.kt`**

```kotlin
@Serializable
data class JourneyStateDto(
    @SerialName("reservation_id") val reservationId: Long,
    @SerialName("reservation_status") val reservationStatus: String,
    @SerialName("trip_id") val tripId: Long,
    @SerialName("trip_status") val tripStatus: String,
    @SerialName("last_departed_stop_order") val lastDepartedStopOrder: Int? = null,
    @SerialName("destination_stop_order") val destinationStopOrder: Int,
    val stops: List<JourneyStopDto> = emptyList(),
    @SerialName("can_extend") val canExtend: Boolean = false,
    val extension: ExtensionOfferDto? = null,
)

@Serializable
data class JourneyStopDto(
    @SerialName("trip_stop_time_id") val tripStopTimeId: Long,
    @SerialName("stop_id") val stopId: Long,
    @SerialName("stop_name") val stopName: String,
    @SerialName("stop_order") val stopOrder: Int,
    @SerialName("scheduled_arrival_at") val scheduledArrivalAt: String,
    @SerialName("scheduled_departure_at") val scheduledDepartureAt: String,
    val status: String,
    @SerialName("actual_arrival_at") val actualArrivalAt: String? = null,
    @SerialName("actual_departure_at") val actualDepartureAt: String? = null,
)

@Serializable
data class ExtensionOfferDto(
    @SerialName("current_seat_free") val currentSeatFree: Boolean,
    @SerialName("remaining_stops") val remainingStops: List<ExtensionStopDto> = emptyList(),
)

@Serializable
data class ExtensionStopDto(
    @SerialName("trip_stop_time_id") val tripStopTimeId: Long,
    @SerialName("stop_name") val stopName: String,
    @SerialName("stop_order") val stopOrder: Int,
)

@Serializable
data class ExtendRequestDto(
    @SerialName("new_destination_trip_stop_time_id") val newDestinationTripStopTimeId: Long,
    @SerialName("trip_seat_id") val tripSeatId: Long? = null,
)

@Serializable
data class ExtendResponseDto(
    @SerialName("reservation_id") val reservationId: Long,
    @SerialName("destination_stop_order") val destinationStopOrder: Int,
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("seat_label") val seatLabel: String,
    val status: String,
)
```

- [ ] **Step 2: Modelos de dominio en `Journey.kt`**

```kotlin
package com.appmovilidadclinica.passenger.shared.domain.model

import java.time.Instant

/** Estado de polling del pasajero (espejo de GET /reservations/{id}/journey). */
data class JourneyState(
    val reservationId: Long,
    val reservationStatus: ReservationStatus,
    val tripStatus: TripStatus,
    val destinationStopOrder: Int,
    val stops: List<TripStop>,
    val canExtend: Boolean,
    val extension: ExtensionOffer?,
)

data class ExtensionOffer(
    val currentSeatFree: Boolean,
    val remainingStops: List<ExtensionStop>,
)

data class ExtensionStop(
    val tripStopTimeId: Long,
    val stopName: String,
    val stopOrder: Int,
)

/** Resultado de POST /reservations/{id}/extend. */
data class ExtendResult(
    val reservationId: Long,
    val destinationStopOrder: Int,
    val tripSeatId: Long,
    val seatLabel: String,
    val status: ReservationStatus,
)
```

Nota: `Instant` no se usa en `JourneyState` porque `TripStop` ya lo tiene; si el compilador marca el import como no usado, quitarlo.

- [ ] **Step 3: Mapper en `JourneyMapper.kt`**

```kotlin
package com.appmovilidadclinica.passenger.data.mapper

import com.appmovilidadclinica.passenger.shared.data.remote.dto.ExtendResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.JourneyStateDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.JourneyStopDto
import com.appmovilidadclinica.passenger.shared.domain.model.ExtendResult
import com.appmovilidadclinica.passenger.shared.domain.model.ExtensionOffer
import com.appmovilidadclinica.passenger.shared.domain.model.ExtensionStop
import com.appmovilidadclinica.passenger.shared.domain.model.JourneyState
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStop
import com.appmovilidadclinica.passenger.shared.domain.model.TripStopStatus
import java.time.OffsetDateTime

private fun parseInstant(raw: String) = OffsetDateTime.parse(raw).toInstant()

fun JourneyStopDto.toDomain(): TripStop = TripStop(
    tripStopTimeId = tripStopTimeId,
    stopId = stopId,
    stopOrder = stopOrder,
    stopName = stopName,
    scheduledArrivalAt = parseInstant(scheduledArrivalAt),
    scheduledDepartureAt = parseInstant(scheduledDepartureAt),
    actualArrivalAt = actualArrivalAt?.let(::parseInstant),
    actualDepartureAt = actualDepartureAt?.let(::parseInstant),
    status = TripStopStatus.valueOf(status),
)

fun JourneyStateDto.toDomain(): JourneyState = JourneyState(
    reservationId = reservationId,
    reservationStatus = ReservationStatus.valueOf(reservationStatus),
    tripStatus = TripStatus.valueOf(tripStatus),
    destinationStopOrder = destinationStopOrder,
    stops = stops.sortedBy { it.stopOrder }.map { it.toDomain() },
    canExtend = canExtend,
    extension = extension?.let { offer ->
        ExtensionOffer(
            currentSeatFree = offer.currentSeatFree,
            remainingStops = offer.remainingStops.map {
                ExtensionStop(it.tripStopTimeId, it.stopName, it.stopOrder)
            },
        )
    },
)

fun ExtendResponseDto.toDomain(): ExtendResult = ExtendResult(
    reservationId = reservationId,
    destinationStopOrder = destinationStopOrder,
    tripSeatId = tripSeatId,
    seatLabel = seatLabel,
    status = ReservationStatus.valueOf(status),
)
```

- [ ] **Step 4: API en `KtorReservationsApi.kt`**

```kotlin
    suspend fun getJourney(reservationId: Long): HttpResponse =
        client.get("reservations/$reservationId/journey")

    suspend fun extend(reservationId: Long, body: ExtendRequestDto): HttpResponse =
        client.post("reservations/$reservationId/extend") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
```

- [ ] **Step 5: Repositorio (interfaz + impl)**

En `ReservationsRepository.kt`:

```kotlin
    /** GET /api/reservations/{id}/journey — estado de polling (semáforo + extensión). */
    suspend fun getJourney(reservationId: Long): AppResult<JourneyState>

    /**
     * POST /api/reservations/{id}/extend — estira la reserva a un nuevo destino.
     * `tripSeatId = null` conserva el asiento actual.
     */
    suspend fun extend(
        reservationId: Long,
        newDestinationTripStopTimeId: Long,
        tripSeatId: Long? = null,
    ): AppResult<ExtendResult>
```

En `ReservationsRepositoryImpl.kt`:

```kotlin
    override suspend fun getJourney(reservationId: Long): AppResult<JourneyState> {
        val result = safeApiCall<JourneyStateDto>(
            errorMapper = errorMapper,
            call = { apiClient.reservationsApi.getJourney(reservationId) },
            parseBody = { it.body() },
        )
        return result.map { it.toDomain() }
    }

    override suspend fun extend(
        reservationId: Long,
        newDestinationTripStopTimeId: Long,
        tripSeatId: Long?,
    ): AppResult<ExtendResult> {
        val result = safeApiCall<ExtendResponseDto>(
            errorMapper = errorMapper,
            call = {
                apiClient.reservationsApi.extend(
                    reservationId,
                    ExtendRequestDto(
                        newDestinationTripStopTimeId = newDestinationTripStopTimeId,
                        tripSeatId = tripSeatId,
                    ),
                )
            },
            parseBody = { it.body() },
        )
        return result.map { it.toDomain() }
    }
```

Agregar los imports correspondientes (`JourneyStateDto`, `ExtendResponseDto`, `ExtendRequestDto`, `JourneyState`, `ExtendResult`, `com.appmovilidadclinica.passenger.data.mapper.toDomain`).

- [ ] **Step 6: Verificar que compila**

Run (desde `passenger-android`): `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add passenger-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/passenger/shared/data/remote/dto/ReservationDto.kt passenger-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/passenger/shared/domain/model/Journey.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/mapper/JourneyMapper.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/remote/KtorReservationsApi.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/domain/repository/ReservationsRepository.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/repository/ReservationsRepositoryImpl.kt
git commit -m "feat(passenger): journey y extend en capa data"
```

---

### Task 11: App — ViewModel (polling + extensión)

**Files:**
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailViewModel.kt`

**Interfaces:**
- Consumes: `ReservationsRepository.getJourney/extend`, `ListSeatsUseCase`, `JourneyState`, `ExtensionOffer`, `TripSeat`.
- Produces: `MyReservationDetailUiState.canExtend`, `.extension`, `.extensionSeats`, `.extending`, `.extensionError`; `suspend fun refreshJourney()`, `fun loadExtensionSeats(tripStopTimeId: Long)`, `fun extend(newDestinationTripStopTimeId: Long, tripSeatId: Long?)`.

- [ ] **Step 1: Extender el estado**

Agregar a `MyReservationDetailUiState`:

```kotlin
    val canExtend: Boolean = false,
    val extension: ExtensionOffer? = null,
    val extensionSeats: List<TripSeat> = emptyList(),
    val loadingExtensionSeats: Boolean = false,
    val extending: Boolean = false,
    val extensionError: String? = null,
```

Imports nuevos: `ExtensionOffer`, `TripSeat`, `ListSeatsUseCase`.

- [ ] **Step 2: Inyectar `ListSeatsUseCase` y agregar el polling**

Agregar `private val listSeatsUseCase: ListSeatsUseCase` al constructor.

```kotlin
    /** Consulta el estado de polling. Lo llama el Screen desde repeatOnLifecycle. */
    suspend fun refreshJourney() {
        val reservation = _uiState.value.reservation ?: return
        when (val result = reservationsRepository.getJourney(reservation.reservationId)) {
            is AppResult.Success -> {
                val journey = result.data
                _uiState.update {
                    it.copy(
                        stops = journey.stops,
                        loadingStops = false,
                        canExtend = journey.canExtend,
                        extension = journey.extension,
                    )
                }
                if (journey.canExtend) {
                    journey.extension?.remainingStops?.firstOrNull()?.let {
                        loadExtensionSeats(it.tripStopTimeId)
                    }
                }
            }
            is AppResult.Failure -> _uiState.update { it.copy(loadingStops = false) }
        }
    }
```

- [ ] **Step 3: Cargar asientos y extender**

```kotlin
    /** Carga los asientos libres para el tramo [destino actual, destino elegido]. */
    fun loadExtensionSeats(tripStopTimeId: Long) {
        val state = _uiState.value
        val reservation = state.reservation ?: return
        val origin = state.stops.find { it.tripStopTimeId == reservation.destinationTripStopTimeId } ?: return
        val destination = state.stops.find { it.tripStopTimeId == tripStopTimeId } ?: return
        _uiState.update { it.copy(loadingExtensionSeats = true, extensionError = null) }
        viewModelScope.launch {
            when (val result = listSeatsUseCase(reservation.tripId, origin, destination)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(loadingExtensionSeats = false, extensionSeats = result.data)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(loadingExtensionSeats = false, extensionSeats = emptyList())
                }
            }
        }
    }

    /** Confirma la extensión al destino elegido (y opcionalmente a otro asiento). */
    fun extend(newDestinationTripStopTimeId: Long, tripSeatId: Long?) {
        val reservationId = _uiState.value.reservation?.reservationId ?: return
        _uiState.update { it.copy(extending = true, extensionError = null) }
        viewModelScope.launch {
            when (val result = reservationsRepository.extend(
                reservationId = reservationId,
                newDestinationTripStopTimeId = newDestinationTripStopTimeId,
                tripSeatId = tripSeatId,
            )) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(extending = false, extensionSeats = emptyList()) }
                    refreshJourney()
                }
                is AppResult.Failure -> {
                    val msg = (result.error as? AppError.Conflict)?.message
                        ?: "No se pudo extender el viaje. Intente nuevamente."
                    _uiState.update { it.copy(extending = false, extensionError = msg) }
                }
            }
        }
    }

    fun dismissExtensionError() = _uiState.update { it.copy(extensionError = null) }
```

Importar `AppError` (`com.appmovilidadclinica.passenger.shared.domain.error.AppError`).

- [ ] **Step 4: Verificar que compila**

Run (desde `passenger-android`): `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailViewModel.kt
git commit -m "feat(passenger): polling y extension en el ViewModel de reserva"
```

---

### Task 12: App — `SeatCell` común, tarjeta y bottom sheet de extensión

**Files:**
- Create: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/common/SeatCell.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailScreen.kt`

**Interfaces:**
- Consumes: `MyReservationDetailViewModel.refreshJourney/loadExtensionSeats/extend`, `SeatCell`.
- Produces: `SeatCell(seat, selected, onClick)` reutilizable.

- [ ] **Step 1: Extraer `SeatCell` a `presentation/common/SeatCell.kt`**

```kotlin
package com.appmovilidadclinica.passenger.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.passenger.shared.domain.model.TripSeat

/**
 * Celda de asiento reutilizable (selección de asiento y extensión de viaje).
 * `enabled` sale de `seat.isSelectable`: los ocupados/bloqueados no son
 * clickeables.
 */
@Composable
fun SeatCell(
    seat: TripSeat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        seat.isSelectable -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val stateDescription = when {
        selected -> "seleccionado"
        seat.isSelectable -> "disponible"
        else -> "ocupado"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = seat.isSelectable, onClick = onClick)
            .semantics { contentDescription = "Asiento ${seat.seatLabel}, $stateDescription" },
        contentAlignment = Alignment.Center,
    ) {
        Text(seat.seatLabel, color = textColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(14.dp),
            )
        } else if (!seat.isSelectable) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(14.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Usar el `SeatCell` común en `SeatSelectionScreen.kt`**

Borrar la función `private fun SeatCell(...)` del archivo y agregar el import `com.appmovilidadclinica.passenger.presentation.common.SeatCell`. El call site (`SeatCell(seat = seat, selected = ..., onClick = ...)`) no cambia porque los parámetros son los mismos. Quitar los imports que queden sin usar (`border`, `clickable`, `Lock`, `Check`, `semantics`, `contentDescription`).

- [ ] **Step 3: Polling en `MyReservationDetailScreen.kt`**

Agregar imports: `androidx.lifecycle.compose.LocalLifecycleOwner`, `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.repeatOnLifecycle`, `kotlinx.coroutines.delay`, `com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus`.

Dentro del composable, después de `val reservation = state.reservation`:

```kotlin
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state.reservation?.status) {
        if (state.reservation?.status == ReservationStatus.BOARDED) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refreshJourney()
                    delay(20_000)
                }
            }
        }
    }
```

- [ ] **Step 4: Tarjeta de extensión + bottom sheet**

Agregar el estado del sheet y el banner dentro del `Column` (después de la Card del "Recorrido"):

```kotlin
    var showExtensionSheet by remember { mutableStateOf(false) }

    if (state.canExtend) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (state.extension?.currentSeatFree == true) {
                        "Estás por llegar a tu destino. ¿Querés extender tu viaje?"
                    } else {
                        "Tu asiento ya se asignó a otra persona. Podés elegir otro para seguir viaje."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showExtensionSheet = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Extender viaje")
                }
            }
        }
    }
```

Y al final del composable (fuera del `Scaffold`, junto al `AlertDialog` de cancelación), el bottom sheet:

```kotlin
    if (showExtensionSheet && state.extension != null) {
        ExtensionBottomSheet(
            remainingStops = state.extension!!.remainingStops,
            seats = state.extensionSeats,
            loadingSeats = state.loadingExtensionSeats,
            extending = state.extending,
            errorMessage = state.extensionError,
            onStopSelected = viewModel::loadExtensionSeats,
            onConfirm = { tripStopTimeId, tripSeatId ->
                viewModel.extend(tripStopTimeId, tripSeatId)
                showExtensionSheet = false
            },
            onDismiss = { showExtensionSheet = false; viewModel.dismissExtensionError() },
        )
    }
```

Agregar la implementación (en el mismo archivo):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionBottomSheet(
    remainingStops: List<com.appmovilidadclinica.passenger.shared.domain.model.ExtensionStop>,
    seats: List<com.appmovilidadclinica.passenger.shared.domain.model.TripSeat>,
    loadingSeats: Boolean,
    extending: Boolean,
    errorMessage: String?,
    onStopSelected: (Long) -> Unit,
    onConfirm: (Long, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedStopId by remember { mutableStateOf(remainingStops.firstOrNull()?.tripStopTimeId) }
    var selectedSeatId by remember { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Extender viaje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = remainingStops.find { it.tripStopTimeId == selectedStopId }?.stopName.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Nuevo destino") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    remainingStops.forEach { stop ->
                        DropdownMenuItem(
                            text = { Text(stop.stopName) },
                            onClick = {
                                selectedStopId = stop.tripStopTimeId
                                selectedSeatId = null
                                expanded = false
                                onStopSelected(stop.tripStopTimeId)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (loadingSeats) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                ) {
                    items(seats, key = { it.tripSeatId }) { seat ->
                        SeatCell(
                            seat = seat,
                            selected = selectedSeatId == seat.tripSeatId,
                            onClick = { selectedSeatId = seat.tripSeatId },
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onConfirm(selectedStopId ?: return@Button, selectedSeatId) },
                enabled = selectedStopId != null && !extending,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (extending) "Extendiendo…" else "Confirmar extensión")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
```

Imports nuevos en el screen: `androidx.compose.foundation.lazy.grid.LazyVerticalGrid`, `GridCells`, `items`, `androidx.compose.material3.ModalBottomSheet`, `rememberModalBottomSheetState`, `ExposedDropdownMenuBox`, `ExposedDropdownMenuDefaults`, `DropdownMenuItem`, `OutlinedTextField`, `androidx.compose.material3.ExperimentalMaterial3Api`, `androidx.compose.runtime.mutableStateOf`, `remember`, `setValue`, `androidx.compose.foundation.layout.heightIn`, `com.appmovilidadclinica.passenger.presentation.common.SeatCell`.

- [ ] **Step 5: Verificar que compila**

Run (desde `passenger-android`): `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/common/SeatCell.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailScreen.kt
git commit -m "feat(passenger): cartel y bottom sheet de extension de viaje"
```

---

### Task 13: App — tests de ViewModel

**Files:**
- Modify: `passenger-android/gradle/libs.versions.toml` (agregar `junit`)
- Modify: `passenger-android/app/build.gradle.kts` (test deps)
- Create: `passenger-android/app/src/test/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `MyReservationDetailViewModel`, `ReservationsRepository`, `TripsRepository`.

- [ ] **Step 1: Agregar JUnit al catálogo**

En `passenger-android/gradle/libs.versions.toml`, en `[libraries]`:

```toml
junit = { group = "junit", name = "junit", version = "4.13.2" }
```

- [ ] **Step 2: Agregar las dependencias de test**

En `passenger-android/app/build.gradle.kts`, dentro de `dependencies { ... }`:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: Escribir el test (debe fallar primero por fakes ausentes)**

```kotlin
package com.appmovilidadclinica.passenger.presentation.myreservation

import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import com.appmovilidadclinica.passenger.domain.repository.ReservationTripContext
import com.appmovilidadclinica.passenger.domain.repository.TripsRepository
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.ExtendResult
import com.appmovilidadclinica.passenger.shared.domain.model.JourneyState
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationRequest
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStop
import com.appmovilidadclinica.passenger.shared.domain.model.TripStopStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private class FakeReservationsRepository : ReservationsRepository {
    var journey = JourneyState(
        reservationId = 1,
        reservationStatus = ReservationStatus.BOARDED,
        tripStatus = TripStatus.IN_PROGRESS,
        destinationStopOrder = 4,
        stops = emptyList(),
        canExtend = false,
        extension = null,
    )
    var extendResult: AppResult<ExtendResult> = AppResult.Failure(
        com.appmovilidadclinica.passenger.shared.domain.error.AppError.Conflict(message = "tomado"),
    )
    var extendCalls = 0

    override suspend fun getJourney(reservationId: Long): AppResult<JourneyState> =
        AppResult.Success(journey)

    override suspend fun extend(
        reservationId: Long,
        newDestinationTripStopTimeId: Long,
        tripSeatId: Long?,
    ): AppResult<ExtendResult> {
        extendCalls++
        return extendResult
    }

    override suspend fun confirm(request: ReservationRequest, tripContext: ReservationTripContext) =
        throw UnsupportedOperationException()
    override suspend fun cancel(reservationId: Long) = throw UnsupportedOperationException()
    override suspend fun selfCheckin(reservationId: Long) = throw UnsupportedOperationException()
    override suspend fun reportIncident(reservationId: Long, incidentType: String, description: String) =
        throw UnsupportedOperationException()
    override fun observeReservations(): Flow<List<Reservation>> = flowOf(emptyList())
    override fun observeReservation(reservationId: Long): Flow<Reservation?> = flowOf(null)
    override suspend fun syncFromBackend() = throw UnsupportedOperationException()
}

private class FakeTripsRepository : TripsRepository {
    override suspend fun search(
        date: String,
        origin: Long?,
        destination: Long?,
        direction: String?,
    ) = throw UnsupportedOperationException()

    override suspend fun getDetail(tripId: Long) = throw UnsupportedOperationException()

    override suspend fun listSeats(
        tripId: Long,
        origin: TripStop,
        destination: TripStop,
    ) = throw UnsupportedOperationException()
}

class MyReservationDetailViewModelTest {

    @Test
    fun `refreshJourney publica canExtend del backend`() = runTest {
        val repo = FakeReservationsRepository()
        repo.journey = repo.journey.copy(canExtend = true)

        val vm = MyReservationDetailViewModel(
            savedStateHandle = savedStateHandleFor(1L),
            reservationsRepository = repo,
            tripsRepository = FakeTripsRepository(),
            generateQrUseCase = com.appmovilidadclinica.passenger.domain.usecase.GenerateQrUseCase(),
        )

        vm.refreshJourney()

        assertTrue(vm.uiState.value.canExtend)
    }

    @Test
    fun `extend con 409 deja extensionError y no lanza`() = runTest {
        val repo = FakeReservationsRepository()
        val vm = MyReservationDetailViewModel(
            savedStateHandle = savedStateHandleFor(1L),
            reservationsRepository = repo,
            tripsRepository = FakeTripsRepository(),
            generateQrUseCase = com.appmovilidadclinica.passenger.domain.usecase.GenerateQrUseCase(),
        )

        vm.extend(newDestinationTripStopTimeId = 55, tripSeatId = null)
        advanceUntilIdle()

        assertEquals(1, repo.extendCalls)
        assertEquals("tomado", vm.uiState.value.extensionError)
    }
}

private fun savedStateHandleFor(reservationId: Long) =
    androidx.lifecycle.SavedStateHandle(mapOf("reservationId" to reservationId))
```

Nota: ajustar `savedStateHandleFor` a cómo `Screen.MyReservationDetail` se serializa (el `toRoute<Screen.MyReservationDetail>()` espera la clave del parámetro). Si el `SavedStateHandle` no resuelve, usar el nombre exacto del campo de `Screen.MyReservationDetail` (ver `presentation/navigation/Screen.kt`).

- [ ] **Step 4: Correr los tests**

Run (desde `passenger-android`): `./gradlew :app:testDebugUnitTest --tests "*MyReservationDetailViewModelTest*"`
Expected: `BUILD SUCCESSFUL`, 2 tests pasando.

- [ ] **Step 5: Commit**

```bash
git add passenger-android/gradle/libs.versions.toml passenger-android/app/build.gradle.kts passenger-android/app/src/test/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailViewModelTest.kt
git commit -m "test(passenger): polling y extension del ViewModel de reserva"
```

---

## Self-Review

**1. Spec coverage**

| Requisito del spec | Tarea |
|---|---|
| Aviso un paradero antes | Task 5 (`can_extend` con `last_departed+1`) + Task 12 (cartel) |
| Asiento libre → extender | Task 5 (`current_seat_free`) + Task 12 |
| Asiento tomado → aviso + otros libres | Task 12 (mensaje + grilla) |
| Lista desplegable de paraderos restantes | Task 5 (`remaining_stops`) + Task 12 |
| Actualiza la misma reserva | Task 2 (`sp_extend_reservation`) |
| Sin re-confirmar abordaje | Task 2 (tramos nuevos → `OCCUPIED`) |
| Bajada automática en la llegada | Task 3 (`sp_mark_trip_stop_arrival`) |
| Sin respuesta → se baja solo | Task 3 (baja a todos los BOARDED con ese destino) |
| Cerrar BOARDED al completar viaje | Task 3 + Task 4 |
| Semáforo en vivo | Task 5 (`stops`) + Task 11/12 (polling 20s) |
| Reporte en "Disponibilidad de asientos" | Tasks 1, 8, 9 |
| Polling, no push | Task 11/12 |
| Migración 0006 | Tasks 1-3 |
| Tests backend | Task 7 |
| Tests app | Task 13 |

**2. Placeholder scan:** sin TBD/TODO; todos los steps con código real.

**3. Type consistency:** `JourneyState`/`JourneyStop`/`ExtensionOffer`/`ExtensionStop` (Go) ↔ `JourneyStateDto`/`JourneyStopDto`/`ExtensionOfferDto`/`ExtensionStopDto` (Kotlin) con los mismos `@SerialName`. `ExtendParams`/`ExtendResult` (Go) ↔ `ExtendRequestDto`/`ExtendResponseDto` (Kotlin). `original_destination_stop_order` (SQL) ↔ `original_destination_name` (vista/reporte). `CompleteTrip(ctx, tripID, driverID)` cambia en Task 4 en interfaz, impl y service a la vez.
