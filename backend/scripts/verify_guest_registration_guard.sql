-- ============================================================================
-- verify_guest_registration_guard.sql
-- Verifica 0019_guest_registration_guard (validacion del estado del viaje al
-- registrar un ocupante invitado) en una base DESCARTABLE. NO toca ninguna
-- base existente: crea y borra `transporte_verify_guard`.
--
-- Uso (desde el directorio de migraciones, para que resuelvan los `source`):
--   cd /root/appmovilidadclinica/backend/migrations
--   mariadb -u root < ../scripts/verify_guest_registration_guard.sql
--
-- Resultado esperado: todos los SELECT de TEST deben dar OK.
--
-- Nota: los casos negativos usan un helper (verify_call) porque un SIGNAL
-- abortaria el script del cliente; el helper atrapa el SQLSTATE y devuelve el
-- mensaje del SP en un OUT.
-- ============================================================================

DROP DATABASE IF EXISTS transporte_verify_guard;
CREATE DATABASE transporte_verify_guard CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE transporte_verify_guard;

-- ---------------------------------------------------------------------------
-- Esquema minimo (solo lo que toca sp_register_guest_occupant)
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

CREATE TABLE trip_seat_segments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_seat_id BIGINT UNSIGNED NOT NULL,
    trip_segment_id BIGINT UNSIGNED NOT NULL,
    state ENUM('AVAILABLE','RESERVED','OCCUPIED','USED','BLOCKED') NOT NULL DEFAULT 'AVAILABLE',
    reservation_id BIGINT UNSIGNED NULL,
    reserved_at DATETIME NULL,
    released_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_verify_guard_seat_segment UNIQUE (trip_seat_id, trip_segment_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- Migraciones reales: 0013 (tabla + SP) y 0019 (el guard, lo nuevo)
-- ---------------------------------------------------------------------------

source 0013_guest_occupants.up.sql
source 0019_guest_registration_guard.up.sql

-- ---------------------------------------------------------------------------
-- Fixtures: un viaje por cada estado, cada uno con paradas 1-2-3, asiento y
-- dos segmentos. Convencion de ids: viaje T -> paradas T*10+1..3, asiento
-- T*10+1, segmentos T*10+1..2.
-- ---------------------------------------------------------------------------

INSERT INTO users (id, role, active) VALUES (1, 'DRIVER', 1), (2, 'WORKER', 1);

INSERT INTO trip_instances (id, driver_id, status) VALUES
    (10, 1, 'PUBLISHED'),
    (11, 1, 'BOARDING'),
    (12, 1, 'IN_PROGRESS'),
    (13, 1, 'DRAFT'),
    (14, 1, 'COMPLETED'),
    (15, 1, 'CANCELLED');

INSERT INTO trip_stop_times (id, trip_id, stop_order) VALUES
    (101, 10, 1), (102, 10, 2), (103, 10, 3),
    (111, 11, 1), (112, 11, 2), (113, 11, 3),
    (121, 12, 1), (122, 12, 2), (123, 12, 3),
    (131, 13, 1), (132, 13, 2), (133, 13, 3),
    (141, 14, 1), (142, 14, 2), (143, 14, 3),
    (151, 15, 1), (152, 15, 2), (153, 15, 3);

INSERT INTO trip_seats (id, trip_id, seat_label) VALUES
    (201, 10, '1'), (211, 11, '1'), (221, 12, '1'),
    (231, 13, '1'), (241, 14, '1'), (251, 15, '1');

INSERT INTO trip_segments (id, trip_id, segment_order) VALUES
    (301, 10, 1), (302, 10, 2),
    (311, 11, 1), (312, 11, 2),
    (321, 12, 1), (322, 12, 2),
    (331, 13, 1), (332, 13, 2),
    (341, 14, 1), (342, 14, 2),
    (351, 15, 1), (352, 15, 2);

INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state) VALUES
    (201, 301, 'AVAILABLE'), (201, 302, 'AVAILABLE'),
    (211, 311, 'AVAILABLE'), (211, 312, 'AVAILABLE'),
    (221, 321, 'AVAILABLE'), (221, 322, 'AVAILABLE'),
    (231, 331, 'AVAILABLE'), (231, 332, 'AVAILABLE'),
    (241, 341, 'AVAILABLE'), (241, 342, 'AVAILABLE'),
    (251, 351, 'AVAILABLE'), (251, 352, 'AVAILABLE');

-- ---------------------------------------------------------------------------
-- Helper: llama al SP y devuelve 'SIN ERROR' o el mensaje del SIGNAL
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS verify_call;

DELIMITER $$

CREATE PROCEDURE verify_call(
    IN p_trip_id BIGINT UNSIGNED,
    IN p_trip_seat_id BIGINT UNSIGNED,
    IN p_origin BIGINT UNSIGNED,
    IN p_destination BIGINT UNSIGNED,
    IN p_first_name VARCHAR(100),
    OUT p_error VARCHAR(255)
)
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 p_error = MESSAGE_TEXT;
    END;

    SET p_error = 'SIN ERROR';

    CALL sp_register_guest_occupant(
        p_trip_id, p_trip_seat_id, p_origin, p_destination,
        p_first_name, 'Test', 1
    );
END$$

DELIMITER ;

-- ---------------------------------------------------------------------------
-- TEST 0: el SP desplegado ya no es el de la 0013
-- ---------------------------------------------------------------------------
SELECT 'TEST 0 - SP con guard' AS test,
       CASE WHEN (SELECT ROUTINE_DEFINITION FROM information_schema.ROUTINES
                   WHERE ROUTINE_SCHEMA = DATABASE()
                     AND ROUTINE_NAME = 'sp_register_guest_occupant')
                 LIKE '%v_trip_status%'
            THEN 'OK' ELSE 'FALLO' END AS resultado;

-- ---------------------------------------------------------------------------
-- TEST 1: PUBLICADO -> permite registrar el invitado
-- ---------------------------------------------------------------------------
CALL verify_call(10, 201, 101, 102, 'Invitado 10', @err_published);

SELECT 'TEST 1 - PUBLISHED permite' AS test,
       CASE WHEN @err_published = 'SIN ERROR'
             AND (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 10 AND status = 'BOARDED') = 1
             AND (SELECT state FROM trip_seat_segments
                   WHERE trip_seat_id = 201 AND trip_segment_id = 301) = 'OCCUPIED'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @err_published AS error_devuelto;

-- ---------------------------------------------------------------------------
-- TEST 2: BOARDING -> permite
-- ---------------------------------------------------------------------------
CALL verify_call(11, 211, 111, 112, 'Invitado 11', @err_boarding);

SELECT 'TEST 2 - BOARDING permite' AS test,
       CASE WHEN @err_boarding = 'SIN ERROR'
             AND (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 11) = 1
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @err_boarding AS error_devuelto;

-- ---------------------------------------------------------------------------
-- TEST 3: EN CURSO -> permite
-- ---------------------------------------------------------------------------
CALL verify_call(12, 221, 121, 122, 'Invitado 12', @err_in_progress);

SELECT 'TEST 3 - IN_PROGRESS permite' AS test,
       CASE WHEN @err_in_progress = 'SIN ERROR'
             AND (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 12) = 1
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @err_in_progress AS error_devuelto;

-- ---------------------------------------------------------------------------
-- TEST 4: BORRADOR -> rechaza
-- ---------------------------------------------------------------------------
CALL verify_call(13, 231, 131, 132, 'Invitado 13', @err_draft);

SELECT 'TEST 4 - DRAFT rechaza' AS test,
       CASE WHEN @err_draft = 'El viaje no esta publicado'
             AND (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 13) = 0
             AND (SELECT state FROM trip_seat_segments
                   WHERE trip_seat_id = 231 AND trip_segment_id = 331) = 'AVAILABLE'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @err_draft AS error_devuelto;

-- ---------------------------------------------------------------------------
-- TEST 5: COMPLETADO -> rechaza (el bug reportado)
-- ---------------------------------------------------------------------------
CALL verify_call(14, 241, 141, 142, 'Invitado 14', @err_completed);

SELECT 'TEST 5 - COMPLETED rechaza' AS test,
       CASE WHEN @err_completed = 'El viaje ya esta cerrado y no admite invitados'
             AND (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 14) = 0
             AND (SELECT state FROM trip_seat_segments
                   WHERE trip_seat_id = 241 AND trip_segment_id = 341) = 'AVAILABLE'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @err_completed AS error_devuelto;

-- ---------------------------------------------------------------------------
-- TEST 6: CANCELADO -> rechaza
-- ---------------------------------------------------------------------------
CALL verify_call(15, 251, 151, 152, 'Invitado 15', @err_cancelled);

SELECT 'TEST 6 - CANCELLED rechaza' AS test,
       CASE WHEN @err_cancelled = 'El viaje ya esta cerrado y no admite invitados'
             AND (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 15) = 0
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @err_cancelled AS error_devuelto;

-- ---------------------------------------------------------------------------
-- TEST 7: viaje inexistente -> rechaza con mensaje propio (antes fallaba con
--         un error de FK crudo de MariaDB)
-- ---------------------------------------------------------------------------
CALL verify_call(999, 201, 101, 102, 'Invitado 999', @err_missing);

SELECT 'TEST 7 - viaje inexistente' AS test,
       CASE WHEN @err_missing = 'El viaje no existe' THEN 'OK' ELSE 'FALLO' END AS resultado,
       @err_missing AS error_devuelto;

-- ---------------------------------------------------------------------------
-- TEST 8: regresion de las validaciones previas (asiento ocupado y duplicado)
-- ---------------------------------------------------------------------------
CALL verify_call(10, 201, 101, 102, 'Invitado 10', @err_duplicate);
CALL verify_call(11, 211, 111, 112, 'Invitado 11', @err_duplicate2);
-- Tramo [1 -> 3) del asiento 221 (viaje 12): sus dos segmentos ya estan
-- OCCUPIED por el invitado del TEST 3, con nombre nuevo para no caer en el
-- chequeo de duplicado.
CALL verify_call(12, 221, 121, 123, 'Otro Invitado', @err_seat_occupied);

SELECT 'TEST 8 - validaciones previas intactas' AS test,
       CASE WHEN @err_duplicate = 'Ese pasajero ya está registrado en este viaje'
             AND @err_duplicate2 = 'Ese pasajero ya está registrado en este viaje'
             AND @err_seat_occupied = 'El asiento ya está ocupado en uno o más tramos solicitados'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @err_duplicate AS duplicado,
       @err_seat_occupied AS asiento_ocupado;

-- ---------------------------------------------------------------------------
-- TEST 9: rollback del .down devuelve el SP de la 0013 (sin guard)
-- ---------------------------------------------------------------------------
source 0019_guest_registration_guard.down.sql

CALL verify_call(14, 241, 141, 142, 'Invitado 14', @err_after_down);

SELECT 'TEST 9 - rollback (down)' AS test,
       CASE WHEN (SELECT ROUTINE_DEFINITION FROM information_schema.ROUTINES
                   WHERE ROUTINE_SCHEMA = DATABASE()
                     AND ROUTINE_NAME = 'sp_register_guest_occupant')
                 NOT LIKE '%v_trip_status%'
             AND @err_after_down = 'SIN ERROR'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @err_after_down AS error_devuelto;

-- ---------------------------------------------------------------------------
-- Limpieza
-- ---------------------------------------------------------------------------
DROP DATABASE transporte_verify_guard;
