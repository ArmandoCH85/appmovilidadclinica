-- 0008_trip_stop_departure.up.sql
-- Crea el stored procedure sp_mark_trip_stop_departure, espejo de
-- sp_mark_trip_stop_arrival, que registra la salida del conductor de una
-- parada (trip_stop_time) determinada.

DROP PROCEDURE IF EXISTS sp_mark_trip_stop_departure;

DELIMITER $$

CREATE PROCEDURE sp_mark_trip_stop_departure(
    IN p_trip_stop_time_id BIGINT UNSIGNED,
    IN p_driver_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_assigned_driver_id BIGINT UNSIGNED;
    DECLARE v_trip_status VARCHAR(30);
    DECLARE v_stop_status VARCHAR(20);

    SELECT trip.driver_id, trip.status, stop_time.status
      INTO v_assigned_driver_id, v_trip_status, v_stop_status
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
