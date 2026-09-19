-- ============================================================================
-- 0018_guest_auto_alight.down.sql
-- Revierte la 0018: restaura los SPs tal como quedaron en la 0017 (sin rama de
-- invitados) y devuelve guest_occupants.status a ENUM('BOARDED') sin
-- completed_at.
--
-- OJO: los invitados que ya quedaron en COMPLETED se reabren a BOARDED antes
-- de angostar el ENUM (MariaDB rechaza el ALTER si hay filas con un valor que
-- no existe en el ENUM destino). Sus segmentos de asiento NO se devuelven a
-- OCCUPIED: quedan USED, igual que los de las reservas bajadas por la 0017.
--
-- Nota: los cuerpos de los SPs son los de 0017_auto_alight.up.sql copiados
-- aqui a proposito (el runner no ejecuta `source`).
-- ============================================================================

UPDATE guest_occupants
   SET status = 'BOARDED',
       completed_at = NULL
 WHERE status = 'COMPLETED';

ALTER TABLE guest_occupants
    DROP COLUMN completed_at,
    MODIFY COLUMN status ENUM('BOARDED') NOT NULL DEFAULT 'BOARDED';

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
