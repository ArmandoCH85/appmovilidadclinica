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

    -- Lock no-op sobre el rango nuevo (patron de sp_confirm_reservation).
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
