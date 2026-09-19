-- ============================================================================
-- 0018_guest_auto_alight.up.sql
-- Bajada automatica de OCUPANTES INVITADOS.
--
-- Motivo: la 0017 cierra las reservas al llegar al destino o al finalizar el
-- viaje, pero filtra todo por reservation_id. Los invitados de la 0013 ocupan
-- trip_seat_segments via guest_occupant_id con reservation_id NULL, asi que
-- quedaban fuera y acumulaban dos fugas:
--   1. guest_occupants.status se quedaba en BOARDED para siempre.
--   2. sus segmentos de asiento quedaban OCCUPIED para siempre (asiento
--      fantasma: nadie lo volvia a liberar).
--
-- Esta migracion:
--   a) amplia guest_occupants.status a ENUM('BOARDED','COMPLETED') y agrega
--      completed_at (espejo de reservations.completed_at);
--   b) recrea sp_mark_trip_stop_arrival y sp_complete_trip (los de la 0017)
--      agregando la rama de invitados.
--
-- Nota: NO se escribe en reservation_events para invitados. Esa tabla tiene
-- reservation_id NOT NULL con FK a reservations, y el invitado no tiene
-- reserva. La bitacora de bajada de invitados queda en guest_occupants
-- (status + completed_at).
--
-- Nota: se usa DROP + CREATE (no CREATE OR REPLACE) para mantener el mismo
-- criterio que 0002/0013/0016/0017 y no depender de sintaxis solo-MariaDB.
-- ============================================================================

ALTER TABLE guest_occupants
    MODIFY COLUMN status ENUM('BOARDED','COMPLETED') NOT NULL DEFAULT 'BOARDED',
    ADD COLUMN completed_at DATETIME NULL AFTER status;

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

    -- Bitacora de bajada automatica (antes de cambiar el estado).
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

    -- 0018: invitados cuyo destino es esta parada. Los segmentos se marcan
    -- ANTES de cerrar al invitado, porque el subquery filtra por
    -- guest_occupants.status = 'BOARDED'.
    UPDATE trip_seat_segments
       SET state = 'USED'
     WHERE state = 'OCCUPIED'
       AND guest_occupant_id IN (
           SELECT id FROM guest_occupants
            WHERE trip_id = v_trip_id
              AND destination_trip_stop_time_id = p_trip_stop_time_id
              AND status = 'BOARDED'
       );

    UPDATE guest_occupants
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

    -- 0018: invitados que quedaron arriba (mismo criterio que las reservas).
    UPDATE trip_seat_segments
       SET state = 'USED'
     WHERE state = 'OCCUPIED'
       AND guest_occupant_id IN (
           SELECT id FROM guest_occupants
            WHERE trip_id = p_trip_id AND status = 'BOARDED'
       );

    UPDATE guest_occupants
       SET status = 'COMPLETED',
           completed_at = v_effective_at
     WHERE trip_id = p_trip_id
       AND status = 'BOARDED';

    COMMIT;
END$$

DELIMITER ;
