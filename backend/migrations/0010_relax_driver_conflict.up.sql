-- =============================================================
-- 0010_relax_driver_conflict.up.sql
-- Relaja las validaciones de conflicto del CONDUCTOR y del VEHICULO
-- en sp_generate_trip_instance. Los conductores rotan y cada
-- servicio se asume operados por buses distintos del mismo tipo.
-- Cada trip_template es un servicio independiente; los horarios
-- pueden solapar entre si sin que el sistema rechace la generacion.
-- =============================================================
DROP PROCEDURE IF EXISTS sp_generate_trip_instance;
DELIMITER $$
CREATE PROCEDURE sp_generate_trip_instance(
    IN p_trip_template_id BIGINT UNSIGNED,
    IN p_service_date DATE,
    IN p_generation_run_id BIGINT UNSIGNED
)
procedure_block: BEGIN
    DECLARE v_done TINYINT DEFAULT 0;
    DECLARE v_template_exists INT DEFAULT 0;
    DECLARE v_existing_trip_id BIGINT UNSIGNED;
    DECLARE v_route_id BIGINT UNSIGNED;
    DECLARE v_calendar_id BIGINT UNSIGNED;
    DECLARE v_vehicle_id BIGINT UNSIGNED;
    DECLARE v_driver_id BIGINT UNSIGNED;
    DECLARE v_departure_time TIME;
    DECLARE v_profile_mode VARCHAR(30);
    DECLARE v_open_days SMALLINT UNSIGNED;
    DECLARE v_booking_close_minutes SMALLINT UNSIGNED;
    DECLARE v_no_show_minutes SMALLINT UNSIGNED;
    DECLARE v_auto_publish TINYINT;
    DECLARE v_template_code VARCHAR(50);
    DECLARE v_driver_role VARCHAR(20);
    DECLARE v_vehicle_active TINYINT;
    DECLARE v_vehicle_capacity SMALLINT UNSIGNED;
    DECLARE v_total_vehicle_seats INT DEFAULT 0;
    DECLARE v_active_vehicle_seats INT DEFAULT 0;
    DECLARE v_route_stop_count INT DEFAULT 0;
    DECLARE v_max_stop_order INT DEFAULT 0;
    DECLARE v_trip_id BIGINT UNSIGNED;
    DECLARE v_trip_code VARCHAR(60);
    DECLARE v_start_at DATETIME;
    DECLARE v_end_at DATETIME;
    DECLARE v_booking_opens_at DATETIME;
    DECLARE v_booking_closes_at DATETIME;
    DECLARE v_route_stop_id BIGINT UNSIGNED;
    DECLARE v_stop_id BIGINT UNSIGNED;
    DECLARE v_stop_order SMALLINT UNSIGNED;
    DECLARE v_dwell_minutes SMALLINT UNSIGNED;
    DECLARE v_prev_route_stop_id BIGINT UNSIGNED;
    DECLARE v_prev_departure_at DATETIME;
    DECLARE v_route_segment_id BIGINT UNSIGNED;
    DECLARE v_profile_id BIGINT UNSIGNED;
    DECLARE v_travel_minutes SMALLINT UNSIGNED;
    DECLARE v_reference_at DATETIME;
    DECLARE v_arrival_at DATETIME;
    DECLARE v_departure_at DATETIME;
    DECLARE v_conflicts INT DEFAULT 0;
    DECLARE v_error_message TEXT;

    DECLARE route_stop_cursor CURSOR FOR
        SELECT id, stop_id, stop_order, dwell_minutes
          FROM route_stops
         WHERE route_id = v_route_id
         ORDER BY stop_order;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 v_error_message = MESSAGE_TEXT;
        ROLLBACK;

        IF p_generation_run_id IS NOT NULL THEN
            UPDATE trip_generation_runs
               SET failed_count = failed_count + 1,
                   error_summary = CONCAT_WS(
                       CHAR(10),
                       error_summary,
                       CONCAT(
                           'Plantilla ', p_trip_template_id,
                           ', fecha ', COALESCE(CAST(p_service_date AS CHAR), 'NULL'),
                           ': ', COALESCE(v_error_message, 'Error no especificado')
                       )
                   )
             WHERE id = p_generation_run_id;
        END IF;

        RESIGNAL;
    END;

    IF p_service_date IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La fecha de servicio es obligatoria';
    END IF;

    START TRANSACTION;

    SELECT COUNT(*)
      INTO v_template_exists
      FROM trip_templates
     WHERE id = p_trip_template_id
       AND active = 1;

    IF v_template_exists = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La plantilla no existe o está inactiva';
    END IF;

    SELECT route_id,
           service_calendar_id,
           departure_time,
           default_vehicle_id,
           default_driver_id,
           profile_reference_mode,
           booking_open_days_before,
           booking_close_minutes_before,
           no_show_tolerance_minutes,
           automatic_publish,
           code
      INTO v_route_id,
           v_calendar_id,
           v_departure_time,
           v_vehicle_id,
           v_driver_id,
           v_profile_mode,
           v_open_days,
           v_booking_close_minutes,
           v_no_show_minutes,
           v_auto_publish,
           v_template_code
      FROM trip_templates
     WHERE id = p_trip_template_id;

    SET v_existing_trip_id = (
        SELECT id
          FROM trip_instances
         WHERE trip_template_id = p_trip_template_id
           AND service_date = p_service_date
         LIMIT 1
    );

    IF v_existing_trip_id IS NOT NULL THEN
        IF p_generation_run_id IS NOT NULL THEN
            UPDATE trip_generation_runs
               SET skipped_count = skipped_count + 1
             WHERE id = p_generation_run_id;
        END IF;

        COMMIT;
        SELECT v_existing_trip_id AS trip_id, 'ALREADY_EXISTS' AS generation_result;
        LEAVE procedure_block;
    END IF;

    IF fn_service_operates(v_calendar_id, p_service_date) = 0 THEN
        IF p_generation_run_id IS NOT NULL THEN
            UPDATE trip_generation_runs
               SET skipped_count = skipped_count + 1
             WHERE id = p_generation_run_id;
        END IF;

        COMMIT;
        SELECT NULL AS trip_id, 'SKIPPED_BY_CALENDAR' AS generation_result;
        LEAVE procedure_block;
    END IF;

    SELECT active, seat_capacity
      INTO v_vehicle_active, v_vehicle_capacity
      FROM vehicles
     WHERE id = v_vehicle_id;

    IF v_vehicle_active IS NULL OR v_vehicle_active = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El vehículo de la plantilla está inactivo';
    END IF;

    SELECT role
      INTO v_driver_role
      FROM users
     WHERE id = v_driver_id
       AND active = 1;

    IF v_driver_role IS NULL OR v_driver_role <> 'DRIVER' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El conductor de la plantilla no es un DRIVER activo';
    END IF;

    SELECT COUNT(*),
           COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0)
      INTO v_total_vehicle_seats, v_active_vehicle_seats
      FROM vehicle_seats
     WHERE vehicle_id = v_vehicle_id
       AND status <> 'RETIRED';

    IF v_total_vehicle_seats <> v_vehicle_capacity THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La capacidad del vehículo no coincide con sus asientos no retirados';
    END IF;

    SELECT COUNT(*), MAX(stop_order)
      INTO v_route_stop_count, v_max_stop_order
      FROM route_stops
     WHERE route_id = v_route_id;

    IF v_route_stop_count < 2 OR v_route_stop_count <> v_max_stop_order THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La ruta debe tener al menos dos paradas con órdenes consecutivos desde 1';
    END IF;

    SET v_start_at = TIMESTAMP(p_service_date, v_departure_time);
    SET v_end_at = v_start_at;
    SET v_booking_opens_at = DATE_SUB(v_start_at, INTERVAL v_open_days DAY);
    SET v_booking_closes_at = DATE_SUB(v_start_at, INTERVAL v_booking_close_minutes MINUTE);
    SET v_trip_code = CONCAT(
        'T-', LEFT(v_template_code, 35), '-', DATE_FORMAT(p_service_date, '%Y%m%d')
    );

    INSERT INTO trip_instances (
        trip_code,
        source,
        trip_template_id,
        generation_run_id,
        route_id,
        service_date,
        scheduled_start_at,
        scheduled_end_at,
        booking_opens_at,
        booking_closes_at,
        vehicle_id,
        driver_id,
        seat_capacity_snapshot,
        no_show_tolerance_minutes,
        status
    ) VALUES (
        v_trip_code,
        'GENERATED',
        p_trip_template_id,
        p_generation_run_id,
        v_route_id,
        p_service_date,
        v_start_at,
        v_end_at,
        v_booking_opens_at,
        v_booking_closes_at,
        v_vehicle_id,
        v_driver_id,
        v_active_vehicle_seats,
        v_no_show_minutes,
        'DRAFT'
    );

    SET v_trip_id = LAST_INSERT_ID();
    SET v_done = 0;
    OPEN route_stop_cursor;

    route_stop_loop: LOOP
        FETCH route_stop_cursor
         INTO v_route_stop_id, v_stop_id, v_stop_order, v_dwell_minutes;

        IF v_done = 1 THEN
            LEAVE route_stop_loop;
        END IF;

        IF v_prev_route_stop_id IS NULL THEN
            SET v_arrival_at = v_start_at;
            SET v_departure_at = v_start_at;
            SET v_profile_id = NULL;
            SET v_travel_minutes = 0;
            SET v_dwell_minutes = 0;
        ELSE
            SET v_route_segment_id = (
                SELECT id
                  FROM route_segments
                 WHERE route_id = v_route_id
                   AND from_route_stop_id = v_prev_route_stop_id
                   AND to_route_stop_id = v_route_stop_id
                   AND active = 1
                 LIMIT 1
            );

            IF v_route_segment_id IS NULL THEN
                SET v_error_message = CONCAT(
                    'Falta el tramo que termina en el orden ', v_stop_order
                );
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error_message;
            END IF;

            IF v_profile_mode = 'TRIP_DEPARTURE' THEN
                SET v_reference_at = v_start_at;
            ELSE
                SET v_reference_at = v_prev_departure_at;
            END IF;

            SET v_profile_id = fn_select_travel_time_profile(
                v_route_segment_id,
                v_reference_at
            );

            SET v_travel_minutes = (
                SELECT travel_minutes
                  FROM route_segment_travel_times
                 WHERE route_segment_id = v_route_segment_id
                   AND profile_id = v_profile_id
                 LIMIT 1
            );

            IF v_profile_id IS NULL OR v_travel_minutes IS NULL THEN
                SET v_error_message = CONCAT(
                    'No existe tiempo aplicable para el tramo ',
                    v_route_segment_id,
                    ' a las ', DATE_FORMAT(v_reference_at, '%Y-%m-%d %H:%i:%s')
                );
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error_message;
            END IF;

            SET v_arrival_at = DATE_ADD(
                v_prev_departure_at,
                INTERVAL v_travel_minutes MINUTE
            );

            IF v_stop_order = v_max_stop_order THEN
                SET v_departure_at = v_arrival_at;
                SET v_dwell_minutes = 0;
            ELSE
                SET v_departure_at = DATE_ADD(
                    v_arrival_at,
                    INTERVAL v_dwell_minutes MINUTE
                );
            END IF;
        END IF;

        INSERT INTO trip_stop_times (
            trip_id,
            route_stop_id,
            stop_id,
            stop_order,
            scheduled_arrival_at,
            scheduled_departure_at,
            applied_profile_id,
            applied_travel_minutes,
            applied_dwell_minutes
        ) VALUES (
            v_trip_id,
            v_route_stop_id,
            v_stop_id,
            v_stop_order,
            v_arrival_at,
            v_departure_at,
            v_profile_id,
            v_travel_minutes,
            v_dwell_minutes
        );

        SET v_prev_route_stop_id = v_route_stop_id;
        SET v_prev_departure_at = v_departure_at;
        SET v_end_at = v_arrival_at;
    END LOOP;

    CLOSE route_stop_cursor;

    INSERT INTO trip_segments (
        trip_id,
        segment_order,
        from_trip_stop_time_id,
        to_trip_stop_time_id
    )
    SELECT current_stop.trip_id,
           current_stop.stop_order,
           current_stop.id,
           next_stop.id
      FROM trip_stop_times current_stop
      JOIN trip_stop_times next_stop
        ON next_stop.trip_id = current_stop.trip_id
       AND next_stop.stop_order = current_stop.stop_order + 1
     WHERE current_stop.trip_id = v_trip_id;

    INSERT INTO trip_seats (
        trip_id,
        vehicle_seat_id,
        seat_number,
        seat_label,
        is_blocked,
        block_reason
    )
    SELECT v_trip_id,
           seat.id,
           seat.seat_number,
           seat.seat_label,
           CASE WHEN seat.status = 'BLOCKED' THEN 1 ELSE 0 END,
           seat.block_reason
      FROM vehicle_seats seat
     WHERE seat.vehicle_id = v_vehicle_id
       AND seat.status <> 'RETIRED'
     ORDER BY seat.seat_number;

    INSERT INTO trip_seat_segments (
        trip_seat_id,
        trip_segment_id,
        state
    )
    SELECT trip_seat.id,
           trip_segment.id,
           CASE WHEN trip_seat.is_blocked = 1 THEN 'BLOCKED' ELSE 'AVAILABLE' END
      FROM trip_seats trip_seat
      JOIN trip_segments trip_segment
        ON trip_segment.trip_id = trip_seat.trip_id
     WHERE trip_seat.trip_id = v_trip_id;

    UPDATE trip_instances
       SET scheduled_end_at = v_end_at
     WHERE id = v_trip_id;


    UPDATE trip_instances
       SET status = CASE WHEN v_auto_publish = 1 THEN 'PUBLISHED' ELSE 'DRAFT' END
     WHERE id = v_trip_id;

    IF p_generation_run_id IS NOT NULL THEN
        UPDATE trip_generation_runs
           SET generated_count = generated_count + 1
         WHERE id = p_generation_run_id;
    END IF;

    COMMIT;
    SELECT v_trip_id AS trip_id, 'GENERATED' AS generation_result;
END$$

DELIMITER ;
