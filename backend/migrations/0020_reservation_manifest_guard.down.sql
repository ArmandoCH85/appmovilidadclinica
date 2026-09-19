-- ============================================================================
-- 0020_reservation_manifest_guard.down.sql
-- Revierte la 0020: restaura las cuatro SPs tal como estan en 0001_schema.
--
-- OJO: al revertir vuelve a ser posible abordar a un pasajero en un viaje ya
-- COMPLETED (si su reserva quedo CONFIRMED), que es el bug que la 0020 cierra.
--
-- Nota: los cuerpos son los de 0001_schema.up.sql copiados aqui a proposito
-- (el runner no ejecuta `source`).
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_mark_reservation_boarded;
DROP PROCEDURE IF EXISTS sp_mark_reservation_boarded_self;
DROP PROCEDURE IF EXISTS sp_mark_reservation_no_show;
DROP PROCEDURE IF EXISTS sp_mark_reservation_alighted;

DELIMITER $$

CREATE PROCEDURE sp_mark_reservation_boarded(
    IN p_reservation_id BIGINT UNSIGNED,
    IN p_driver_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_assigned_driver_id BIGINT UNSIGNED;
    DECLARE v_origin_stop_time_id BIGINT UNSIGNED;
    DECLARE v_actual_arrival_at DATETIME;
    DECLARE v_effective_at DATETIME;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SET v_effective_at = CURRENT_TIMESTAMP;

    SELECT reservation.status,
           trip.driver_id,
           reservation.origin_trip_stop_time_id,
           origin_stop.actual_arrival_at
      INTO v_status,
           v_assigned_driver_id,
           v_origin_stop_time_id,
           v_actual_arrival_at
      FROM reservations reservation
      JOIN trip_instances trip ON trip.id = reservation.trip_id
      JOIN trip_stop_times origin_stop
        ON origin_stop.id = reservation.origin_trip_stop_time_id
     WHERE reservation.id = p_reservation_id
     FOR UPDATE;

    IF v_status <> 'CONFIRMED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La reserva no está CONFIRMED';
    END IF;

    IF v_assigned_driver_id <> p_driver_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Sólo el conductor asignado puede confirmar el abordaje';
    END IF;

    IF v_actual_arrival_at IS NULL OR v_effective_at < v_actual_arrival_at THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Primero debe registrarse la llegada física al punto de subida';
    END IF;

    UPDATE reservations
       SET status = 'BOARDED',
           boarded_at = v_effective_at
     WHERE id = p_reservation_id;

    UPDATE trip_seat_segments
       SET state = 'OCCUPIED'
     WHERE reservation_id = p_reservation_id
       AND state = 'RESERVED';

    UPDATE reservation_segments
       SET allocation_status = 'OCCUPIED'
     WHERE reservation_id = p_reservation_id
       AND allocation_status = 'RESERVED';

    INSERT INTO reservation_events (
        reservation_id,
        event_type,
        trip_stop_time_id,
        actor_user_id,
        event_at,
        details
    ) VALUES (
        p_reservation_id,
        'BOARDED',
        v_origin_stop_time_id,
        p_driver_id,
        v_effective_at,
        'Abordaje confirmado por el conductor'
    );

    COMMIT;
END$$

CREATE PROCEDURE sp_mark_reservation_boarded_self(
    IN p_reservation_id BIGINT UNSIGNED,
    IN p_worker_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_reservation_worker_id BIGINT UNSIGNED;
    DECLARE v_origin_stop_time_id BIGINT UNSIGNED;
    DECLARE v_scheduled_departure_at DATETIME;
    DECLARE v_effective_at DATETIME;
    -- Ventana de self-checkin. Coincide con la que aplica la app en
    -- MyReservationDetailViewModel.canSelfCheckin (±30 min alrededor de la
    -- salida). El backend la valida server-side porque el cliente puede
    -- ser modificado; nunca confiamos en el reloj del dispositivo.
    DECLARE v_self_checkin_window_minutes SMALLINT DEFAULT 30;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SET v_effective_at = CURRENT_TIMESTAMP;

    SELECT reservation.status,
           reservation.worker_id,
           reservation.origin_trip_stop_time_id,
           origin_stop.scheduled_departure_at
      INTO v_status,
           v_reservation_worker_id,
           v_origin_stop_time_id,
           v_scheduled_departure_at
      FROM reservations reservation
      JOIN trip_stop_times origin_stop
        ON origin_stop.id = reservation.origin_trip_stop_time_id
     WHERE reservation.id = p_reservation_id
     FOR UPDATE;

    IF v_status <> 'CONFIRMED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La reserva no está CONFIRMED';
    END IF;

    -- Ownership real: solo el dueño de la reserva puede auto-confirmar
    -- su abordaje. Esto cierra el hueco de `cancel` (donde el backend no
    -- valida ownership y la app lo mitiga con filtro client-side): aca la
    -- validacion SI es server-side.
    IF v_reservation_worker_id <> p_worker_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo el trabajador dueno de la reserva puede confirmar su abordaje';
    END IF;

    -- Ventana de tiempo: el boarding solo tiene sentido cerca del horario
    -- real de salida del paradero de origen. ±30 min cubre llegadas
    -- tempranas y pequenas demoras, y bloquea confirmaciones muy fuera
    -- de tiempo que podrian ser intentos de fraude o bugs del cliente.
    IF v_effective_at < DATE_SUB(v_scheduled_departure_at, INTERVAL v_self_checkin_window_minutes MINUTE)
       OR v_effective_at > DATE_ADD(v_scheduled_departure_at, INTERVAL v_self_checkin_window_minutes MINUTE) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Fuera de la ventana de self-checkin (+/-30 min del horario de salida)';
    END IF;

    UPDATE reservations
       SET status = 'BOARDED',
           boarded_at = v_effective_at
     WHERE id = p_reservation_id;

    UPDATE trip_seat_segments
       SET state = 'OCCUPIED'
     WHERE reservation_id = p_reservation_id
       AND state = 'RESERVED';

    UPDATE reservation_segments
       SET allocation_status = 'OCCUPIED'
     WHERE reservation_id = p_reservation_id
       AND allocation_status = 'RESERVED';

    INSERT INTO reservation_events (
        reservation_id,
        event_type,
        trip_stop_time_id,
        actor_user_id,
        event_at,
        details
    ) VALUES (
        p_reservation_id,
        'BOARDED',
        v_origin_stop_time_id,
        p_worker_id,
        v_effective_at,
        'Abordaje auto-confirmado por el pasajero (self check-in)'
    );

    COMMIT;
END$$

CREATE PROCEDURE sp_mark_reservation_no_show(
    IN p_reservation_id BIGINT UNSIGNED,
    IN p_driver_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_assigned_driver_id BIGINT UNSIGNED;
    DECLARE v_origin_stop_time_id BIGINT UNSIGNED;
    DECLARE v_actual_arrival_at DATETIME;
    DECLARE v_tolerance_minutes SMALLINT UNSIGNED;
    DECLARE v_effective_at DATETIME;
    DECLARE v_release_count INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SET v_effective_at = CURRENT_TIMESTAMP;

    SELECT reservation.status,
           trip.driver_id,
           reservation.origin_trip_stop_time_id,
           origin_stop.actual_arrival_at,
           trip.no_show_tolerance_minutes
      INTO v_status,
           v_assigned_driver_id,
           v_origin_stop_time_id,
           v_actual_arrival_at,
           v_tolerance_minutes
      FROM reservations reservation
      JOIN trip_instances trip ON trip.id = reservation.trip_id
      JOIN trip_stop_times origin_stop
        ON origin_stop.id = reservation.origin_trip_stop_time_id
     WHERE reservation.id = p_reservation_id
     FOR UPDATE;

    IF v_status <> 'CONFIRMED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Sólo una reserva CONFIRMED puede pasar a NO_SHOW';
    END IF;

    IF v_assigned_driver_id <> p_driver_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Sólo el conductor asignado puede marcar NO_SHOW';
    END IF;

    IF v_actual_arrival_at IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Debe registrarse la llegada física al punto de subida';
    END IF;

    IF v_effective_at < DATE_ADD(
        v_actual_arrival_at,
        INTERVAL v_tolerance_minutes MINUTE
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aún no terminó el tiempo de tolerancia de NO_SHOW';
    END IF;

    UPDATE trip_seat_segments
       SET state = 'AVAILABLE',
           reservation_id = NULL,
           released_at = v_effective_at
     WHERE reservation_id = p_reservation_id
       AND state = 'RESERVED';

    SET v_release_count = ROW_COUNT();

    UPDATE reservation_segments
       SET allocation_status = 'RELEASED',
           released_at = v_effective_at
     WHERE reservation_id = p_reservation_id
       AND allocation_status = 'RESERVED';

    UPDATE reservations
       SET status = 'NO_SHOW',
           no_show_at = v_effective_at,
           no_show_by_user_id = p_driver_id,
           no_show_trip_stop_time_id = v_origin_stop_time_id
     WHERE id = p_reservation_id;

    INSERT INTO reservation_events (
        reservation_id,
        event_type,
        trip_stop_time_id,
        actor_user_id,
        event_at,
        details
    ) VALUES (
        p_reservation_id,
        'NO_SHOW',
        v_origin_stop_time_id,
        p_driver_id,
        v_effective_at,
        CONCAT('No abordó después de ', v_tolerance_minutes, ' minutos')
    );

    INSERT INTO reservation_events (
        reservation_id,
        event_type,
        trip_stop_time_id,
        actor_user_id,
        event_at,
        details
    ) VALUES (
        p_reservation_id,
        'SEGMENTS_RELEASED',
        v_origin_stop_time_id,
        p_driver_id,
        v_effective_at,
        CONCAT(v_release_count, ' segmentos liberados por NO_SHOW')
    );

    COMMIT;
END$$

CREATE PROCEDURE sp_mark_reservation_alighted(
    IN p_reservation_id BIGINT UNSIGNED,
    IN p_driver_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_assigned_driver_id BIGINT UNSIGNED;
    DECLARE v_destination_stop_time_id BIGINT UNSIGNED;
    DECLARE v_actual_arrival_at DATETIME;
    DECLARE v_effective_at DATETIME;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SET v_effective_at = CURRENT_TIMESTAMP;

    SELECT reservation.status,
           trip.driver_id,
           reservation.destination_trip_stop_time_id,
           destination_stop.actual_arrival_at
      INTO v_status,
           v_assigned_driver_id,
           v_destination_stop_time_id,
           v_actual_arrival_at
      FROM reservations reservation
      JOIN trip_instances trip ON trip.id = reservation.trip_id
      JOIN trip_stop_times destination_stop
        ON destination_stop.id = reservation.destination_trip_stop_time_id
     WHERE reservation.id = p_reservation_id
     FOR UPDATE;

    IF v_status <> 'BOARDED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Sólo una reserva BOARDED puede finalizar';
    END IF;

    IF v_assigned_driver_id <> p_driver_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Sólo el conductor asignado puede confirmar la bajada';
    END IF;

    IF v_actual_arrival_at IS NULL OR v_effective_at < v_actual_arrival_at THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Primero debe registrarse la llegada al destino';
    END IF;

    UPDATE reservations
       SET status = 'COMPLETED',
           completed_at = v_effective_at
     WHERE id = p_reservation_id;

    UPDATE trip_seat_segments
       SET state = 'USED'
     WHERE reservation_id = p_reservation_id
       AND state = 'OCCUPIED';

    UPDATE reservation_segments
       SET allocation_status = 'USED'
     WHERE reservation_id = p_reservation_id
       AND allocation_status = 'OCCUPIED';

    INSERT INTO reservation_events (
        reservation_id,
        event_type,
        trip_stop_time_id,
        actor_user_id,
        event_at,
        details
    ) VALUES (
        p_reservation_id,
        'ALIGHTED',
        v_destination_stop_time_id,
        p_driver_id,
        v_effective_at,
        'Bajada confirmada en el destino programado'
    );

    COMMIT;
END$$

DELIMITER ;
