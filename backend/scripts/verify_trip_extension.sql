-- ============================================================================
-- verify_trip_extension.sql
-- Verifica los SPs de extension de viaje del pasajero en una base DESCARTABLE.
-- NO toca ninguna base existente: crea y borra `transporte_verify_ext`.
--
-- Uso (desde el directorio de migraciones, para que resuelvan los `source`):
--   cd /opt/appmovilidadclinica/backend/migrations
--   mariadb -u root < /ruta/a/verify_trip_extension.sql
--
-- Resultado esperado: todos los SELECT de TEST deben dar OK.
-- ============================================================================

DROP DATABASE IF EXISTS transporte_verify_ext;
CREATE DATABASE transporte_verify_ext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE transporte_verify_ext;

-- ---------------------------------------------------------------------------
-- Esquema minimo (solo las tablas que tocan los SPs)
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
    CONSTRAINT uq_verify_seat_segment UNIQUE (trip_seat_id, trip_segment_id)
) ENGINE=InnoDB;

CREATE TABLE reservation_segments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT UNSIGNED NOT NULL,
    trip_segment_id BIGINT UNSIGNED NOT NULL,
    allocation_status ENUM('RESERVED','OCCUPIED','USED','RELEASED') NOT NULL DEFAULT 'RESERVED',
    released_at DATETIME NULL,
    CONSTRAINT uq_verify_res_segment UNIQUE (reservation_id, trip_segment_id)
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
-- SPs nuevos (se toman de los archivos reales del deploy)
-- ---------------------------------------------------------------------------

source 0016_sp_extend_reservation.up.sql
source 0017_auto_alight.up.sql

-- ---------------------------------------------------------------------------
-- Fixtures: viaje en curso, 3 paradas, 1 asiento, reserva 1 BOARDED [1 -> 2)
-- ---------------------------------------------------------------------------

INSERT INTO users (id, role, active) VALUES (1, 'DRIVER', 1), (2, 'WORKER', 1);

INSERT INTO trip_instances (id, driver_id, status) VALUES (10, 1, 'IN_PROGRESS');

INSERT INTO trip_stop_times (id, trip_id, stop_order, status) VALUES
    (101, 10, 1, 'DEPARTED'),
    (102, 10, 2, 'PENDING'),
    (103, 10, 3, 'PENDING');
UPDATE trip_stop_times SET actual_departure_at = NOW() WHERE id = 101;

INSERT INTO trip_seats (id, trip_id, seat_label) VALUES (201, 10, '1');

INSERT INTO trip_segments (id, trip_id, segment_order) VALUES (301, 10, 1), (302, 10, 2);

INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state, reservation_id, reserved_at)
VALUES (201, 301, 'OCCUPIED', 1, NOW()), (201, 302, 'AVAILABLE', NULL, NULL);

INSERT INTO reservations (id, trip_id, worker_id, trip_seat_id,
                          origin_trip_stop_time_id, destination_trip_stop_time_id,
                          origin_stop_order, destination_stop_order, status)
VALUES (1, 10, 2, 201, 101, 102, 1, 2, 'BOARDED');

INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status)
VALUES (1, 301, 'OCCUPIED');

-- ---------------------------------------------------------------------------
-- TEST 1: extender la reserva del orden 2 al 3 (mismo asiento)
-- ---------------------------------------------------------------------------
CALL sp_extend_reservation(1, 2, 103, 0);

SELECT 'TEST 1 - extender' AS test,
       CASE WHEN r.destination_stop_order = 3
             AND r.destination_trip_stop_time_id = 103
             AND r.original_destination_stop_order = 2
             AND r.status = 'BOARDED'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       r.destination_stop_order AS dest_orden,
       r.original_destination_stop_order AS dest_original,
       (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=302) AS seg2_state,
       (SELECT COUNT(*) FROM reservation_events WHERE reservation_id=1 AND event_type='EXTENDED') AS eventos_extended
  FROM reservations r WHERE r.id = 1;

-- ---------------------------------------------------------------------------
-- TEST 2: marcar llegada al destino VIEJO (102) NO debe bajar al pasajero
-- ---------------------------------------------------------------------------
CALL sp_mark_trip_stop_arrival(102, 1);

SELECT 'TEST 2 - no baja en destino viejo' AS test,
       CASE WHEN r.status = 'BOARDED' THEN 'OK' ELSE 'FALLO' END AS resultado,
       r.status AS res_status,
       (SELECT status FROM trip_stop_times WHERE id=102) AS parada_102
  FROM reservations r WHERE r.id = 1;

-- ---------------------------------------------------------------------------
-- TEST 3: marcar llegada al destino NUEVO (103) SI debe bajarlo y liberar
-- ---------------------------------------------------------------------------
CALL sp_mark_trip_stop_arrival(103, 1);

SELECT 'TEST 3 - baja en destino nuevo' AS test,
       CASE WHEN r.status = 'COMPLETED'
             AND r.completed_at IS NOT NULL
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=301) = 'USED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=302) = 'USED'
             AND (SELECT allocation_status FROM reservation_segments WHERE reservation_id=1 AND trip_segment_id=302) = 'USED'
             AND (SELECT COUNT(*) FROM reservation_events WHERE reservation_id=1 AND event_type='ALIGHTED') = 1
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       r.status AS res_status,
       (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=301) AS seg1,
       (SELECT state FROM trip_seat_segments WHERE trip_seat_id=201 AND trip_segment_id=302) AS seg2
  FROM reservations r WHERE r.id = 1;

-- ---------------------------------------------------------------------------
-- TEST 4: al completar el viaje, las reservas BOARDED que quedaron se cierran
-- ---------------------------------------------------------------------------
-- El asiento 201/segmento 302 ya existe (TEST 3 lo dejo USED): se reusa.
UPDATE trip_seat_segments
   SET state = 'OCCUPIED', reservation_id = 2, reserved_at = NOW()
 WHERE trip_seat_id = 201 AND trip_segment_id = 302;

INSERT INTO reservations (id, trip_id, worker_id, trip_seat_id,
                          origin_trip_stop_time_id, destination_trip_stop_time_id,
                          origin_stop_order, destination_stop_order, status)
VALUES (2, 10, 2, 201, 102, 103, 2, 3, 'BOARDED');

INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status)
VALUES (2, 302, 'OCCUPIED');

CALL sp_complete_trip(10, 1);

SELECT 'TEST 4 - cierre al completar viaje' AS test,
       CASE WHEN (SELECT status FROM trip_instances WHERE id=10) = 'COMPLETED'
             AND (SELECT actual_end_at FROM trip_instances WHERE id=10) IS NOT NULL
             AND (SELECT status FROM reservations WHERE id=2) = 'COMPLETED'
             AND (SELECT state FROM trip_seat_segments WHERE reservation_id=2 AND trip_segment_id=302) = 'USED'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       (SELECT status FROM trip_instances WHERE id=10) AS viaje,
       (SELECT status FROM reservations WHERE id=2) AS res2_status;

-- ---------------------------------------------------------------------------
-- Limpieza
-- ---------------------------------------------------------------------------
DROP DATABASE transporte_verify_ext;
