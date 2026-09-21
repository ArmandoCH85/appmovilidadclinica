-- ============================================================================
-- 0024_trip_stop_mark_guards.up.sql
-- Cierra el hueco por el que se pudieron marcar llegadas/salidas en viajes que
-- nunca se iniciaron (status PUBLISHED) y en fechas ajenas al día de servicio.
--
-- Contexto: el reporte "Llegadas por sede/paradero" destapó marcas de llegada
-- fechadas dias antes del service_date del viaje (p.ej. un viaje del 28/09 con
-- "llegada real" del 20/09). El app de conductor muestra el boton "Marcar
-- llegada" en paradas PENDING sin importar el status del viaje, y el SP solo
-- bloqueaba COMPLETED/CANCELLED. Resultado: marcas (y auto-bajadas de
-- reservas) sobre viajes futuros.
--
-- Guard nuevo (mismo en llegada y salida):
--   1. El viaje debe estar iniciado: status IN ('BOARDING','IN_PROGRESS').
--      Un viaje PUBLISHED/DRAFT no puede tener llegadas reales.
--   2. La marca debe caer dentro del dia de servicio del viaje, con 1 dia de
--      holgura para los viajes nocturnos que cruzan medianoche:
--      DATE(marca) BETWEEN service_date AND service_date + 1 dia.
--      (Verificado: service_date == DATE(scheduled_start_at) en todos los
--      viajes, asi que esa ventana es exacta.)
--
-- Se preservan tal cual el resto de validaciones y la logica de auto-bajada
-- (sp_mark_trip_stop_arrival de la 0018 y sp_mark_trip_stop_departure de la
-- 0008), solo se agregan los dos guards.
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
