-- ============================================================================
-- verify_guest_auto_alight.sql
-- Verifica 0018_guest_auto_alight (bajada automatica de ocupantes invitados)
-- en una base DESCARTABLE. NO toca ninguna base existente: crea y borra
-- `transporte_verify_guest`.
--
-- Uso (desde el directorio de migraciones, para que resuelvan los `source`):
--   cd /root/appmovilidadclinica/backend/migrations
--   mariadb -u root < ../scripts/verify_guest_auto_alight.sql
--
-- Resultado esperado: todos los SELECT de TEST deben dar OK.
-- ============================================================================

DROP DATABASE IF EXISTS transporte_verify_guest;
CREATE DATABASE transporte_verify_guest CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE transporte_verify_guest;

-- ---------------------------------------------------------------------------
-- Esquema minimo (solo las tablas que tocan los SPs y las migraciones 0013/0017/0018)
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    role ENUM('ADMIN','DRIVER','WORKER') NOT NULL,
    active TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE trip_instances (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT UNSIGNED NOT NULL,
    status ENUM('DRAFT','PUBLISHED','BOARDING','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL,
    actual_end_at DATETIME NULL
) ENGINE=InnoDB;

CREATE TABLE trip_stop_times (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    stop_order SMALLINT UNSIGNED NOT NULL,
    status ENUM('PENDING','ARRIVED','DEPARTED','SKIPPED') NOT NULL DEFAULT 'PENDING',
    actual_arrival_at DATETIME NULL,
    actual_departure_at DATETIME NULL,
    arrival_marked_by_user_id BIGINT UNSIGNED NULL
) ENGINE=InnoDB;

CREATE TABLE trip_seats (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    seat_label VARCHAR(10) NOT NULL,
    is_blocked TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE trip_segments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    segment_order SMALLINT UNSIGNED NOT NULL
) ENGINE=InnoDB;

CREATE TABLE reservations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    worker_id BIGINT UNSIGNED NOT NULL,
    trip_seat_id BIGINT UNSIGNED NOT NULL,
    origin_trip_stop_time_id BIGINT UNSIGNED NOT NULL,
    destination_trip_stop_time_id BIGINT UNSIGNED NOT NULL,
    origin_stop_order SMALLINT UNSIGNED NOT NULL,
    destination_stop_order SMALLINT UNSIGNED NOT NULL,
    original_destination_stop_order SMALLINT UNSIGNED NULL,
    status ENUM('CONFIRMED','BOARDED','COMPLETED','NO_SHOW','CANCELLED') NOT NULL,
    completed_at DATETIME NULL
) ENGINE=InnoDB;

CREATE TABLE trip_seat_segments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_seat_id BIGINT UNSIGNED NOT NULL,
    trip_segment_id BIGINT UNSIGNED NOT NULL,
    state ENUM('AVAILABLE','RESERVED','OCCUPIED','USED','BLOCKED') NOT NULL DEFAULT 'AVAILABLE',
    reservation_id BIGINT UNSIGNED NULL,
    reserved_at DATETIME NULL,
    released_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_verify_guest_seat_segment UNIQUE (trip_seat_id, trip_segment_id)
) ENGINE=InnoDB;

CREATE TABLE reservation_segments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT UNSIGNED NOT NULL,
    trip_segment_id BIGINT UNSIGNED NOT NULL,
    allocation_status ENUM('RESERVED','OCCUPIED','USED','RELEASED') NOT NULL DEFAULT 'RESERVED',
    released_at DATETIME NULL,
    CONSTRAINT uq_verify_guest_res_segment UNIQUE (reservation_id, trip_segment_id)
) ENGINE=InnoDB;

CREATE TABLE reservation_events (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT UNSIGNED NOT NULL,
    event_type ENUM('CONFIRMED','BOARDED','ALIGHTED','NO_SHOW',
                    'SEGMENTS_RELEASED','CANCELLED','EXTENDED') NOT NULL,
    trip_stop_time_id BIGINT UNSIGNED NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    event_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details VARCHAR(500) NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- Migraciones reales: 0013 (tabla + sp_register_guest_occupant),
-- 0017 (bajada automatica de reservas), 0018 (bajada de invitados, lo nuevo)
-- ---------------------------------------------------------------------------

source 0013_guest_occupants.up.sql
source 0017_auto_alight.up.sql
source 0018_guest_auto_alight.up.sql

-- ---------------------------------------------------------------------------
-- Fixtures: viaje en curso, 3 paradas, 3 asientos, 2 segmentos.
--   invitado 1: asiento 201, tramo [101 -> 102)  -> segmento 301
--   invitado 2: asiento 202, tramo [102 -> 103)  -> segmento 302
--   reserva  1: asiento 203, tramo [101 -> 102)  -> segmento 301 (regresion 0017)
-- ---------------------------------------------------------------------------

INSERT INTO users (id, role, active) VALUES (1, 'DRIVER', 1), (2, 'WORKER', 1);

INSERT INTO trip_instances (id, driver_id, status) VALUES (10, 1, 'IN_PROGRESS');

INSERT INTO trip_stop_times (id, trip_id, stop_order, status) VALUES
    (101, 10, 1, 'DEPARTED'),
    (102, 10, 2, 'PENDING'),
    (103, 10, 3, 'PENDING');
UPDATE trip_stop_times SET actual_departure_at = NOW() WHERE id = 101;

INSERT INTO trip_seats (id, trip_id, seat_label) VALUES
    (201, 10, '1'), (202, 10, '2'), (203, 10, '3');

INSERT INTO trip_segments (id, trip_id, segment_order) VALUES (301, 10, 1), (302, 10, 2);

-- Inventario: 2 segmentos por asiento.
INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state) VALUES
    (201, 301, 'AVAILABLE'), (201, 302, 'AVAILABLE'),
    (202, 301, 'AVAILABLE'), (202, 302, 'AVAILABLE'),
    (203, 301, 'AVAILABLE'), (203, 302, 'AVAILABLE');

CALL sp_register_guest_occupant(10, 201, 101, 102, 'Juan', 'Perez', 1);
CALL sp_register_guest_occupant(10, 202, 102, 103, 'Ana', 'Gomez', 1);

-- Reserva BOARDED en su propio asiento (no debe romperse con la 0018):
-- el asiento 203 es el unico que queda libre, se ocupa el inventario ya creado.
UPDATE trip_seat_segments
   SET state = 'OCCUPIED', reservation_id = 1, reserved_at = NOW()
 WHERE trip_seat_id = 203;

INSERT INTO reservations (id, trip_id, worker_id, trip_seat_id,
                          origin_trip_stop_time_id, destination_trip_stop_time_id,
                          origin_stop_order, destination_stop_order, status)
VALUES (1, 10, 2, 203, 101, 102, 1, 2, 'BOARDED');

INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status)
VALUES (1, 301, 'OCCUPIED'), (1, 302, 'OCCUPIED');

-- ---------------------------------------------------------------------------
-- TEST 0: el ENUM quedo ampliado y existe completed_at
-- ---------------------------------------------------------------------------
SELECT 'TEST 0 - esquema' AS test,
       CASE WHEN (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'guest_occupants'
                     AND COLUMN_NAME = 'status') = 'enum(''BOARDED'',''COMPLETED'')'
             AND (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'guest_occupants'
                     AND COLUMN_NAME = 'completed_at') = 1
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'guest_occupants'
           AND COLUMN_NAME = 'status') AS status_type;

-- ---------------------------------------------------------------------------
-- TEST 1: estado inicial de los invitados (nacen BOARDED y ocupan su tramo)
-- ---------------------------------------------------------------------------
SELECT 'TEST 1 - invitados registrados' AS test,
       CASE WHEN (SELECT COUNT(*) FROM guest_occupants
                   WHERE trip_id = 10 AND status = 'BOARDED' AND completed_at IS NULL) = 2
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=301) = 'OCCUPIED'
             AND (SELECT guest_occupant_id IS NOT NULL FROM trip_seat_segments
                   WHERE trip_seat_id=201 AND trip_segment_id=301) = 1
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=202 AND trip_segment_id=302) = 'OCCUPIED'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       (SELECT COUNT(*) FROM guest_occupants WHERE status = 'BOARDED') AS invitados_boarded,
       (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=301) AS seg201_301;

-- ---------------------------------------------------------------------------
-- TEST 2: llegada a 101, que no es destino de nadie (invitado 1 baja en 102,
--         invitado 2 en 103, reserva 1 en 102) -> no debe cerrar nada
-- ---------------------------------------------------------------------------
CALL sp_mark_trip_stop_arrival(101, 1);

SELECT 'TEST 2 - no cierra de mas' AS test,
       CASE WHEN (SELECT status FROM guest_occupants WHERE id = 1) = 'BOARDED'
             AND (SELECT status FROM guest_occupants WHERE id = 2) = 'BOARDED'
             AND (SELECT status FROM reservations WHERE id = 1) = 'BOARDED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=301) = 'OCCUPIED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=202 AND trip_segment_id=302) = 'OCCUPIED'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       (SELECT status FROM guest_occupants WHERE id = 1) AS invitado1,
       (SELECT status FROM guest_occupants WHERE id = 2) AS invitado2;

-- ---------------------------------------------------------------------------
-- TEST 3: llegada a 102 -> baja al invitado 1 y a la reserva 1 (0017 intacta),
--         y NO toca al invitado 2 (su destino es 103)
-- ---------------------------------------------------------------------------
CALL sp_mark_trip_stop_arrival(102, 1);

SELECT 'TEST 3 - bajada en el destino' AS test,
       CASE WHEN (SELECT status FROM guest_occupants WHERE id = 1) = 'COMPLETED'
             AND (SELECT completed_at IS NOT NULL FROM guest_occupants WHERE id = 1) = 1
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=301) = 'USED'
             AND (SELECT status FROM guest_occupants WHERE id = 2) = 'BOARDED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=202 AND trip_segment_id=302) = 'OCCUPIED'
             AND (SELECT status FROM reservations WHERE id = 1) = 'COMPLETED'
             AND (SELECT completed_at IS NOT NULL FROM reservations WHERE id = 1) = 1
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=203 AND trip_segment_id=301) = 'USED'
             AND (SELECT allocation_status FROM reservation_segments WHERE reservation_id=1 AND trip_segment_id=301) = 'USED'
             AND (SELECT COUNT(*) FROM reservation_events WHERE reservation_id=1 AND event_type='ALIGHTED') = 1
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       (SELECT status FROM guest_occupants WHERE id = 1) AS invitado1,
       (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=301) AS seg_invitado1,
       (SELECT status FROM reservations WHERE id = 1) AS reserva1;

-- ---------------------------------------------------------------------------
-- TEST 4: al finalizar el viaje se cierra el invitado que quedo arriba
-- ---------------------------------------------------------------------------
CALL sp_complete_trip(10, 1);

SELECT 'TEST 4 - cierre al finalizar el viaje' AS test,
       CASE WHEN (SELECT status FROM trip_instances WHERE id = 10) = 'COMPLETED'
             AND (SELECT status FROM guest_occupants WHERE id = 2) = 'COMPLETED'
             AND (SELECT completed_at IS NOT NULL FROM guest_occupants WHERE id = 2) = 1
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=202 AND trip_segment_id=302) = 'USED'
             AND (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 10 AND status = 'BOARDED') = 0
             AND (SELECT COUNT(*) FROM trip_seat_segments
                   WHERE guest_occupant_id IS NOT NULL AND state = 'OCCUPIED') = 0
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       (SELECT status FROM guest_occupants WHERE id = 2) AS invitado2,
       (SELECT state FROM trip_seat_segments WHERE trip_seat_id=202 AND trip_segment_id=302) AS seg_invitado2,
       (SELECT COUNT(*) FROM trip_seat_segments
         WHERE guest_occupant_id IS NOT NULL AND state = 'OCCUPIED') AS asientos_invitado_fantasma;

-- ---------------------------------------------------------------------------
-- TEST 5: los invitados bajados desaparecen del manifiesto del conductor
--         (mismo filtro que usa internal/modules/driver/repository.go)
-- ---------------------------------------------------------------------------
SELECT 'TEST 5 - manifiesto' AS test,
       CASE WHEN (SELECT COUNT(*) FROM guest_occupants
                   WHERE trip_id = 10 AND status = 'BOARDED') = 0
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 10) AS invitados_totales,
       (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 10 AND status = 'BOARDED') AS visibles_en_manifiesto;

-- ---------------------------------------------------------------------------
-- TEST 6: rollback. El .down restaura el ENUM original y los SPs de la 0017
--         (no revienta con invitados ya en COMPLETED: los reabre a BOARDED)
-- ---------------------------------------------------------------------------
source 0018_guest_auto_alight.down.sql

SELECT 'TEST 6 - rollback (down)' AS test,
       CASE WHEN (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'guest_occupants'
                     AND COLUMN_NAME = 'status') = 'enum(''BOARDED'')'
             AND (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'guest_occupants'
                     AND COLUMN_NAME = 'completed_at') = 0
             AND (SELECT COUNT(*) FROM guest_occupants WHERE status = 'BOARDED') = 2
             AND (SELECT COUNT(*) FROM information_schema.ROUTINES
                   WHERE ROUTINE_SCHEMA = DATABASE()
                     AND ROUTINE_NAME IN ('sp_mark_trip_stop_arrival', 'sp_complete_trip')) = 2
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'guest_occupants'
           AND COLUMN_NAME = 'status') AS status_type,
       (SELECT COUNT(*) FROM guest_occupants WHERE status = 'BOARDED') AS invitados_boarded;

-- ---------------------------------------------------------------------------
-- Limpieza
-- ---------------------------------------------------------------------------
DROP DATABASE transporte_verify_guest;
