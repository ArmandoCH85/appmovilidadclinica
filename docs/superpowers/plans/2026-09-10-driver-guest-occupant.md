# Registro de pasajero sin app por el conductor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El conductor ocupa un asiento libre con un pasajero sin app (nombre + apellido + tramo) desde un mapa de asientos nuevo en su app.

**Architecture:** Backend: tabla `guest_occupants` + SP atómico `sp_register_guest_occupant` (espejo de `sp_confirm_reservation`) + endpoint `POST /api/driver/trips/{id}/guest-occupants` solo-DRIVER; el inventario `trip_seat_segments` gana `guest_occupant_id` nullable, así `sp_list_trip_seats` sigue funcionando sin cambios (marca `OCCUPIED`). App driver (KMP `driver-android/shared`): ruta `Route.SeatMap` + pantalla con selectores de tramo, mapa de asientos y diálogo de nombre, reusando `GET /api/trips/{id}/seats` (el módulo trips no tiene guard de rol: cualquier JWT válido lo llama).

**Tech Stack:** Go + MariaDB (stored procedures), Kotlin Multiplatform + Compose Multiplatform + Ktor + Koin (driver app, ViewModels manuales con `remember`, repositorio por `koinInject`).

## Global Constraints

- Solo nombre y apellido del invitado (2–100 caracteres cada uno). Sin DNI, sin crear usuario.
- Unicidad: mismo nombre + apellido no se repite en el mismo viaje (case-insensitive) → 409.
- No tocar el flujo de reservas de pasajeros, ni `sp_confirm_reservation`, ni el panel admin.
- Mock a mano en tests Go, sin mockery.
- La driver app no tiene tests UI: verificación manual en emulador.
- Commits convencionales, sin atribución a IA.
- Desplegar el backend ANTES o junto con la app (si no: 404). La migración corre sola al reiniciar (schema idempotente), pero recrea el schema: coordinar como siempre.

---

## File Structure

| Archivo | Responsabilidad |
|---|---|
| `backend/migrations/0001_schema.up.sql` (modificar) | Tabla `guest_occupants`, columna `trip_seat_segments.guest_occupant_id` + FK, SP `sp_register_guest_occupant` |
| `backend/internal/modules/driver/repository.go` (modificar) | `GuestOccupantParams`, `RegisterGuest` (llama al SP), `GetTripPassengers` con UNION de invitados, campo invitado en `Passenger` |
| `backend/internal/modules/driver/service.go` (modificar) | `RegisterGuestOccupant` + `requireDriver`/`ensureAssigned` existentes + método en interfaz |
| `backend/internal/modules/driver/service_test.go` (modificar) | Extender `mockDriverRepo` + tests TDD |
| `backend/internal/modules/driver/handler.go` (modificar) | `RegisterGuest` + ruta |
| `driver-android/shared/.../data/remote/dto/SeatDto.kt` (crear) | `SeatAvailabilityDto` (espejo de `SeatResult` del backend) |
| `driver-android/shared/.../data/remote/dto/GuestOccupantDto.kt` (crear) | Request/response del registro |
| `driver-android/shared/.../data/remote/DriverApi.kt` (modificar) | `getSeats` + `registerGuest` |
| `driver-android/shared/.../domain/model/SeatAvailability.kt` (crear) | Modelo de asiento + disponibilidad |
| `driver-android/shared/.../domain/model/Passenger.kt` (modificar) | Campos de invitado (ver Task 5) |
| `driver-android/shared/.../data/remote/dto/PassengerDto.kt` (modificar) | Campos de invitado (ver Task 5) |
| `driver-android/shared/.../domain/repository/DriverRepository.kt` (modificar) | `getSeats` + `registerGuest` (+ mapeo de invitados donde corresponda) |
| `driver-android/shared/.../ui/navigation/Route.kt` (modificar) | `Route.SeatMap(tripId)` |
| `driver-android/shared/.../ui/navigation/DriverNavGraph.kt` (modificar) | Slot SeatMap + callback desde TripDetail |
| `driver-android/shared/.../ui/screens/seatmap/SeatMapScreen.kt` (crear) | Tramo + mapa + diálogo de nombre |
| `driver-android/shared/.../ui/screens/seatmap/SeatMapViewModel.kt` (crear) | Estado, validación, submit |
| `driver-android/shared/.../ui/screens/tripdetail/TripDetailScreen.kt` (modificar) | Botón "Ocupar asiento" + badge de invitado en lista |

---

### Task 1: Migración — tabla, columna de inventario y SP atómico

**Files:**
- Modify: `backend/migrations/0001_schema.up.sql`
- Modify: `backend/migrations/0001_schema.down.sql` (agregar los DROP espejo)

**Interfaces:**
- Consumes: tablas `trip_instances`, `trip_seats`, `trip_segments`, `trip_seat_segments`, `trip_stop_times`, `users` (todas existentes).
- Produces: tabla `guest_occupants`, `trip_seat_segments.guest_occupant_id`, SP `sp_register_guest_occupant` — los usa la Task 2.

Contexto que el implementador debe leer primero: `0001_schema.up.sql` líneas 1406-1459 (`sp_list_trip_seats`, no se toca), 1467-1730 (`sp_confirm_reservation`, molde del SP nuevo), 494-516 (`trip_seat_segments`).

- [ ] **Step 1: Tabla `guest_occupants`** — agregar después del `CREATE TABLE trip_incidents` (línea ~646):

```sql
-- 22b. Ocupantes invitados: pasajeros sin app registrados por el conductor
--     (solo nombre + apellido, sin usuario ni DNI). Nacen abordados.
CREATE TABLE guest_occupants (
    id                              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id                         BIGINT UNSIGNED NOT NULL,
    trip_seat_id                    BIGINT UNSIGNED NOT NULL,
    origin_trip_stop_time_id        BIGINT UNSIGNED NOT NULL,
    destination_trip_stop_time_id   BIGINT UNSIGNED NOT NULL,
    origin_stop_order               SMALLINT UNSIGNED NOT NULL,
    destination_stop_order          SMALLINT UNSIGNED NOT NULL,
    first_name                      VARCHAR(100) NOT NULL,
    last_name                       VARCHAR(100) NOT NULL,
    registered_by_user_id           BIGINT UNSIGNED NOT NULL,
    status                          ENUM('BOARDED') NOT NULL DEFAULT 'BOARDED',
    created_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_guest_occupants_trip
        FOREIGN KEY (trip_id) REFERENCES trip_instances(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_guest_occupants_seat
        FOREIGN KEY (trip_seat_id) REFERENCES trip_seats(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_guest_occupants_origin
        FOREIGN KEY (origin_trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_guest_occupants_destination
        FOREIGN KEY (destination_trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_guest_occupants_registered_by
        FOREIGN KEY (registered_by_user_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 2: Columna en el inventario** — después del `CREATE TABLE trip_seat_segments` agregar:

```sql
ALTER TABLE trip_seat_segments
    ADD COLUMN guest_occupant_id BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_trip_seat_segments_guest
        FOREIGN KEY (guest_occupant_id) REFERENCES guest_occupants(id)
        ON UPDATE RESTRICT ON DELETE SET NULL;
```

(Con `state = 'OCCUPIED'`, `sp_list_trip_seats` excluye esos tramos sin
cambios: su `CASE` marca ocupado todo `state <> 'AVAILABLE'`.)

- [ ] **Step 3: SP `sp_register_guest_occupant`** — agregar en la sección G
(después de `sp_confirm_reservation`, antes del próximo objeto; usa
delimitador `$$` como los SP vecinos). Espejo de `sp_confirm_reservation`
(sin QR, sin worker, sin `reservation_segments`/`reservation_events` —
YAGNI), más el chequeo de nombre duplicado:

```sql
-- Registra un ocupante invitado (pasajero sin app) y marca solo los
-- segmentos solicitados como OCCUPIED. Concurrencia igual que
-- sp_confirm_reservation: UPDATE sin cambio para bloquear filas + conteo
-- de conflictos dentro de la transacción.
CREATE PROCEDURE sp_register_guest_occupant(
    IN p_trip_id BIGINT UNSIGNED,
    IN p_trip_seat_id BIGINT UNSIGNED,
    IN p_origin_trip_stop_time_id BIGINT UNSIGNED,
    IN p_destination_trip_stop_time_id BIGINT UNSIGNED,
    IN p_first_name VARCHAR(100),
    IN p_last_name VARCHAR(100),
    IN p_registered_by_user_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_origin_order SMALLINT UNSIGNED;
    DECLARE v_destination_order SMALLINT UNSIGNED;
    DECLARE v_expected_segments INT;
    DECLARE v_inventory_rows INT;
    DECLARE v_conflicting_segments INT;
    DECLARE v_duplicate INT;
    DECLARE v_guest_id BIGINT UNSIGNED;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    IF NOT EXISTS (
        SELECT 1 FROM trip_seats
         WHERE id = p_trip_seat_id
           AND trip_id = p_trip_id
           AND is_blocked = 0
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El asiento no pertenece al viaje o está bloqueado';
    END IF;

    SELECT origin_time.stop_order, destination_time.stop_order
      INTO v_origin_order, v_destination_order
      FROM trip_stop_times origin_time
      JOIN trip_stop_times destination_time
        ON destination_time.id = p_destination_trip_stop_time_id
       AND destination_time.trip_id = origin_time.trip_id
     WHERE origin_time.id = p_origin_trip_stop_time_id
       AND origin_time.trip_id = p_trip_id;

    IF v_origin_order IS NULL
       OR v_destination_order IS NULL
       OR v_origin_order >= v_destination_order THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El origen y destino no forman un tramo válido';
    END IF;

    -- Mismo nombre + apellido ya registrado en el viaje (case-insensitive).
    SELECT COUNT(*)
      INTO v_duplicate
      FROM guest_occupants
     WHERE trip_id = p_trip_id
       AND LOWER(first_name) = LOWER(p_first_name)
       AND LOWER(last_name) = LOWER(p_last_name);

    IF v_duplicate > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Ese pasajero ya está registrado en este viaje';
    END IF;

    -- Bloqueo de filas exactas (sin cambio funcional).
    UPDATE trip_seat_segments inventory
    JOIN trip_segments segment
      ON segment.id = inventory.trip_segment_id
     SET inventory.updated_at = CURRENT_TIMESTAMP
   WHERE inventory.trip_seat_id = p_trip_seat_id
     AND segment.trip_id = p_trip_id
     AND segment.segment_order >= v_origin_order
     AND segment.segment_order < v_destination_order;

    SELECT COUNT(*),
           SUM(CASE WHEN inventory.state <> 'AVAILABLE' THEN 1 ELSE 0 END)
      INTO v_inventory_rows, v_conflicting_segments
      FROM trip_seat_segments inventory
      JOIN trip_segments segment
        ON segment.id = inventory.trip_segment_id
     WHERE inventory.trip_seat_id = p_trip_seat_id
       AND segment.trip_id = p_trip_id
       AND segment.segment_order >= v_origin_order
       AND segment.segment_order < v_destination_order;

    SET v_expected_segments = v_destination_order - v_origin_order;

    IF v_inventory_rows <> v_expected_segments THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El inventario por segmentos del asiento está incompleto';
    END IF;

    IF COALESCE(v_conflicting_segments, 0) > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El asiento ya está ocupado en uno o más tramos solicitados';
    END IF;

    INSERT INTO guest_occupants (
        trip_id, trip_seat_id,
        origin_trip_stop_time_id, destination_trip_stop_time_id,
        origin_stop_order, destination_stop_order,
        first_name, last_name, registered_by_user_id, status
    ) VALUES (
        p_trip_id, p_trip_seat_id,
        p_origin_trip_stop_time_id, p_destination_trip_stop_time_id,
        v_origin_order, v_destination_order,
        p_first_name, p_last_name, p_registered_by_user_id, 'BOARDED'
    );

    SET v_guest_id = LAST_INSERT_ID();

    UPDATE trip_seat_segments inventory
    JOIN trip_segments segment
      ON segment.id = inventory.trip_segment_id
     SET inventory.state = 'OCCUPIED',
         inventory.guest_occupant_id = v_guest_id,
         inventory.reserved_at = CURRENT_TIMESTAMP,
         inventory.released_at = NULL
   WHERE inventory.trip_seat_id = p_trip_seat_id
     AND segment.trip_id = p_trip_id
     AND segment.segment_order >= v_origin_order
     AND segment.segment_order < v_destination_order;

    COMMIT;

    SELECT v_guest_id AS id;
END$$
```

- [ ] **Step 4: `down.sql` espejo** — junto al `DROP PROCEDURE IF EXISTS sp_list_trip_seats` y `DROP TABLE IF EXISTS trip_incidents` existentes, agregar:

```sql
DROP PROCEDURE IF EXISTS sp_register_guest_occupant;
DROP TABLE IF EXISTS guest_occupants;
```

(más la reversión de la columna, SQL válido en MariaDB; ubicarla junto a
los otros DROP):

```sql
ALTER TABLE trip_seat_segments DROP FOREIGN KEY fk_trip_seat_segments_guest;
ALTER TABLE trip_seat_segments DROP COLUMN guest_occupant_id;
```

- [ ] **Step 5: Commit**

```bash
git add backend/migrations/0001_schema.up.sql backend/migrations/0001_schema.down.sql
git commit -m "feat(backend): tabla guest_occupants y SP de registro por conductor"
```

Nota: no hay test automatizado de SP sin BD; la verificación es deploy en
staging + curl (Task 7). Revisar dos veces nombres de columnas contra las
tablas reales (`trip_stop_times.stop_order`, `trip_seats.is_blocked`).

---

### Task 2: Repositorio driver — `RegisterGuest` + pasajeros con invitados

**Files:**
- Modify: `backend/internal/modules/driver/repository.go`

**Interfaces:**
- Consumes: SP `sp_register_guest_occupant` (Task 1); error `45000` traducido por el repo (ver cómo `SelfCheckin` del módulo booking traduce SIGNAL vía `dberr.TranslateSP` — leer ese código y replicar el manejo).
- Produces: `GuestOccupantParams`, `RegisterGuest(ctx, p) (int64, error)`, `Passenger` extendido con invitado — los usa la Task 3.

- [ ] **Step 1: Params + métodos en interfaz**

```go
// GuestOccupantParams agrupa los campos para sp_register_guest_occupant.
type GuestOccupantParams struct {
	TripID                   int64
	TripSeatID               int64
	OriginTripStopTimeID     int64
	DestinationTripStopTimeID int64
	FirstName                string
	LastName                 string
}
```

En la interfaz `DriverRepository`, junto a `ReportIncident`:

```go
	// RegisterGuest llama a sp_register_guest_occupant y devuelve el id del
	// invitado creado. El SP valida tramo, asiento libre y nombre duplicado.
	RegisterGuest(ctx context.Context, p GuestOccupantParams, reporterUserID int64) (int64, error)
```

- [ ] **Step 2: Implementación** (junto a `ReportIncident` del repo, línea ~441):

```go
// RegisterGuest invoca sp_register_guest_occupant. Los SIGNAL '45000' del SP
// (asiento ocupado, tramo inválido, nombre duplicado) se traducen igual que
// en el resto del módulo (ver manejo de errores del repo).
func (r *driverRepository) RegisterGuest(ctx context.Context, p GuestOccupantParams, reporterUserID int64) (int64, error) {
	var id int64
	err := r.db.QueryRowContext(ctx, "CALL sp_register_guest_occupant(?, ?, ?, ?, ?, ?, ?)",
		p.TripID, p.TripSeatID, p.OriginTripStopTimeID, p.DestinationTripStopTimeID,
		p.FirstName, p.LastName, reporterUserID,
	).Scan(&id)
	if err != nil {
		return 0, fmt.Errorf("registrando ocupante invitado: %w", err)
	}
	return id, nil
}
```

(Verificar en el archivo cómo se traducen los errores `45000` de otros SP
del módulo — p. ej. `MarkBoarded` — y aplicar el mismo tratamiento al `err`
antes de envolverlo.)

- [ ] **Step 3: Invitados en `GetTripPassengers`** — extender el struct
`Passenger` con:

```go
	IsGuest          bool   `json:"is_guest"`
	GuestDisplayName string `json:"guest_display_name,omitempty"`
```

y agregar UNION a la query (después del SELECT de reservas, antes del
`ORDER BY`): misma forma (mismas columnas en el mismo orden), con
`reservation_id = 0`, `reservation_code = ''`, `worker_id = 0`,
`worker_full_name = CONCAT(first_name, ' ', last_name)`,
`is_guest = TRUE`, `guest_display_name` igual, `status = 'BOARDED'`,
`confirmed_at = created_at`, `boarded_at = created_at`. Orden final por
`origin_stop_order, seat.seat_number` (aplicar el ORDER BY al resultado
unido).

- [ ] **Step 4: Commit**

```bash
git add backend/internal/modules/driver/repository.go
git commit -m "feat(driver): registro de invitado y pasajeros con invitados en repositorio"
```

---

### Task 3: Servicio driver + handler + ruta (TDD)

**Files:**
- Modify: `backend/internal/modules/driver/service.go`
- Modify: `backend/internal/modules/driver/service_test.go`
- Modify: `backend/internal/modules/driver/handler.go`

**Interfaces:**
- Consumes: `RegisterGuest` (Task 2); `requireDriver`, `ensureAssigned` (existentes en `service.go`).
- Produces: `POST /api/driver/trips/{id}/guest-occupants` → 201 `{"id"}` — lo usa la Task 5.

- [ ] **Step 1: Extender `mockDriverRepo`** en `service_test.go` (leer el mock
existente: campos `reportIncidentID/reportIncidentErr` y método
`ReportIncident` en líneas ~36-93 — agregar `guestID/guestErr` +
`RegisterGuest` con la misma forma). Agregar request del servicio en
`service.go`:

```go
// RegisterGuestRequest es el cuerpo de POST /driver/trips/{id}/guest-occupants.
// El trip_id viaja en el path y el conductor en el JWT.
type RegisterGuestRequest struct {
	TripSeatID                int64  `json:"trip_seat_id" validate:"required,gt=0"`
	OriginTripStopTimeID      int64  `json:"origin_trip_stop_time_id" validate:"required,gt=0"`
	DestinationTripStopTimeID int64  `json:"destination_trip_stop_time_id" validate:"required,gt=0"`
	FirstName                 string `json:"first_name" validate:"required,min=2,max=100"`
	LastName                  string `json:"last_name" validate:"required,min=2,max=100"`
}
```

Método en la interfaz `DriverService` + implementación (espejo de
`ReportIncident` en `service.go:230-239`):

```go
// RegisterGuestOccupant registra un invitado (pasajero sin app) en un
// asiento libre del viaje del conductor.
func (s *driverService) RegisterGuestOccupant(ctx context.Context, tripID int64, req RegisterGuestRequest) (int64, error) {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return 0, err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return 0, err
	}
	return s.repo.RegisterGuest(ctx, GuestOccupantParams{
		TripID:                   tripID,
		TripSeatID:               req.TripSeatID,
		OriginTripStopTimeID:     req.OriginTripStopTimeID,
		DestinationTripStopTimeID: req.DestinationTripStopTimeID,
		FirstName:                req.FirstName,
		LastName:                 req.LastName,
	}, driverID)
}
```

- [ ] **Step 2: Tests que fallan primero** — en `service_test.go`, con el
estilo de los tests de `ReportIncident` existentes (leerlos) más un helper
de contexto DRIVER (el módulo ya debe tener uno para sus tests — reusarlo;
si solo hay de driver válido, agregar variante con rol WORKER para el caso
403):

```go
func TestRegisterGuestOccupant_Success_ReturnsID(t *testing.T) { /* repo devuelve 700, asignado OK → id 700, sin error */ }
func TestRegisterGuestOccupant_NotAssigned_ReturnsForbidden(t *testing.T) { /* ensureAssigned falla → ForbiddenError, el repo no debe llamarse */ }
func TestRegisterGuestOccupant_NonDriverRole_ReturnsForbidden(t *testing.T) { /* ctx rol WORKER → ForbiddenError */ }
func TestRegisterGuestOccupant_RepoConflict_BubblesUp(t *testing.T) { /* repo devuelve ConflictError (asiento ocupado / duplicado) → mismo error */ }
```

Run: `cd backend && go test ./internal/modules/driver/ -run TestRegisterGuestOccupant -v` → Expected: FAIL (método/request indefinidos).

- [ ] **Step 3: Implementar** (Step 1) y correr `go test ./internal/modules/driver/ -v` → Expected: PASS todos.

- [ ] **Step 4: Handler + ruta** — en `handler.go`, espejo de
`ReportIncident` (`handler.go:200-223`): parsear `{id}` del path con el
helper del archivo (`parseID(w, r, "id")`), decodificar
`RegisterGuestRequest`, `validate.Default.Struct`, llamar al servicio,
responder 201:

```go
// registerGuestResponse es la respuesta de POST /driver/trips/{id}/guest-occupants.
type registerGuestResponse struct {
	ID int64 `json:"id"`
}

// RegisterGuest maneja POST /driver/trips/{id}/guest-occupants — registra
// un ocupante invitado (pasajero sin app) en un asiento libre. El trip_id
// autoritativo es el del path; el conductor sale del JWT.
func (h *DriverHandler) RegisterGuest(w http.ResponseWriter, r *http.Request) {
	tripID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req RegisterGuestRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	id, err := h.svc.RegisterGuestOccupant(r.Context(), tripID, req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, registerGuestResponse{ID: id})
}
```

En `RegisterRoutes`, junto a `r.Post("/trips/{id}/incidents", h.ReportIncident)`:

```go
		r.Post("/trips/{id}/guest-occupants", h.RegisterGuest)
```

- [ ] **Step 5: Verificar compilación + tests del paquete**

Run: `cd backend && go build ./... && go test ./internal/modules/driver/`
Expected: build OK, tests PASS (incluye los 4 `TestRegisterGuestOccupant_*`).

- [ ] **Step 6: Commit**

```bash
git add backend/internal/modules/driver/service.go backend/internal/modules/driver/service_test.go backend/internal/modules/driver/handler.go
git commit -m "feat(driver): endpoint de registro de invitado por conductor"
```

---

### Task 4: App driver — capa data (asientos + registro + invitado en pasajeros)

**Files:**
- Create: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/remote/dto/SeatDto.kt`
- Create: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/remote/dto/GuestOccupantDto.kt`
- Create: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/domain/model/SeatAvailability.kt`
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/remote/DriverApi.kt`
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/domain/repository/DriverRepository.kt`
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/repository/AuthRepositoryImpl.kt` — NO. La implementación concreta del repositorio vive en `androidMain` (ver `AuthRepositoryImpl.kt` en `shared/src/androidMain/.../data/repository/`). Leer ese archivo y el `DriverRepository` implementado allí (buscar `class .*DriverRepository` en `androidMain`) y agregar los métodos espejando `reportIncident`.
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/domain/model/Passenger.kt`
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/remote/dto/PassengerDto.kt`

**Interfaces:**
- Consumes: `GET /api/trips/{id}/seats?origin=&destination=` (sin guard de rol, cualquier JWT) y `POST /api/driver/trips/{id}/guest-occupants` → 201 `{"id"}` (Task 3).
- Produces: `DriverRepository.getSeats` + `registerGuest` + modelo con invitado — los usa la Task 5.

**Antes de empezar:** leer `androidMain/.../data/repository/AuthRepositoryImpl.kt` para ubicar la implementación de `DriverRepository` (misma clase/archivo), `domain/model/Passenger.kt`, `data/remote/dto/PassengerDto.kt` y el mapeo DTO→dominio existente.

- [ ] **Step 1: DTOs** — el backend devuelve `SeatResult` con JSON exacto
`trip_seat_id: Long`, `seat_number: Int`, `seat_label: String`,
`availability: String` (`"AVAILABLE"`, `"OCCUPIED_IN_REQUESTED_RANGE"`,
`"BLOCKED"` — ver `backend/internal/modules/trips/repository.go:38-44`).
Crear `.../data/remote/dto/SeatDto.kt`:

```kotlin
package com.appmovilidadclinica.driver.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeatAvailabilityDto(
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("seat_number") val seatNumber: Int,
    @SerialName("seat_label") val seatLabel: String,
    val availability: String,
)
```

Crear también `.../data/remote/dto/GuestOccupantDto.kt`:

```kotlin
package com.appmovilidadclinica.driver.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GuestOccupantRequestDto(
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("origin_trip_stop_time_id") val originTripStopTimeId: Long,
    @SerialName("destination_trip_stop_time_id") val destinationTripStopTimeId: Long,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
)

@Serializable
data class GuestOccupantResponseDto(val id: Long)
```

- [ ] **Step 2: `DriverApi`** — junto a `reportIncident`:

```kotlin
    suspend fun getSeats(tripId: Long, originTripStopTimeId: Long, destinationTripStopTimeId: Long): HttpResponse =
        client.get("trips/$tripId/seats") {
            url {
                parameters.append("origin", originTripStopTimeId.toString())
                parameters.append("destination", destinationTripStopTimeId.toString())
            }
        }

    suspend fun getSeatsParsed(tripId: Long, originTripStopTimeId: Long, destinationTripStopTimeId: Long): List<SeatAvailabilityDto> =
        getSeats(tripId, originTripStopTimeId, destinationTripStopTimeId).body()

    suspend fun registerGuest(tripId: Long, body: GuestOccupantRequestDto): HttpResponse =
        client.post("driver/trips/$tripId/guest-occupants") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
```

- [ ] **Step 3: Dominio + repositorio** — crear
`.../domain/model/SeatAvailability.kt`:

```kotlin
package com.appmovilidadclinica.driver.shared.domain.model

data class SeatAvailability(
    val tripSeatId: Long,
    val seatNumber: Int,
    val seatLabel: String,
    val availability: String,
) {
    val isAvailable: Boolean get() = availability == "AVAILABLE"
}
```

Agregar a la interfaz `DriverRepository`:

```kotlin
    suspend fun getSeats(tripId: Long, originTripStopTimeId: Long, destinationTripStopTimeId: Long): Result<List<SeatAvailability>>
    suspend fun registerGuest(
        tripId: Long,
        tripSeatId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
        firstName: String,
        lastName: String,
    ): Result<Long>
```

Implementar ambos en la clase de `androidMain` que implementa
`DriverRepository`, espejando el manejo de errores de `reportIncident`
existente. Agregar a `Passenger` (dominio) y `PassengerDto` + su mapeo:

```kotlin
    val isGuest: Boolean = false,
    val guestDisplayName: String? = null,
```

(JSON backend: `is_guest: Boolean`, `guest_display_name` ausente si no es
invitado → `String? = null`.)

- [ ] **Step 4: Commit**

```bash
git add driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/remote/dto/SeatDto.kt driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/remote/dto/GuestOccupantDto.kt driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/domain/model/SeatAvailability.kt
git commit -m "feat(driver): capa data de asientos e invitado"
```

(más los archivos modificados de Api/repositorio/modelo/PassengerDto en el
mismo commit.)

---

### Task 5: App driver — pantalla SeatMap + entrada

**Files:**
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/navigation/Route.kt`
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/navigation/DriverNavGraph.kt`
- Create: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/seatmap/SeatMapViewModel.kt`
- Create: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/seatmap/SeatMapScreen.kt`
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/tripdetail/TripDetailScreen.kt`

**Interfaces:**
- Consumes: `DriverRepository.getSeats` + `registerGuest`, `Passenger.isGuest` (Task 4).
- Produces: flujo verificable en la Task 6.

Patrón UI: `IncidentScreen.kt` + `IncidentViewModel.kt` (Scaffold + TopAppBar
con ←, `AlertDialog` de confirmación, `toastMessage`, ViewModel manual con
`remember` + `dispose()` + `koinInject()` del repositorio).

- [ ] **Step 1: Ruta** — en `Route.kt`:

```kotlin
    data class SeatMap(val tripId: Long) : Route()
```

En `DriverNavGraph.kt`, agregar callback al `TripDetailScreen`
(`onOccupySeat: (Long) -> Unit = {}` — nuevo param con default para no
romper otros callers) y el slot:

```kotlin
            is Route.TripDetail -> TripDetailScreen(
                ...
                onOccupySeat = { navState.push(Route.SeatMap(it)) },
            )
            ...
            is Route.SeatMap -> SeatMapScreen(
                tripId = current.tripId,
                onBack = { navState.pop() },
                onRegistered = {
                    // Vuelve al detalle; el detalle recarga pasajeros al
                    // reanudarse (verificar: si TripDetail no recarga solo,
                    // llamar viewModel.load() en LaunchedEffect del regreso
                    // o pasar callback de refresh).
                },
            )
```

- [ ] **Step 2: ViewModel** — crear `SeatMapViewModel.kt` (clase manual como
`TripDetailViewModel`: `CoroutineScope(SupervisorJob() + Dispatchers.Main)`
+ `dispose()`, sin Koin salvo el repositorio por parámetro):

```kotlin
data class SeatMapUiState(
    val stops: List<TripStop> = emptyList(),
    val originStopId: Long? = null,
    val destinationStopId: Long? = null,
    val seats: List<SeatAvailability> = emptyList(),
    val loadingStops: Boolean = true,
    val loadingSeats: Boolean = false,
    val selectedSeatId: Long? = null,
    val firstName: String = "",
    val lastName: String = "",
    val showGuestDialog: Boolean = false,
    val fieldError: String? = null,
    val errorMessage: String? = null,
    val submitting: Boolean = false,
    val registered: Boolean = false,
)
```

Lógica: `load()` trae paradas (`getTripStops`) y preselecciona
origen = primera parada, destino = última; al cambiar tramo, `loadSeats()`
(`getSeats`, solo si origen < destino por `stopOrder`); tap en asiento con
`isAvailable` → abre diálogo; `submit()`: nombres no vacíos (si no,
`fieldError = "Ingrese nombre y apellido."`) → `registerGuest` → éxito:
`registered = true`; error: `errorMessage` con el mensaje del backend sin
limpiar el diálogo.

- [ ] **Step 3: Screen** — crear `SeatMapScreen.kt` (`tripId`,
`driverRepository = koinInject()`, `onBack = {}`, `onRegistered = {}`,
mismo esqueleto que `IncidentScreen`): título "Ocupar asiento"; dos
dropdowns de tramo (origen/destino con nombres de `TripStop`); grilla de
asientos (`LazyVerticalGrid`, celdas 48dp: libre = contorno primario y
clicable, ocupado/bloqueado = gris no clicable, seleccionado = relleno
primario); tap en libre → `AlertDialog` "Pasajero sin app" con dos
`OutlinedTextField` (Nombre, Apellido) + `fieldError` + botones
Cancelar/Confirmar (spinner si `submitting`); `errorMessage` del backend en
rojo bajo la grilla; `LaunchedEffect(registered)` → `onRegistered()`.

- [ ] **Step 4: Entrada + badge invitado en `TripDetailScreen`** — agregar
botón "Ocupar asiento" en la sección de pasajeros (junto a "Escanear QR" o
encabezado de la lista; seguir el estilo de botones existente) que llame
`onOccupySeat(tripId)`; en cada fila de pasajero, si `passenger.isGuest`,
mostrar etiqueta "Invitado" (`AssistChip` o texto secundario — lo más simple
que encaje con la fila existente).

- [ ] **Step 5: Commit**

```bash
git add driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/navigation/Route.kt driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/navigation/DriverNavGraph.kt driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/seatmap/ driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/tripdetail/TripDetailScreen.kt
git commit -m "feat(driver): mapa de asientos y registro de invitado"
```

---

### Task 6: Verificación punta a punta

**Files:** ninguno (solo comandos + QA).

- [ ] **Step 1: Tests backend**

Run: `cd backend && go test ./internal/modules/driver/ -v`
Expected: PASS (incluye `TestRegisterGuestOccupant_*`).

- [ ] **Step 2: Compilar driver app**

Run: `cd driver-android && .\gradlew :shared:assembleDebug -x test --console=plain -q` (ajustar el nombre del módulo según `settings.gradle.kts` si difiere)
Expected: BUILD SUCCESSFUL. (KMP `commonMain` compila para Android acá; iOS queda para su pipeline.)

- [ ] **Step 3: QA manual en emulador** (APK driver, login DRIVER):
  1. Detalle del viaje → botón "Ocupar asiento" → pantalla con tramo + mapa.
  2. Sin tramo válido → mapa no carga / mensaje acorde.
  3. Tap asiento ocupado → no hace nada; tap libre → diálogo nombre/apellido.
  4. Diálogo vacío → error inline; nombre válido → confirma → toast + vuelve + la lista de pasajeros muestra al invitado con badge.
  5. Repetir mismo nombre en el viaje → 409 del backend visible.
  6. Asiento recién ocupado ya no sale libre (refresco).
  7. En `GET /api/admin/incidents`... no — en `GET /api/driver/trips/{id}/passengers` verificar que el invitado aparece con `is_guest: true`.

- [ ] **Step 4: Commit final si hubo fixes** (solo si el QA obligó a cambios).

---

## Self-Review

1. **Spec coverage:** §4 backend → Tasks 1-3 (tabla + SP + repo + servicio + handler + tests + pasajeros con UNION). §5 app → Tasks 4-5 (data + SeatMap + entrada + badge). No-objetivos respetados (sin usuario/DNI, sin editar/liberar, sin reporte admin, sin código compartido con passenger). Rollout → Global Constraints + Task 6 (curl en staging tras deploy; la verificación del SP es en staging porque no hay BD local en CI).
2. **Placeholder scan:** sin TBD/TODO; cada paso trae código o comando con salida esperada. La única lectura diferida ("leer el manejo 45000 de MarkBoarded", "leer mock/helper existentes") apunta a símbolos y líneas verificados en este plan.
3. **Type consistency:** `RegisterGuest(ctx, GuestOccupantParams, reporterUserID) (int64, error)` igual en interfaz, impl, servicio y tests; `RegisterGuestOccupant(ctx, tripID, RegisterGuestRequest) (int64, error)` igual en interfaz, impl, handler y tests; `registerGuest(tripId, tripSeatId, origin, destination, firstName, lastName): Result<Long>` igual en interfaz, impl Android y ViewModel; DTO JSON (`trip_seat_id`, `origin_trip_stop_time_id`, `destination_trip_stop_time_id`, `first_name`, `last_name` → `{"id"}`) coincide con Go (`json:` tags) en ambos sentidos; `SeatAvailabilityDto` coincide con `SeatResult` (`trips/repository.go:38-44`).
