-- ============================================================================
-- 0020_reservation_manifest_guard.up.sql
-- Valida el estado del viaje en las cuatro SPs que cambian el manifiesto.
--
-- Bug (mismo tipo que el de los invitados, 0019): ni
-- sp_mark_reservation_boarded, ni sp_mark_reservation_boarded_self, ni
-- sp_mark_reservation_no_show, ni sp_mark_reservation_alighted miraban el
-- estado del viaje. El unico cierre era el estado de la reserva.
--
-- Por que queda expuesto: sp_complete_trip (0017) cierra las reservas
-- BOARDED, pero NO las que quedaron CONFIRMED. Un viaje COMPLETADO con una
-- reserva CONFIRMED deja esa reserva abordable para siempre:
--   - el conductor puede marcarla desde la app, porque la lista de viajes
--     filtra solo status <> 'CANCELLED' (driver/repository.go:172) y la
--     tarjeta del pasajero muestra "Abordar" mientras la reserva este
--     CONFIRMED (TripDetailScreen.kt:368);
--   - el pasajero puede auto-confirmarla con su app dentro de los +/-30 min
--     del horario de salida, aunque el viaje ya haya terminado.
-- Los viajes CANCELLED no quedan expuestos: sp_cancel_trip (0002) cancela
-- sus reservas CONFIRMED/BOARDED.
--
-- Ventana permitida: PUBLISHED, BOARDING e IN_PROGRESS. Se rechazan DRAFT,
-- COMPLETED y CANCELLED. Mismo criterio que la 0019.
--
-- Cuerpos base: 0001_schema.up.sql. Se agrega la columna trip.status al
-- SELECT ... INTO existente (que ya tiene FOR UPDATE), salvo en
-- sp_mark_reservation_boarded_self, que no joineaba trip_instances.
--
-- Nota sobre el job de NO_SHOW: platform/jobs/noshow_checker.go NO llama a
-- sp_mark_reservation_no_show (inlinea su propia logica para poder correr en
-- una transaccion Go), asi que este guard no lo afecta. Su SELECT tampoco
-- filtra por estado del viaje, y eso es deliberado: es el unico mecanismo que
-- termina de limpiar las reservas CONFIRMED que quedaron colgadas en un viaje
-- ya cerrado (las libera como NO_SHOW). Agregarle el filtro las dejaria
-- RESERVED para siempre.
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

    DECLARE v_trip_status VARCHAR(30);

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
           trip.status
      INTO v_status,
           v_assigned_driver_id,
           v_origin_stop_time_id,
           v_actual_arrival_at,
           v_trip_status
      FROM reservations reservation
      JOIN trip_instances trip ON trip.id = reservation.trip_id
      JOIN trip_stop_times origin_stop
        ON origin_stop.id = reservation.origin_trip_stop_time_id
     WHERE reservation.id = p_reservation_id
     FOR UPDATE;

    -- 0020: el viaje tiene que admitir cambios en el manifiesto. Antes estas
    -- SPs nunca miraban el estado del viaje: una reserva que quedo CONFIRMED
    -- cuando el viaje se completo todavia se podia abordar (o auto-confirmar
    -- dentro de la ventana de +/-30 min), dejando un abordaje despues del fin
    -- real del viaje.
    IF v_trip_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no existe';
    END IF;

    IF v_trip_status IN ('COMPLETED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje ya esta cerrado y no admite cambios en el manifiesto';
    END IF;

    IF v_trip_status = 'DRAFT' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no esta publicado';
    END IF;

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

    DECLARE v_trip_status VARCHAR(30);

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
           origin_stop.scheduled_departure_at,
           trip.status
      INTO v_status,
           v_reservation_worker_id,
           v_origin_stop_time_id,
           v_scheduled_departure_at,
           v_trip_status
      FROM reservations reservation
      JOIN trip_instances trip ON trip.id = reservation.trip_id
      JOIN trip_stop_times origin_stop
        ON origin_stop.id = reservation.origin_trip_stop_time_id
     WHERE reservation.id = p_reservation_id
     FOR UPDATE;

    -- 0020: el viaje tiene que admitir cambios en el manifiesto. Antes estas
    -- SPs nunca miraban el estado del viaje: una reserva que quedo CONFIRMED
    -- cuando el viaje se completo todavia se podia abordar (o auto-confirmar
    -- dentro de la ventana de +/-30 min), dejando un abordaje despues del fin
    -- real del viaje.
    IF v_trip_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no existe';
    END IF;

    IF v_trip_status IN ('COMPLETED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje ya esta cerrado y no admite cambios en el manifiesto';
    END IF;

    IF v_trip_status = 'DRAFT' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no esta publicado';
    END IF;

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

    DECLARE v_trip_status VARCHAR(30);

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
           trip.no_show_tolerance_minutes,
           trip.status
      INTO v_status,
           v_assigned_driver_id,
           v_origin_stop_time_id,
           v_actual_arrival_at,
           v_tolerance_minutes,
           v_trip_status
      FROM reservations reservation
      JOIN trip_instances trip ON trip.id = reservation.trip_id
      JOIN trip_stop_times origin_stop
        ON origin_stop.id = reservation.origin_trip_stop_time_id
     WHERE reservation.id = p_reservation_id
     FOR UPDATE;

    -- 0020: el viaje tiene que admitir cambios en el manifiesto. Antes estas
    -- SPs nunca miraban el estado del viaje: una reserva que quedo CONFIRMED
    -- cuando el viaje se completo todavia se podia abordar (o auto-confirmar
    -- dentro de la ventana de +/-30 min), dejando un abordaje despues del fin
    -- real del viaje.
    IF v_trip_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no existe';
    END IF;

    IF v_trip_status IN ('COMPLETED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje ya esta cerrado y no admite cambios en el manifiesto';
    END IF;

    IF v_trip_status = 'DRAFT' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no esta publicado';
    END IF;

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

    DECLARE v_trip_status VARCHAR(30);

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
           destination_stop.actual_arrival_at,
           trip.status
      INTO v_status,
           v_assigned_driver_id,
           v_destination_stop_time_id,
           v_actual_arrival_at,
           v_trip_status
      FROM reservations reservation
      JOIN trip_instances trip ON trip.id = reservation.trip_id
      JOIN trip_stop_times destination_stop
        ON destination_stop.id = reservation.destination_trip_stop_time_id
     WHERE reservation.id = p_reservation_id
     FOR UPDATE;

    -- 0020: el viaje tiene que admitir cambios en el manifiesto. Antes estas
    -- SPs nunca miraban el estado del viaje: una reserva que quedo CONFIRMED
    -- cuando el viaje se completo todavia se podia abordar (o auto-confirmar
    -- dentro de la ventana de +/-30 min), dejando un abordaje despues del fin
    -- real del viaje.
    IF v_trip_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no existe';
    END IF;

    IF v_trip_status IN ('COMPLETED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje ya esta cerrado y no admite cambios en el manifiesto';
    END IF;

    IF v_trip_status = 'DRAFT' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no esta publicado';
    END IF;

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
