-- ============================================================================
-- 0028_remove_mark_date_guard.down.sql
-- Restaura las definiciones de la 0024 (con el guard de fecha) para
-- sp_mark_trip_stop_arrival y sp_mark_trip_stop_departure.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_mark_trip_stop_arrival;
DROP PROCEDURE IF EXISTS sp_mark_trip_stop_departure;

DELIMITER $$

-- Redefinicion de 0018 + guards (status iniciado + ventana de fecha).
CREATE PROCEDURE sp_mark_trip_stop_arrival(
    IN p_trip_stop_time_id BIGINT UNSIGNED,
    IN p_driver_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_assigned_driver_id BIGINT UNSIGNED;
    DECLARE v_trip_status VARCHAR(30);
    DECLARE v_trip_id BIGINT UNSIGNED;
    DECLARE v_service_date DATE;
    DECLARE v_effective_at DATETIME;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SET v_effective_at = CURRENT_TIMESTAMP;

    SELECT trip.driver_id, trip.status, trip.id, trip.service_date
      INTO v_assigned_driver_id, v_trip_status, v_trip_id, v_service_date
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

    -- Guard 1: el viaje debe estar iniciado.
    IF v_trip_status NOT IN ('BOARDING', 'IN_PROGRESS') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje debe estar iniciado para marcar la llegada';
    END IF;

    -- Guard 2: la marca debe pertenecer al dia de servicio (o al siguiente,
    -- para viajes nocturnos que cruzan medianoche).
    IF DATE(v_effective_at) NOT BETWEEN v_service_date AND DATE_ADD(v_service_date, INTERVAL 1 DAY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La marca de llegada esta fuera del dia de servicio del viaje';
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

-- Redefinicion de 0008 + guards (status iniciado + ventana de fecha).
CREATE PROCEDURE sp_mark_trip_stop_departure(
    IN p_trip_stop_time_id BIGINT UNSIGNED,
    IN p_driver_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_assigned_driver_id BIGINT UNSIGNED;
    DECLARE v_trip_status VARCHAR(30);
    DECLARE v_service_date DATE;
    DECLARE v_stop_status VARCHAR(20);

    SELECT trip.driver_id, trip.status, trip.service_date, stop_time.status
      INTO v_assigned_driver_id, v_trip_status, v_service_date, v_stop_status
      FROM trip_stop_times stop_time
      JOIN trip_instances trip ON trip.id = stop_time.trip_id
     WHERE stop_time.id = p_trip_stop_time_id;

    IF v_assigned_driver_id IS NULL OR v_assigned_driver_id <> p_driver_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo el conductor asignado puede marcar la salida';
    END IF;

    IF v_trip_status IN ('COMPLETED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no admite nuevas marcas de salida';
    END IF;

    -- Guard 1: el viaje debe estar iniciado.
    IF v_trip_status NOT IN ('BOARDING', 'IN_PROGRESS') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje debe estar iniciado para marcar la salida';
    END IF;

    -- Guard 2: la marca debe pertenecer al dia de servicio (o al siguiente,
    -- para viajes nocturnos que cruzan medianoche).
    IF DATE(CURRENT_TIMESTAMP) NOT BETWEEN v_service_date AND DATE_ADD(v_service_date, INTERVAL 1 DAY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La marca de salida esta fuera del dia de servicio del viaje';
    END IF;

    IF v_stop_status = 'PENDING' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Primero debe registrarse la llegada al paradero';
    END IF;

    IF v_stop_status = 'DEPARTED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El paradero ya fue marcado como salido';
    END IF;

    UPDATE trip_stop_times
       SET actual_departure_at = CURRENT_TIMESTAMP,
           status = 'DEPARTED'
     WHERE id = p_trip_stop_time_id;
END$$

DELIMITER ;
