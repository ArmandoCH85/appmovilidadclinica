-- ============================================================================
-- 0019_guest_registration_guard.up.sql
-- Valida el estado del viaje al registrar un ocupante invitado.
--
-- Bug: sp_register_guest_occupant (0013) validaba asiento, tramo valido y
-- nombre duplicado, pero NO el estado del viaje. Se podian registrar
-- invitados en viajes ya COMPLETED o CANCELLED y quedaban colgados: status
-- BOARDED para siempre y asiento OCCUPIED para siempre (la 0018 y su backfill
-- arreglan a los que ya existian, no el origen).
-- Evidencia en produccion: el invitado id 4 del viaje 14 se registro a las
-- 12:29:09, con el viaje ya COMPLETED desde las 12:28:07.
--
-- Ventana permitida: PUBLISHED (el viaje ya es visible y se puede reservar),
-- BOARDING e IN_PROGRESS (en curso). Se rechazan DRAFT (no publicado),
-- COMPLETED y CANCELLED.
--
-- Cuerpo base: 0013_guest_occupants.up.sql, con el guard agregado despues del
-- START TRANSACTION (para que el FOR UPDATE quede dentro de la transaccion).
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_register_guest_occupant;

DELIMITER $$

CREATE PROCEDURE sp_register_guest_occupant(
    IN p_trip_id BIGINT UNSIGNED,
    IN p_trip_seat_id BIGINT UNSIGNED,
    IN p_origin_trip_stop_time_id BIGINT UNSIGNED,
    IN p_destination_trip_stop_time_id BIGINT UNSIGNED,
    IN p_first_name VARCHAR(100),
    IN p_last_name VARCHAR(100),
    IN p_registered_by_user_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_origin_order SMALLINT UNSIGNED;
    DECLARE v_destination_order SMALLINT UNSIGNED;
    DECLARE v_expected_segments INT;
    DECLARE v_inventory_rows INT;
    DECLARE v_conflicting_segments INT;
    DECLARE v_duplicate INT;
    DECLARE v_guest_id BIGINT UNSIGNED;
    DECLARE v_trip_status VARCHAR(30);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- 0019: un invitado solo puede registrarse en un viaje que admite
    -- abordajes. Antes se validaba asiento, tramo y duplicado, pero NO el
    -- estado del viaje: se podian cargar invitados en viajes ya COMPLETED o
    -- CANCELLED y quedaban colgados (ver 0018 y el backfill posterior).
    SELECT trip.status
      INTO v_trip_status
      FROM trip_instances trip
     WHERE trip.id = p_trip_id
     FOR UPDATE;

    IF v_trip_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no existe';
    END IF;

    IF v_trip_status IN ('COMPLETED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje ya esta cerrado y no admite invitados';
    END IF;

    IF v_trip_status = 'DRAFT' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no esta publicado';
    END IF;


    IF NOT EXISTS (
        SELECT 1 FROM trip_seats
         WHERE id = p_trip_seat_id
           AND trip_id = p_trip_id
           AND is_blocked = 0
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El asiento no pertenece al viaje o está bloqueado';
    END IF;

    SELECT origin_time.stop_order, destination_time.stop_order
      INTO v_origin_order, v_destination_order
      FROM trip_stop_times origin_time
      JOIN trip_stop_times destination_time
        ON destination_time.id = p_destination_trip_stop_time_id
       AND destination_time.trip_id = origin_time.trip_id
     WHERE origin_time.id = p_origin_trip_stop_time_id
       AND origin_time.trip_id = p_trip_id;

    IF v_origin_order IS NULL
       OR v_destination_order IS NULL
       OR v_origin_order >= v_destination_order THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El origen y destino no forman un tramo válido';
    END IF;

    SELECT COUNT(*)
      INTO v_duplicate
      FROM guest_occupants
     WHERE trip_id = p_trip_id
       AND LOWER(first_name) = LOWER(p_first_name)
       AND LOWER(last_name) = LOWER(p_last_name);

    IF v_duplicate > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Ese pasajero ya está registrado en este viaje';
    END IF;

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

    SET v_expected_segments = v_destination_order - v_origin_order;

    IF v_inventory_rows <> v_expected_segments THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El inventario por segmentos del asiento está incompleto';
    END IF;

    IF COALESCE(v_conflicting_segments, 0) > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El asiento ya está ocupado en uno o más tramos solicitados';
    END IF;

    INSERT INTO guest_occupants (
        trip_id, trip_seat_id,
        origin_trip_stop_time_id, destination_trip_stop_time_id,
        origin_stop_order, destination_stop_order,
        first_name, last_name, registered_by_user_id, status
    ) VALUES (
        p_trip_id, p_trip_seat_id,
        p_origin_trip_stop_time_id, p_destination_trip_stop_time_id,
        v_origin_order, v_destination_order,
        p_first_name, p_last_name, p_registered_by_user_id, 'BOARDED'
    );

    SET v_guest_id = LAST_INSERT_ID();

    UPDATE trip_seat_segments inventory
    JOIN trip_segments segment
      ON segment.id = inventory.trip_segment_id
     SET inventory.state = 'OCCUPIED',
         inventory.guest_occupant_id = v_guest_id,
         inventory.reserved_at = CURRENT_TIMESTAMP,
         inventory.released_at = NULL
   WHERE inventory.trip_seat_id = p_trip_seat_id
     AND segment.trip_id = p_trip_id
     AND segment.segment_order >= v_origin_order
     AND segment.segment_order < v_destination_order;

    COMMIT;

    SELECT v_guest_id AS id;
END$$

DELIMITER ;
