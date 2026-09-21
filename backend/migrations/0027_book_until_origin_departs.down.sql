-- ============================================================================
-- 0027_book_until_origin_departs.down.sql
-- Restaura las definiciones previas a la 0027:
--   - sp_search_trips        (tal cual la 0011: solo status PUBLISHED)
--   - sp_confirm_reservation (tal cual la 0026: exige PUBLISHED + ventana)
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_search_trips;
DROP PROCEDURE IF EXISTS sp_confirm_reservation;

DELIMITER $$

CREATE PROCEDURE sp_search_trips(
    IN p_service_date DATE,
    IN p_direction VARCHAR(10),
    IN p_origin_stop_id BIGINT UNSIGNED,
    IN p_destination_stop_id BIGINT UNSIGNED
)
BEGIN
    SELECT trip.id AS trip_id,
           trip.trip_code,
           route.code AS route_code,
           route.name AS route_name,
           route.direction,
           origin_stop.stop_order AS origin_order,
           origin_place.name AS origin_name,
           origin_stop.scheduled_departure_at AS origin_departure_at,
           destination_stop.stop_order AS destination_order,
           destination_place.name AS destination_name,
           destination_stop.scheduled_arrival_at AS destination_arrival_at,
           vehicle.internal_code AS vehicle_code,
           vehicle.plate,
           trip.booking_opens_at,
           trip.booking_closes_at,
           CASE
               WHEN CURRENT_TIMESTAMP < trip.booking_opens_at THEN 'NOT_OPEN'
               WHEN CURRENT_TIMESTAMP >= trip.booking_closes_at THEN 'CLOSED'
               ELSE 'OPEN'
           END AS booking_state,
           (
               SELECT COUNT(*)
                 FROM trip_seats candidate_seat
                WHERE candidate_seat.trip_id = trip.id
                  AND candidate_seat.is_blocked = 0
                  AND NOT EXISTS (
                      SELECT 1
                        FROM trip_segments requested_segment
                        JOIN trip_seat_segments inventory
                          ON inventory.trip_segment_id = requested_segment.id
                         AND inventory.trip_seat_id = candidate_seat.id
                       WHERE requested_segment.trip_id = trip.id
                         AND requested_segment.segment_order >= origin_stop.stop_order
                         AND requested_segment.segment_order < destination_stop.stop_order
                         AND inventory.state <> 'AVAILABLE'
                  )
           ) AS available_seats
      FROM trip_instances trip
      JOIN transport_routes route
        ON route.id = trip.route_id
      JOIN trip_stop_times origin_stop
        ON origin_stop.trip_id = trip.id
       AND origin_stop.stop_id = p_origin_stop_id
      JOIN trip_stop_times destination_stop
        ON destination_stop.trip_id = trip.id
       AND destination_stop.stop_id = p_destination_stop_id
      JOIN route_stops origin_rule
        ON origin_rule.id = origin_stop.route_stop_id
      JOIN route_stops destination_rule
        ON destination_rule.id = destination_stop.route_stop_id
      JOIN transport_stops origin_place
        ON origin_place.id = origin_stop.stop_id
      JOIN transport_stops destination_place
        ON destination_place.id = destination_stop.stop_id
      JOIN vehicles vehicle
        ON vehicle.id = trip.vehicle_id
     WHERE trip.service_date = p_service_date
       AND route.direction = p_direction
       AND trip.status = 'PUBLISHED'
       AND origin_stop.stop_order < destination_stop.stop_order
       AND origin_rule.pickup_allowed = 1
       AND destination_rule.dropoff_allowed = 1
       AND (
            (route.direction = 'IDA' AND destination_place.stop_type = 'SEDE')
            OR (route.direction = 'VUELTA')
       )
     ORDER BY origin_stop.scheduled_departure_at;
END$$

CREATE PROCEDURE sp_confirm_reservation(
    IN p_trip_id BIGINT UNSIGNED,
    IN p_worker_id BIGINT UNSIGNED,
    IN p_trip_seat_id BIGINT UNSIGNED,
    IN p_origin_trip_stop_time_id BIGINT UNSIGNED,
    IN p_destination_trip_stop_time_id BIGINT UNSIGNED,
    IN p_booking_group_uuid CHAR(36)
)
BEGIN
    DECLARE v_trip_exists INT DEFAULT 0;
    DECLARE v_worker_role VARCHAR(20);
    DECLARE v_trip_status VARCHAR(30);
    DECLARE v_direction VARCHAR(10);
    DECLARE v_booking_opens_at DATETIME;
    DECLARE v_booking_closes_at DATETIME;
    DECLARE v_origin_order SMALLINT UNSIGNED;
    DECLARE v_destination_order SMALLINT UNSIGNED;
    DECLARE v_origin_type VARCHAR(20);
    DECLARE v_destination_type VARCHAR(20);
    DECLARE v_pickup_allowed TINYINT;
    DECLARE v_dropoff_allowed TINYINT;
    DECLARE v_expected_segments INT;
    DECLARE v_inventory_rows INT;
    DECLARE v_conflicting_segments INT;
    DECLARE v_reservation_id BIGINT UNSIGNED;
    DECLARE v_reservation_code VARCHAR(32);
    DECLARE v_qr_token CHAR(36);
    DECLARE v_qr_hash CHAR(64);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*)
      INTO v_trip_exists
      FROM trip_instances
     WHERE id = p_trip_id;

    IF v_trip_exists = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El viaje no existe';
    END IF;

    SELECT worker.role
      INTO v_worker_role
      FROM users worker
     WHERE worker.id = p_worker_id
       AND worker.active = 1;

    IF v_worker_role IS NULL OR v_worker_role <> 'WORKER' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La reserva sólo puede asignarse a un WORKER activo';
    END IF;

    SELECT trip.status,
           route.direction,
           trip.booking_opens_at,
           trip.booking_closes_at
      INTO v_trip_status,
           v_direction,
           v_booking_opens_at,
           v_booking_closes_at
      FROM trip_instances trip
      JOIN transport_routes route ON route.id = trip.route_id
     WHERE trip.id = p_trip_id
     FOR UPDATE;

    IF v_trip_status <> 'PUBLISHED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no está publicado para reservas';
    END IF;

    IF CURRENT_TIMESTAMP < v_booking_opens_at
       OR CURRENT_TIMESTAMP >= v_booking_closes_at THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La ventana de reserva no está abierta';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM trip_seats
         WHERE id = p_trip_seat_id
           AND trip_id = p_trip_id
           AND is_blocked = 0
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El asiento no pertenece al viaje o está bloqueado';
    END IF;

    SELECT origin_time.stop_order,
           destination_time.stop_order,
           origin_place.stop_type,
           destination_place.stop_type,
           origin_rule.pickup_allowed,
           destination_rule.dropoff_allowed
      INTO v_origin_order,
           v_destination_order,
           v_origin_type,
           v_destination_type,
           v_pickup_allowed,
           v_dropoff_allowed
      FROM trip_stop_times origin_time
      JOIN trip_stop_times destination_time
        ON destination_time.id = p_destination_trip_stop_time_id
       AND destination_time.trip_id = origin_time.trip_id
      JOIN route_stops origin_rule
        ON origin_rule.id = origin_time.route_stop_id
      JOIN route_stops destination_rule
        ON destination_rule.id = destination_time.route_stop_id
      JOIN transport_stops origin_place
        ON origin_place.id = origin_time.stop_id
      JOIN transport_stops destination_place
        ON destination_place.id = destination_time.stop_id
     WHERE origin_time.id = p_origin_trip_stop_time_id
       AND origin_time.trip_id = p_trip_id;

    IF v_origin_order IS NULL
       OR v_destination_order IS NULL
       OR v_origin_order >= v_destination_order THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El origen y destino no forman un tramo válido';
    END IF;

    IF v_pickup_allowed = 0 OR v_dropoff_allowed = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La ruta no permite subir o bajar en los puntos elegidos';
    END IF;

    -- Reglas direccionales (ver 0026):
    --   IDA    -> el DESTINO debe ser SEDE.
    --   VUELTA -> sin restricción de tipo de origen (0011 permitió subir en
    --             paraderos; acá se alinea la reserva con esa búsqueda).
    -- El tipo de origen (v_origin_type) se sigue leyendo arriba por claridad,
    -- pero ya no bloquea nada en VUELTA: lo que manda es pickup_allowed.
    IF v_direction = 'IDA' AND v_destination_type <> 'SEDE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'En IDA, el destino de la reserva debe ser una SEDE';
    END IF;

    SET v_expected_segments = v_destination_order - v_origin_order;

    -- UPDATE sin cambio funcional: bloquea las filas exactas en InnoDB.
    UPDATE trip_seat_segments inventory
    JOIN trip_segments segment
      ON segment.id = inventory.trip_segment_id
     SET inventory.updated_at = CURRENT_TIMESTAMP
   WHERE inventory.trip_seat_id = p_trip_seat_id
     AND segment.trip_id = p_trip_id
     AND segment.segment_order >= v_origin_order
     AND segment.segment_order < v_destination_order;

    SELECT COUNT(*),
           SUM(CASE WHEN inventory.state <> 'AVAILABLE' THEN 1 ELSE 0 END)
      INTO v_inventory_rows, v_conflicting_segments
      FROM trip_seat_segments inventory
      JOIN trip_segments segment
        ON segment.id = inventory.trip_segment_id
     WHERE inventory.trip_seat_id = p_trip_seat_id
       AND segment.trip_id = p_trip_id
       AND segment.segment_order >= v_origin_order
       AND segment.segment_order < v_destination_order;

    IF v_inventory_rows <> v_expected_segments THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El inventario por segmentos del asiento está incompleto';
    END IF;

    IF COALESCE(v_conflicting_segments, 0) > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El asiento ya está ocupado en uno o más tramos solicitados';
    END IF;

    SET v_reservation_code = CONCAT(
        'R-', UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 24))
    );
    SET v_qr_token = UUID();
    SET v_qr_hash = SHA2(v_qr_token, 256);

    INSERT INTO reservations (
        reservation_code,
        qr_token_hash,
        booking_group_uuid,
        trip_id,
        worker_id,
        trip_seat_id,
        origin_trip_stop_time_id,
        destination_trip_stop_time_id,
        origin_stop_order,
        destination_stop_order,
        status,
        created_by_user_id
    ) VALUES (
        v_reservation_code,
        v_qr_hash,
        p_booking_group_uuid,
        p_trip_id,
        p_worker_id,
        p_trip_seat_id,
        p_origin_trip_stop_time_id,
        p_destination_trip_stop_time_id,
        v_origin_order,
        v_destination_order,
        'CONFIRMED',
        p_worker_id
    );

    SET v_reservation_id = LAST_INSERT_ID();

    INSERT INTO reservation_segments (
        reservation_id,
        trip_segment_id,
        allocation_status
    )
    SELECT v_reservation_id,
           segment.id,
           'RESERVED'
      FROM trip_segments segment
     WHERE segment.trip_id = p_trip_id
       AND segment.segment_order >= v_origin_order
       AND segment.segment_order < v_destination_order;

    UPDATE trip_seat_segments inventory
    JOIN trip_segments segment
      ON segment.id = inventory.trip_segment_id
     SET inventory.state = 'RESERVED',
         inventory.reservation_id = v_reservation_id,
         inventory.reserved_at = CURRENT_TIMESTAMP,
         inventory.released_at = NULL
   WHERE inventory.trip_seat_id = p_trip_seat_id
     AND segment.trip_id = p_trip_id
     AND segment.segment_order >= v_origin_order
     AND segment.segment_order < v_destination_order;

    INSERT INTO reservation_events (
        reservation_id,
        event_type,
        trip_stop_time_id,
        actor_user_id,
        details
    ) VALUES (
        v_reservation_id,
        'CONFIRMED',
        p_origin_trip_stop_time_id,
        p_worker_id,
        CONCAT(
            'Asiento bloqueado desde el orden ', v_origin_order,
            ' hasta antes del orden ', v_destination_order
        )
    );

    COMMIT;

    SELECT id,
           reservation_code,
           v_qr_token AS qr_token,
           status,
           origin_stop_order,
           destination_stop_order
      FROM reservations
     WHERE id = v_reservation_id;
END$$

DELIMITER ;
