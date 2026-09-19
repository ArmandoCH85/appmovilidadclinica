-- ============================================================================
-- verify_admin_trip_closure.sql
-- Verifica 0021_admin_trip_closure en una base DESCARTABLE. NO toca ninguna
-- base existente: crea y borra `transporte_verify_admin_close`.
--
-- Uso (desde el directorio de migraciones, para que resuelvan los `source`):
--   cd /root/appmovilidadclinica/backend/migrations
--   mariadb -u root < ../scripts/verify_admin_trip_closure.sql
--
-- Resultado esperado: todos los SELECT de TEST deben dar OK.
--
-- Cubre:
--   - sp_admin_close_trip: cierra reservas e invitados BOARDED (incluidos sus
--     segmentos), setea actual_end_at y NO exige estado IN_PROGRESS.
--   - sp_cancel_trip: ahora tambien libera y cierra a los invitados.
--   - rollback del .down: vuelve a dejar a los invitados colgados al cancelar,
--     que es el bug que la 0021 cierra.
-- ============================================================================

DROP DATABASE IF EXISTS transporte_verify_admin_close;
CREATE DATABASE transporte_verify_admin_close CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE transporte_verify_admin_close;

-- ---------------------------------------------------------------------------
-- Esquema minimo (solo lo que tocan las SPs de cierre/cancelacion)
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
    actual_end_at DATETIME NULL,
    cancellation_reason VARCHAR(255) NULL
) ENGINE=InnoDB;

CREATE TABLE trip_stop_times (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    stop_order SMALLINT UNSIGNED NOT NULL,
    scheduled_departure_at DATETIME NULL,
    actual_arrival_at DATETIME NULL
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
    status ENUM('CONFIRMED','BOARDED','COMPLETED','NO_SHOW','CANCELLED') NOT NULL,
    boarded_at DATETIME NULL,
    completed_at DATETIME NULL
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
-- Migraciones reales: 0013 (tabla guest_occupants), 0018 (completed_at del
-- invitado) y 0021 (lo nuevo)
-- ---------------------------------------------------------------------------

source 0013_guest_occupants.up.sql
source 0018_guest_auto_alight.up.sql
source 0021_admin_trip_closure.up.sql

-- ---------------------------------------------------------------------------
-- Fixtures
--   viaje 10 PUBLISHED : res 10 CONFIRMED, res 11 BOARDED, invitados 1 y 2
--   viaje 11 IN_PROGRESS: res 12 CONFIRMED, invitado 3
--   viaje 12 COMPLETED  : viaje ya cerrado
--   viaje 14 PUBLISHED  : invitado 4 (para el test de rollback)
-- ---------------------------------------------------------------------------

INSERT INTO users (id, role, active) VALUES (1, 'DRIVER', 1), (2, 'WORKER', 1), (77, 'ADMIN', 1);

INSERT INTO trip_instances (id, driver_id, status) VALUES
    (10, 1, 'PUBLISHED'),
    (11, 1, 'IN_PROGRESS'),
    (12, 1, 'COMPLETED'),
    (14, 1, 'PUBLISHED');

INSERT INTO trip_stop_times (id, trip_id, stop_order) VALUES
    (101, 10, 1), (102, 10, 2),
    (111, 11, 1), (112, 11, 2),
    (141, 14, 1), (142, 14, 2);

INSERT INTO trip_seats (id, trip_id, seat_label) VALUES
    (201, 10, '1'), (202, 10, '2'), (203, 10, '3'), (204, 10, '4'),
    (211, 11, '1'), (241, 14, '1');

INSERT INTO trip_segments (id, trip_id, segment_order) VALUES
    (301, 10, 1), (311, 11, 1), (341, 14, 1);

INSERT INTO reservations (id, trip_id, worker_id, trip_seat_id,
                          origin_trip_stop_time_id, destination_trip_stop_time_id,
                          origin_stop_order, destination_stop_order, status) VALUES
    (10, 10, 2, 201, 101, 102, 1, 2, 'CONFIRMED'),
    (11, 10, 2, 202, 101, 102, 1, 2, 'BOARDED'),
    (12, 11, 2, 211, 111, 112, 1, 2, 'CONFIRMED');

-- Segmentos de las reservas.
INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state, reservation_id, reserved_at) VALUES
    (201, 301, 'RESERVED', 10, NOW()),
    (202, 301, 'OCCUPIED', 11, NOW()),
    (211, 311, 'RESERVED', 12, NOW());

INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status) VALUES
    (10, 301, 'RESERVED'), (11, 301, 'OCCUPIED'), (12, 311, 'RESERVED');

-- Invitados (id, viaje, asiento, origen, destino, ordenes).
INSERT INTO guest_occupants (id, trip_id, trip_seat_id,
                             origin_trip_stop_time_id, destination_trip_stop_time_id,
                             origin_stop_order, destination_stop_order,
                             first_name, last_name, registered_by_user_id, status) VALUES
    (1, 10, 203, 101, 102, 1, 2, 'Invitado', 'Uno', 1, 'BOARDED'),
    (2, 10, 204, 101, 102, 1, 2, 'Invitado', 'Dos', 1, 'BOARDED'),
    (3, 11, 211, 111, 112, 1, 2, 'Invitado', 'Tres', 1, 'BOARDED'),
    (4, 14, 241, 141, 142, 1, 2, 'Invitado', 'Cuatro', 1, 'BOARDED');

-- Segmentos de los invitados: por convencion quedan OCCUPIED (0013).
INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state, guest_occupant_id, reserved_at) VALUES
    (203, 301, 'OCCUPIED', 1, NOW()),
    (204, 301, 'OCCUPIED', 2, NOW()),
    (241, 341, 'OCCUPIED', 4, NOW());
-- El invitado 3 comparte asiento 211/segmento 311 con la reserva 12: se usa
-- otro segmento para no chocar. Se crea uno propio para el viaje 11.
INSERT INTO trip_segments (id, trip_id, segment_order) VALUES (312, 11, 2);
INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state, guest_occupant_id, reserved_at) VALUES
    (211, 312, 'OCCUPIED', 3, NOW());

-- ---------------------------------------------------------------------------
-- Helper: llama la SP indicada y devuelve 'SIN ERROR' o el mensaje del SIGNAL
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS verify_call;

DELIMITER $$

CREATE PROCEDURE verify_call(
    IN p_proc VARCHAR(20),
    IN p_trip_id BIGINT UNSIGNED,
    IN p_actor_id BIGINT UNSIGNED,
    OUT p_error VARCHAR(255)
)
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 p_error = MESSAGE_TEXT;
    END;

    SET p_error = 'SIN ERROR';

    IF p_proc = 'admin_close' THEN
        CALL sp_admin_close_trip(p_trip_id, p_actor_id);
    ELSEIF p_proc = 'cancel_trip' THEN
        CALL sp_cancel_trip(p_trip_id, 'Motivo de prueba', p_actor_id);
    ELSE
        SET p_error = CONCAT('proc desconocida: ', p_proc);
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------------
-- TEST 0: esquema y SP nueva
-- ---------------------------------------------------------------------------
SELECT 'TEST 0 - esquema y SP nueva' AS test,
       CASE WHEN (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'guest_occupants'
                     AND COLUMN_NAME = 'status') LIKE '%CANCELLED%'
             AND (SELECT COUNT(*) FROM information_schema.ROUTINES
                   WHERE ROUTINE_SCHEMA = DATABASE()
                     AND ROUTINE_NAME = 'sp_admin_close_trip') = 1
             AND (SELECT ROUTINE_DEFINITION FROM information_schema.ROUTINES
                   WHERE ROUTINE_SCHEMA = DATABASE()
                     AND ROUTINE_NAME = 'sp_cancel_trip') LIKE '%guest_occupants%'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'guest_occupants'
           AND COLUMN_NAME = 'status') AS guest_status_type;

-- ---------------------------------------------------------------------------
-- TEST 1: cierre por admin desde PUBLISHED. Cierra reservas BOARDED e
--         invitados, marca segmentos USED, setea actual_end_at y NO toca las
--         reservas CONFIRMED (esas las limpia el job de NO_SHOW).
-- ---------------------------------------------------------------------------
CALL verify_call('admin_close', 10, 77, @t1_err);

SELECT 'TEST 1 - cierre por admin' AS test,
       CASE WHEN @t1_err = 'SIN ERROR'
             AND (SELECT status FROM trip_instances WHERE id = 10) = 'COMPLETED'
             AND (SELECT actual_end_at IS NOT NULL FROM trip_instances WHERE id = 10) = 1
             AND (SELECT status FROM reservations WHERE id = 11) = 'COMPLETED'
             AND (SELECT completed_at IS NOT NULL FROM reservations WHERE id = 11) = 1
             AND (SELECT status FROM reservations WHERE id = 10) = 'CONFIRMED'
             AND (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 10 AND status = 'COMPLETED') = 2
             AND (SELECT COUNT(*) FROM guest_occupants WHERE trip_id = 10 AND completed_at IS NULL) = 0
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 202 AND trip_segment_id = 301) = 'USED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 203 AND trip_segment_id = 301) = 'USED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 204 AND trip_segment_id = 301) = 'USED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 201 AND trip_segment_id = 301) = 'RESERVED'
             AND (SELECT COUNT(*) FROM reservation_events
                   WHERE reservation_id = 11 AND event_type = 'ALIGHTED'
                     AND actor_user_id = 77) = 1
             AND (SELECT COUNT(*) FROM reservation_events WHERE reservation_id = 10) = 0
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t1_err AS error_devuelto,
       (SELECT status FROM trip_instances WHERE id = 10) AS viaje10,
       (SELECT COUNT(*) FROM guest_occupants
         WHERE trip_id = 10 AND status = 'COMPLETED') AS invitados_cerrados;

-- ---------------------------------------------------------------------------
-- TEST 2: guardas de sp_admin_close_trip
-- ---------------------------------------------------------------------------
CALL verify_call('admin_close', 10, 77, @t2_ya_cerrado);
CALL verify_call('admin_close', 12, 77, @t2_completado);
CALL verify_call('admin_close', 999, 77, @t2_inexistente);

SELECT 'TEST 2 - guardas del cierre' AS test,
       CASE WHEN @t2_ya_cerrado = 'El viaje ya esta cerrado'
             AND @t2_completado = 'El viaje ya esta cerrado'
             AND @t2_inexistente = 'El viaje no existe'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t2_ya_cerrado AS ya_cerrado, @t2_completado AS completado,
       @t2_inexistente AS inexistente;

-- ---------------------------------------------------------------------------
-- TEST 3: cancelar un viaje ahora libera y cierra tambien a los invitados
-- ---------------------------------------------------------------------------
CALL verify_call('cancel_trip', 11, 77, @t3_err);

SELECT 'TEST 3 - cancelacion con invitados' AS test,
       CASE WHEN @t3_err = 'SIN ERROR'
             AND (SELECT status FROM trip_instances WHERE id = 11) = 'CANCELLED'
             AND (SELECT cancellation_reason FROM trip_instances WHERE id = 11) = 'Motivo de prueba'
             AND (SELECT status FROM reservations WHERE id = 12) = 'CANCELLED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 211 AND trip_segment_id = 311) = 'AVAILABLE'
             AND (SELECT status FROM guest_occupants WHERE id = 3) = 'CANCELLED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 211 AND trip_segment_id = 312) = 'AVAILABLE'
             AND (SELECT guest_occupant_id IS NULL FROM trip_seat_segments
                   WHERE trip_seat_id = 211 AND trip_segment_id = 312) = 1
             AND (SELECT released_at IS NOT NULL FROM trip_seat_segments
                   WHERE trip_seat_id = 211 AND trip_segment_id = 312) = 1
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t3_err AS error_devuelto,
       (SELECT status FROM guest_occupants WHERE id = 3) AS invitado3;

-- ---------------------------------------------------------------------------
-- TEST 4: rollback. Sin la 0021, cancelar un viaje deja al invitado colgado
--         (BOARDED y con el asiento OCCUPIED) y sp_admin_close_trip no existe.
-- ---------------------------------------------------------------------------
source 0021_admin_trip_closure.down.sql

CALL verify_call('cancel_trip', 14, 77, @t4_err);

SELECT 'TEST 4 - rollback deja al invitado colgado' AS test,
       CASE WHEN (SELECT COUNT(*) FROM information_schema.ROUTINES
                   WHERE ROUTINE_SCHEMA = DATABASE()
                     AND ROUTINE_NAME = 'sp_admin_close_trip') = 0
             AND (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'guest_occupants'
                     AND COLUMN_NAME = 'status') = 'enum(''BOARDED'',''COMPLETED'')'
             AND @t4_err = 'SIN ERROR'
             AND (SELECT status FROM trip_instances WHERE id = 14) = 'CANCELLED'
             AND (SELECT status FROM guest_occupants WHERE id = 4) = 'BOARDED'
             AND (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 241 AND trip_segment_id = 341) = 'OCCUPIED'
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       @t4_err AS error_devuelto,
       (SELECT status FROM guest_occupants WHERE id = 4) AS invitado4,
       (SELECT state FROM trip_seat_segments WHERE trip_seat_id = 241 AND trip_segment_id = 341) AS asiento_invitado4;

-- ---------------------------------------------------------------------------
-- Limpieza
-- ---------------------------------------------------------------------------
DROP DATABASE transporte_verify_admin_close;
