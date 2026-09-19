-- ============================================================================
-- verify_reservation_manifest_guard.sql
-- Verifica 0020_reservation_manifest_guard (validacion del estado del viaje en
-- las cuatro SPs que cambian el manifiesto) en una base DESCARTABLE. NO toca
-- ninguna base existente: crea y borra `transporte_verify_manifest`.
--
-- Uso (desde el directorio de migraciones, para que resuelvan los `source`):
--   cd /root/appmovilidadclinica/backend/migrations
--   mariadb -u root < ../scripts/verify_reservation_manifest_guard.sql
--
-- Resultado esperado: todos los SELECT de TEST deben dar OK.
--
-- Nota: los casos negativos usan un helper (verify_call) porque un SIGNAL
-- abortaria el script del cliente; el helper atrapa el SQLSTATE y devuelve el
-- mensaje del SP en un OUT.
-- ============================================================================

DROP DATABASE IF EXISTS transporte_verify_manifest;
CREATE DATABASE transporte_verify_manifest CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE transporte_verify_manifest;

-- ---------------------------------------------------------------------------
-- Esquema minimo (solo lo que tocan las cuatro SPs)
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
    no_show_tolerance_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 5,
    actual_end_at DATETIME NULL
) ENGINE=InnoDB;

CREATE TABLE trip_stop_times (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    stop_order SMALLINT UNSIGNED NOT NULL,
    scheduled_departure_at DATETIME NULL,
    actual_arrival_at DATETIME NULL,
    actual_departure_at DATETIME NULL
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
    status ENUM('CONFIRMED','BOARDED','COMPLETED','NO_SHOW','CANCELLED') NOT NULL,
    boarded_at DATETIME NULL,
    completed_at DATETIME NULL,
    -- columnas que escribe sp_mark_reservation_no_show
    no_show_at DATETIME NULL,
    no_show_by_user_id BIGINT UNSIGNED NULL,
    no_show_trip_stop_time_id BIGINT UNSIGNED NULL
) ENGINE=InnoDB;

CREATE TABLE trip_seat_segments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_seat_id BIGINT UNSIGNED NOT NULL,
    trip_segment_id BIGINT UNSIGNED NOT NULL,
    state ENUM('AVAILABLE','RESERVED','OCCUPIED','USED','BLOCKED') NOT NULL DEFAULT 'AVAILABLE',
    reservation_id BIGINT UNSIGNED NULL,
    reserved_at DATETIME NULL,
    released_at DATETIME NULL
) ENGINE=InnoDB;

CREATE TABLE reservation_segments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT UNSIGNED NOT NULL,
    trip_segment_id BIGINT UNSIGNED NOT NULL,
    allocation_status ENUM('RESERVED','OCCUPIED','USED','RELEASED') NOT NULL DEFAULT 'RESERVED',
    released_at DATETIME NULL
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
-- Migracion real bajo prueba
-- ---------------------------------------------------------------------------

source 0020_reservation_manifest_guard.up.sql

-- ---------------------------------------------------------------------------
-- Fixtures
--   viaje 10 IN_PROGRESS, 11 COMPLETED, 12 DRAFT, 13 CANCELLED, 14 PUBLISHED
--   (la 0001 genera los viajes en DRAFT y los publica si el template lo pide,
--    por eso DRAFT tambien se prueba)
--   reservas 1x = viaje 10, 2x = viaje 11, 30 = viaje 12, 40 = viaje 13,
--   50 = viaje 14 y 99 = viaje inexistente
-- ---------------------------------------------------------------------------

INSERT INTO users (id, role, active) VALUES (1, 'DRIVER', 1), (2, 'WORKER', 1);

INSERT INTO trip_instances (id, driver_id, status, no_show_tolerance_minutes, actual_end_at) VALUES
    (10, 1, 'IN_PROGRESS', 0, NULL),
    (11, 1, 'COMPLETED',   0, NOW()),
    (12, 1, 'DRAFT',       0, NULL),
    (13, 1, 'CANCELLED',   0, NULL),
    (14, 1, 'PUBLISHED',   0, NULL);

INSERT INTO trip_stop_times (id, trip_id, stop_order, scheduled_departure_at, actual_arrival_at) VALUES
    (101, 10, 1, NOW(), NOW()), (102, 10, 2, NOW(), NOW()),
    (111, 11, 1, NOW(), NOW()), (112, 11, 2, NOW(), NOW()),
    (121, 12, 1, NOW(), NOW()), (122, 12, 2, NOW(), NOW()),
    (131, 13, 1, NOW(), NOW()), (132, 13, 2, NOW(), NOW()),
    (141, 14, 1, NOW(), NOW()), (142, 14, 2, NOW(), NOW()),
    (191, 999, 1, NOW(), NOW());

-- Helper: (id, trip, origen, destino, estado)
INSERT INTO reservations (id, trip_id, worker_id, trip_seat_id,
                          origin_trip_stop_time_id, destination_trip_stop_time_id,
                          origin_stop_order, destination_stop_order, status) VALUES
    (10, 10, 2, 201, 101, 102, 1, 2, 'CONFIRMED'),
    (11, 10, 2, 202, 101, 102, 1, 2, 'CONFIRMED'),
    (12, 10, 2, 203, 101, 102, 1, 2, 'CONFIRMED'),
    (13, 10, 2, 204, 101, 102, 1, 2, 'BOARDED'),
    (20, 11, 2, 211, 111, 112, 1, 2, 'CONFIRMED'),
    (21, 11, 2, 212, 111, 112, 1, 2, 'CONFIRMED'),
    (22, 11, 2, 213, 111, 112, 1, 2, 'CONFIRMED'),
    (23, 11, 2, 214, 111, 112, 1, 2, 'BOARDED'),
    (30, 12, 2, 221, 121, 122, 1, 2, 'CONFIRMED'),
    (40, 13, 2, 231, 131, 132, 1, 2, 'CONFIRMED'),
    (50, 14, 2, 241, 141, 142, 1, 2, 'CONFIRMED'),
    (99, 999, 2, 291, 191, 191, 1, 2, 'CONFIRMED');

-- Inventario reservado por cada reserva (para verificar que no se toca).
INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state, reservation_id, reserved_at)
SELECT 200 + id, 300 + id, 'RESERVED', id, NOW() FROM reservations;

INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status)
SELECT id, 300 + id, 'RESERVED' FROM reservations;

-- Las reservas BOARDED ya tienen el asiento ocupado (no RESERVED): es el
-- estado que deja sp_mark_reservation_boarded y el que exige el ALIGHTED.
UPDATE trip_seat_segments
   SET state = 'OCCUPIED'
 WHERE reservation_id IN (SELECT id FROM reservations WHERE status = 'BOARDED');
UPDATE reservation_segments
   SET allocation_status = 'OCCUPIED'
 WHERE reservation_id IN (SELECT id FROM reservations WHERE status = 'BOARDED');

-- ---------------------------------------------------------------------------
-- Helper: llama la SP indicada y devuelve 'SIN ERROR' o el mensaje del SIGNAL
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS verify_call;

DELIMITER $$

CREATE PROCEDURE verify_call(
    IN p_proc VARCHAR(20),
    IN p_reservation_id BIGINT UNSIGNED,
    IN p_actor_id BIGINT UNSIGNED,
    OUT p_error VARCHAR(255)
)
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 p_error = MESSAGE_TEXT;
    END;

    SET p_error = 'SIN ERROR';

    IF p_proc = 'boarded' THEN
        CALL sp_mark_reservation_boarded(p_reservation_id, p_actor_id);
    ELSEIF p_proc = 'self' THEN
        CALL sp_mark_reservation_boarded_self(p_reservation_id, p_actor_id);
    ELSEIF p_proc = 'no_show' THEN
        CALL sp_mark_reservation_no_show(p_reservation_id, p_actor_id);
    ELSEIF p_proc = 'alighted' THEN
        CALL sp_mark_reservation_alighted(p_reservation_id, p_actor_id);
    ELSE
        SET p_error = CONCAT('proc desconocida: ', p_proc);
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------------
-- TEST 0: las cuatro SPs desplegadas ya contienen el guard
-- ---------------------------------------------------------------------------
SELECT 'TEST 0 - SPs con guard' AS test,
       CASE WHEN (SELECT COUNT(*) FROM information_schema.ROUTINES
                   WHERE ROUTINE_SCHEMA = DATABASE()
                     AND ROUTINE_NAME IN ('sp_mark_reservation_boarded',
                                          'sp_mark_reservation_boarded_self',
                                          'sp_mark_reservation_no_show',
                                          'sp_mark_reservation_alighted')
                     AND ROUTINE_DEFINITION LIKE '%v_trip_status%') = 4
            THEN 'OK' ELSE 'FALLO' END AS resultado;

-- ---------------------------------------------------------------------------
-- TEST 1: el bug reportado. Viaje COMPLETED:
--         ninguna de las cuatro SPs debe aceptar cambios
-- ---------------------------------------------------------------------------
CALL verify_call('boarded',  20, 1, @t1_boarded);
CALL verify_call('self',     21, 2, @t1_self);
CALL verify_call('no_show',  22, 1, @t1_no_show);
CALL verify_call('alighted', 23, 1, @t1_alighted);

SELECT 'TEST 1 - viaje COMPLETED rechaza todo' AS test,
       CASE WHEN @t1_boarded  = 'El viaje ya esta cerrado y no admite cambios en el manifiesto'
             AND @t1_self     = 'El viaje ya esta cerrado y no admite cambios en el manifiesto'
             AND @t1_no_show  = 'El viaje ya esta cerrado y no admite cambios en el manifiesto'
             AND @t1_alighted = 'El viaje ya esta cerrado y no admite cambios en el manifiesto'
             AND (SELECT COUNT(*) FROM reservations
                   WHERE id IN (20, 21, 22) AND status = 'CONFIRMED') = 3
             AND (SELECT status FROM reservations WHERE id = 23) = 'BOARDED'
             AND (SELECT COUNT(*) FROM reservation_events WHERE reservation_id IN (20, 21, 22, 23)) = 0
             AND (SELECT COUNT(*) FROM trip_seat_segments
                   WHERE trip_seat_id IN (220, 221, 222) AND state = 'RESERVED') = 3
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 223) = 'OCCUPIED'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t1_boarded AS boarded, @t1_self AS self_checkin,
       @t1_no_show AS no_show, @t1_alighted AS alighted;

-- ---------------------------------------------------------------------------
-- TEST 2: viaje DRAFT y viaje CANCELLED -> rechazan
-- ---------------------------------------------------------------------------
CALL verify_call('boarded', 30, 1, @t2_draft);
CALL verify_call('boarded', 40, 1, @t2_cancelled);

SELECT 'TEST 2 - DRAFT y CANCELLED rechazan' AS test,
       CASE WHEN @t2_draft = 'El viaje no esta publicado'
             AND @t2_cancelled = 'El viaje ya esta cerrado y no admite cambios en el manifiesto'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t2_draft AS draft, @t2_cancelled AS cancelled;

-- ---------------------------------------------------------------------------
-- TEST 3: viaje inexistente -> mensaje propio en vez del generico
--         ("Primero debe registrarse la llegada fisica...")
-- ---------------------------------------------------------------------------
CALL verify_call('boarded', 99, 1, @t3_missing);

SELECT 'TEST 3 - viaje inexistente' AS test,
       CASE WHEN @t3_missing = 'El viaje no existe' THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t3_missing AS error_devuelto;

-- ---------------------------------------------------------------------------
-- TEST 4: regresion - viaje IN_PROGRESS sigue permitiendo operar
-- ---------------------------------------------------------------------------
CALL verify_call('boarded',  10, 1, @t4_boarded);
CALL verify_call('no_show',  12, 1, @t4_no_show);
CALL verify_call('alighted', 13, 1, @t4_alighted);

SELECT 'TEST 4 - IN_PROGRESS sigue operativo' AS test,
       CASE WHEN @t4_boarded  = 'SIN ERROR'
             AND (SELECT status FROM reservations WHERE id = 10) = 'BOARDED'
             AND (SELECT boarded_at IS NOT NULL FROM reservations WHERE id = 10) = 1
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 210) = 'OCCUPIED'
             AND (SELECT allocation_status FROM reservation_segments WHERE reservation_id = 10) = 'OCCUPIED'
             AND (SELECT COUNT(*) FROM reservation_events WHERE reservation_id = 10 AND event_type = 'BOARDED') = 1
             AND @t4_no_show = 'SIN ERROR'
             AND (SELECT status FROM reservations WHERE id = 12) = 'NO_SHOW'
             AND (SELECT no_show_at IS NOT NULL FROM reservations WHERE id = 12) = 1
             -- el NO_SHOW libera el asiento: la fila pierde reservation_id
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 212) = 'AVAILABLE'
             AND (SELECT reservation_id IS NULL FROM trip_seat_segments WHERE trip_seat_id = 212) = 1
             AND @t4_alighted = 'SIN ERROR'
             AND (SELECT status FROM reservations WHERE id = 13) = 'COMPLETED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 213) = 'USED'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t4_boarded AS boarded, @t4_no_show AS no_show, @t4_alighted AS alighted;

-- ---------------------------------------------------------------------------
-- TEST 5: regresion - el self check-in sigue funcionando en un viaje
--         PUBLISHED dentro de la ventana de +/-30 min
-- ---------------------------------------------------------------------------
CALL verify_call('self', 50, 2, @t5_self);

SELECT 'TEST 5 - self check-in en PUBLISHED' AS test,
       CASE WHEN @t5_self = 'SIN ERROR'
             AND (SELECT status FROM reservations WHERE id = 50) = 'BOARDED'
             AND (SELECT COUNT(*) FROM reservation_events
                   WHERE reservation_id = 50 AND event_type = 'BOARDED') = 1
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t5_self AS self_checkin;

-- ---------------------------------------------------------------------------
-- TEST 6: el self check-in sigue validando la ventana de tiempo
-- ---------------------------------------------------------------------------
UPDATE trip_stop_times SET scheduled_departure_at = DATE_SUB(NOW(), INTERVAL 3 HOUR) WHERE id = 141;
UPDATE reservations SET status = 'CONFIRMED', boarded_at = NULL WHERE id = 50;

CALL verify_call('self', 50, 2, @t6_window);

SELECT 'TEST 6 - ventana de self check-in intacta' AS test,
       CASE WHEN @t6_window = 'Fuera de la ventana de self-checkin (+/-30 min del horario de salida)'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t6_window AS error_devuelto;

-- ---------------------------------------------------------------------------
-- TEST 7: rollback (.down) -> el bug vuelve a reproducirse. Demuestra que el
--         guard de la 0020 es lo unico que impedia abordar en un viaje cerrado
--         (antes de la 0020, esta llamada devolvia 'SIN ERROR').
-- ---------------------------------------------------------------------------
source 0020_reservation_manifest_guard.down.sql

UPDATE reservations SET status = 'CONFIRMED', boarded_at = NULL WHERE id = 20;
CALL verify_call('boarded', 20, 1, @t7_after_down);

SELECT 'TEST 7 - rollback reproduce el bug' AS test,
       CASE WHEN @t7_after_down = 'SIN ERROR'
             AND (SELECT status FROM reservations WHERE id = 20) = 'BOARDED'
             AND (SELECT boarded_at IS NOT NULL FROM reservations WHERE id = 20) = 1
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t7_after_down AS error_devuelto,
       (SELECT status FROM reservations WHERE id = 20) AS reserva_20;

-- ---------------------------------------------------------------------------
-- Limpieza
-- ---------------------------------------------------------------------------
DROP DATABASE transporte_verify_manifest;
