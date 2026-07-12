-- ============================================================================
-- seed_demo_data.sql
-- Datos de prueba realistas para transporte_corporativo_mvp (clinica).
--
-- Origen: Documentacion/transporte_corporativo_mvp.sql, seccion I (lineas
-- 2213-2601), el dataset de demostracion que el propio autor del esquema
-- escribio para ejercitar el motor completo (generacion de viajes +
-- reservas reales via stored procedures, no bloqueos manuales).
--
-- Requisitos antes de correr esto:
--   1. El backend debe haber arrancado al menos una vez con el fix de
--      migrate.go aplicado (ver commit "fix(migrations): recorta
--      delimitador..."), para que sp_generate_trip_instance,
--      fn_service_operates y sp_confirm_reservation existan.
--   2. Correr con el cliente mysql/mariadb (soporta CALL a stored procedures
--      sin problema), NO via el runner Go:
--        mariadb -u appuser -p transporte_corporativo_mvp < seed_demo_data.sql
--
-- IMPORTANTE: 0001_schema.up.sql hace DROP+CREATE de todas las tablas en
-- CADA arranque del backend (schema "idempotente", sin tabla de control de
-- migraciones). Cualquier restart/rebuild del servicio borra estos datos.
-- Volver a correr este script despues de cada restart.
--
-- Password de los 5 usuarios demo: "password" (hash bcrypt $2y$, compatible
-- con golang.org/x/crypto/bcrypt).
-- ============================================================================

USE transporte_corporativo_mvp;

-- I.1 Puntos fisicos
INSERT INTO transport_stops (
    id, code, name, stop_type, reference_text, active
) VALUES
    (1, 'SEDE_A', 'Sede A', 'SEDE', 'Ingreso principal de la clinica', 1),
    (2, 'PARADERO_1', 'Paradero 1', 'PARADERO', 'Punto registrado manualmente', 1),
    (3, 'PARADERO_2', 'Paradero 2', 'PARADERO', 'Punto registrado manualmente', 1),
    (4, 'PARADERO_3', 'Paradero 3', 'PARADERO', 'Punto registrado manualmente', 1);

-- I.2 Usuarios (1 admin, 1 conductor, 3 trabajadores)
INSERT INTO users (
    id,
    employee_code,
    document_number,
    password_hash,
    full_name,
    role,
    department,
    phone,
    preferred_stop_id,
    driver_license_number,
    driver_license_category,
    driver_license_expires_on,
    active
) VALUES
    (
        1, 'ADM-001', '90000001',
        '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
        'Administrador Demo', 'ADMIN', 'TI', '999000001', NULL,
        NULL, NULL, NULL, 1
    ),
    (
        2, 'CON-001', '90000002',
        '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
        'Conductor Demo', 'DRIVER', 'Transporte', '999000002', NULL,
        'LIC-DEMO-001', 'A-IIb', DATE_ADD(CURDATE(), INTERVAL 2 YEAR), 1
    ),
    (
        3, 'TRA-001', '90000003',
        '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
        'Trabajador Uno', 'WORKER', 'Enfermeria', '999000003', 3,
        NULL, NULL, NULL, 1
    ),
    (
        4, 'TRA-002', '90000004',
        '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
        'Trabajador Dos', 'WORKER', 'Laboratorio', '999000004', 4,
        NULL, NULL, NULL, 1
    ),
    (
        5, 'TRA-003', '90000005',
        '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
        'Trabajador Tres', 'WORKER', 'Administracion', '999000005', 2,
        NULL, NULL, NULL, 1
    );

-- I.3 Vehiculo y asientos fisicos
INSERT INTO vehicles (
    id, internal_code, plate, description, seat_capacity, active
) VALUES
    (1, 'BUS-001', 'ABC-123', 'Bus corporativo de demostracion', 12, 1);

INSERT INTO vehicle_seats (
    id, vehicle_id, seat_number, seat_label, status
) VALUES
    (1, 1, 1, '1', 'ACTIVE'),
    (2, 1, 2, '2', 'ACTIVE'),
    (3, 1, 3, '3', 'ACTIVE'),
    (4, 1, 4, '4', 'ACTIVE'),
    (5, 1, 5, '5', 'ACTIVE'),
    (6, 1, 6, '6', 'ACTIVE'),
    (7, 1, 7, '7', 'ACTIVE'),
    (8, 1, 8, '8', 'ACTIVE'),
    (9, 1, 9, '9', 'ACTIVE'),
    (10, 1, 10, '10', 'ACTIVE'),
    (11, 1, 11, '11', 'ACTIVE'),
    (12, 1, 12, '12', 'ACTIVE');

-- I.4 Rutas direccionales emparejadas (IDA / VUELTA)
INSERT INTO transport_routes (
    id, code, name, direction, paired_route_id, active
) VALUES
    (1, 'RUTA_NORTE_IDA', 'Ruta Norte hacia Sede A', 'IDA', NULL, 1),
    (2, 'RUTA_NORTE_VUELTA', 'Ruta Norte desde Sede A', 'VUELTA', NULL, 1);

UPDATE transport_routes SET paired_route_id = 2 WHERE id = 1;
UPDATE transport_routes SET paired_route_id = 1 WHERE id = 2;

-- IDA: Paradero 3 -> Paradero 2 -> Paradero 1 -> Sede A
INSERT INTO route_stops (
    id, route_id, stop_id, stop_order, dwell_minutes,
    pickup_allowed, dropoff_allowed
) VALUES
    (1, 1, 4, 1, 0, 1, 0),
    (2, 1, 3, 2, 2, 1, 0),
    (3, 1, 2, 3, 2, 1, 0),
    (4, 1, 1, 4, 0, 0, 1);

-- VUELTA: Sede A -> Paradero 1 -> Paradero 2 -> Paradero 3
INSERT INTO route_stops (
    id, route_id, stop_id, stop_order, dwell_minutes,
    pickup_allowed, dropoff_allowed
) VALUES
    (5, 2, 1, 1, 0, 1, 0),
    (6, 2, 2, 2, 2, 0, 1),
    (7, 2, 3, 3, 2, 0, 1),
    (8, 2, 4, 4, 0, 0, 1);

INSERT INTO route_segments (
    id, route_id, segment_order, from_route_stop_id, to_route_stop_id, active
) VALUES
    (1, 1, 1, 1, 2, 1),
    (2, 1, 2, 2, 3, 1),
    (3, 1, 3, 3, 4, 1),
    (4, 2, 1, 5, 6, 1),
    (5, 2, 2, 6, 7, 1),
    (6, 2, 3, 7, 8, 1);

-- I.5 Perfiles y matriz manual de tiempos
INSERT INTO travel_time_profiles (
    id, code, name, start_time, end_time, is_all_day,
    monday, tuesday, wednesday, thursday, friday, saturday, sunday,
    priority, is_default, active
) VALUES
    (
        1, 'BASE_TODO_DIA', 'Tiempo base para cualquier dia', NULL, NULL, 1,
        1, 1, 1, 1, 1, 1, 1,
        0, 1, 1
    ),
    (
        2, 'PUNTA_MANANA_LV', 'Hora punta de manana - lunes a viernes',
        '06:00:00', '09:00:00', 0,
        1, 1, 1, 1, 1, 0, 0,
        100, 0, 1
    ),
    (
        3, 'PUNTA_TARDE_LV', 'Hora punta de tarde - lunes a viernes',
        '17:00:00', '20:00:00', 0,
        1, 1, 1, 1, 1, 0, 0,
        100, 0, 1
    );

-- Cada tramo tiene siempre un valor base y valores especializados.
INSERT INTO route_segment_travel_times (
    route_segment_id, profile_id, travel_minutes, notes
) VALUES
    (1, 1, 10, 'IDA: Paradero 3 a Paradero 2 - base'),
    (1, 2, 14, 'IDA: Paradero 3 a Paradero 2 - punta manana'),
    (1, 3, 12, 'IDA: Paradero 3 a Paradero 2 - punta tarde'),
    (2, 1, 9,  'IDA: Paradero 2 a Paradero 1 - base'),
    (2, 2, 13, 'IDA: Paradero 2 a Paradero 1 - punta manana'),
    (2, 3, 11, 'IDA: Paradero 2 a Paradero 1 - punta tarde'),
    (3, 1, 15, 'IDA: Paradero 1 a Sede A - base'),
    (3, 2, 22, 'IDA: Paradero 1 a Sede A - punta manana'),
    (3, 3, 18, 'IDA: Paradero 1 a Sede A - punta tarde'),
    (4, 1, 15, 'VUELTA: Sede A a Paradero 1 - base'),
    (4, 2, 18, 'VUELTA: Sede A a Paradero 1 - punta manana'),
    (4, 3, 24, 'VUELTA: Sede A a Paradero 1 - punta tarde'),
    (5, 1, 9,  'VUELTA: Paradero 1 a Paradero 2 - base'),
    (5, 2, 11, 'VUELTA: Paradero 1 a Paradero 2 - punta manana'),
    (5, 3, 13, 'VUELTA: Paradero 1 a Paradero 2 - punta tarde'),
    (6, 1, 10, 'VUELTA: Paradero 2 a Paradero 3 - base'),
    (6, 2, 12, 'VUELTA: Paradero 2 a Paradero 3 - punta manana'),
    (6, 3, 15, 'VUELTA: Paradero 2 a Paradero 3 - punta tarde');

-- I.6 Calendario y excepcion
SET @calendar_valid_from = DATE_SUB(CURDATE(), INTERVAL 1 YEAR);
SET @calendar_valid_until = DATE_ADD(CURDATE(), INTERVAL 2 YEAR);
SET @candidate_service_date = DATE_ADD(CURDATE(), INTERVAL 3 DAY);
SET @sample_service_date = CASE DAYOFWEEK(@candidate_service_date)
    WHEN 7 THEN DATE_ADD(@candidate_service_date, INTERVAL 2 DAY)
    WHEN 1 THEN DATE_ADD(@candidate_service_date, INTERVAL 1 DAY)
    ELSE @candidate_service_date
END;

INSERT INTO service_calendars (
    id, code, name, valid_from, valid_until,
    monday, tuesday, wednesday, thursday, friday, saturday, sunday, active
) VALUES (
    1, 'LABORABLE_LV', 'Servicio regular de lunes a viernes',
    @calendar_valid_from, @calendar_valid_until,
    1, 1, 1, 1, 1, 0, 0, 1
);

INSERT INTO service_calendar_exceptions (
    id, calendar_id, exception_date, operation, reason
) VALUES (
    1,
    1,
    DATE_ADD(@sample_service_date, INTERVAL 14 DAY),
    'REMOVE',
    'Fecha no operativa de demostracion; reemplazar por el calendario real'
);

-- I.7 Plantillas recurrentes
INSERT INTO trip_templates (
    id,
    code,
    name,
    route_id,
    service_calendar_id,
    departure_time,
    default_vehicle_id,
    default_driver_id,
    profile_reference_mode,
    booking_open_days_before,
    booking_close_minutes_before,
    no_show_tolerance_minutes,
    automatic_publish,
    active
) VALUES
    (
        1, 'NORTE_IDA_0800', 'Ruta Norte IDA de las 08:00',
        1, 1, '08:00:00', 1, 2, 'SEGMENT_DEPARTURE',
        14, 30, 5, 1, 1
    ),
    (
        2, 'NORTE_VUELTA_1800', 'Ruta Norte VUELTA de las 18:00',
        2, 1, '18:00:00', 1, 2, 'SEGMENT_DEPARTURE',
        14, 30, 5, 1, 1
    );

-- I.8 Ejecucion del motor y materializacion de dos viajes futuros
INSERT INTO trip_generation_runs (
    id, window_start, window_end, status, triggered_by_user_id
) VALUES (
    1, @sample_service_date, @sample_service_date, 'RUNNING', 1
);

CALL sp_generate_trip_instance(1, @sample_service_date, 1);
CALL sp_generate_trip_instance(2, @sample_service_date, 1);

UPDATE trip_generation_runs
   SET status = CASE
                    WHEN failed_count > 0 THEN 'COMPLETED_WITH_ERRORS'
                    ELSE 'COMPLETED'
                END,
       finished_at = CURRENT_TIMESTAMP
 WHERE id = 1;

-- I.9 Reservas reales de demostracion creadas por el mismo procedimiento que
-- usa la aplicacion (sp_confirm_reservation). No se insertan bloqueos a mano.
SET @round_trip_group = UUID();

SET @ida_trip_id = (
    SELECT id
      FROM trip_instances
     WHERE trip_template_id = 1
       AND service_date = @sample_service_date
);

SET @vuelta_trip_id = (
    SELECT id
      FROM trip_instances
     WHERE trip_template_id = 2
       AND service_date = @sample_service_date
);

-- VUELTA: asiento 5 desde Sede A (orden 1) hasta Paradero 2 (orden 3).
-- Se bloquean los segmentos 1 y 2; el segmento 3 queda AVAILABLE.
SET @vuelta_seat_5 = (
    SELECT id FROM trip_seats
     WHERE trip_id = @vuelta_trip_id AND seat_number = 5
);
SET @vuelta_origin_order_1 = (
    SELECT id FROM trip_stop_times
     WHERE trip_id = @vuelta_trip_id AND stop_order = 1
);
SET @vuelta_destination_order_3 = (
    SELECT id FROM trip_stop_times
     WHERE trip_id = @vuelta_trip_id AND stop_order = 3
);

CALL sp_confirm_reservation(
    @vuelta_trip_id,
    3,
    @vuelta_seat_5,
    @vuelta_origin_order_1,
    @vuelta_destination_order_3,
    @round_trip_group
);

-- IDA: asiento 8 desde Paradero 2 (orden 2) hasta Sede A (orden 4).
SET @ida_seat_8 = (
    SELECT id FROM trip_seats
     WHERE trip_id = @ida_trip_id AND seat_number = 8
);
SET @ida_origin_order_2 = (
    SELECT id FROM trip_stop_times
     WHERE trip_id = @ida_trip_id AND stop_order = 2
);
SET @ida_destination_order_4 = (
    SELECT id FROM trip_stop_times
     WHERE trip_id = @ida_trip_id AND stop_order = 4
);

CALL sp_confirm_reservation(
    @ida_trip_id,
    3,
    @ida_seat_8,
    @ida_origin_order_2,
    @ida_destination_order_4,
    @round_trip_group
);

-- I.10 Incidencia cerrada de ejemplo
INSERT INTO trip_incidents (
    trip_id,
    reported_by_user_id,
    incident_type,
    description,
    status,
    reported_at,
    resolved_at,
    resolution_notes
) VALUES (
    @vuelta_trip_id,
    2,
    'OTHER',
    'Registro de demostracion para validar la bitacora de incidencias',
    'RESOLVED',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'Registro de prueba; no corresponde a una incidencia real'
);

-- ============================================================================
-- J. VERIFICACION (correlas a mano despues para confirmar que cargo bien)
-- ============================================================================

-- J.1 Todos los objetos principales deben tener datos.
SELECT 'transport_stops' AS table_name, COUNT(*) AS row_count FROM transport_stops
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'vehicles', COUNT(*) FROM vehicles
UNION ALL SELECT 'transport_routes', COUNT(*) FROM transport_routes
UNION ALL SELECT 'vehicle_seats', COUNT(*) FROM vehicle_seats
UNION ALL SELECT 'route_stops', COUNT(*) FROM route_stops
UNION ALL SELECT 'route_segments', COUNT(*) FROM route_segments
UNION ALL SELECT 'travel_time_profiles', COUNT(*) FROM travel_time_profiles
UNION ALL SELECT 'route_segment_travel_times', COUNT(*) FROM route_segment_travel_times
UNION ALL SELECT 'service_calendars', COUNT(*) FROM service_calendars
UNION ALL SELECT 'service_calendar_exceptions', COUNT(*) FROM service_calendar_exceptions
UNION ALL SELECT 'trip_templates', COUNT(*) FROM trip_templates
UNION ALL SELECT 'trip_generation_runs', COUNT(*) FROM trip_generation_runs
UNION ALL SELECT 'trip_instances', COUNT(*) FROM trip_instances
UNION ALL SELECT 'trip_stop_times', COUNT(*) FROM trip_stop_times
UNION ALL SELECT 'trip_segments', COUNT(*) FROM trip_segments
UNION ALL SELECT 'trip_seats', COUNT(*) FROM trip_seats
UNION ALL SELECT 'reservations', COUNT(*) FROM reservations
UNION ALL SELECT 'trip_seat_segments', COUNT(*) FROM trip_seat_segments
UNION ALL SELECT 'reservation_segments', COUNT(*) FROM reservation_segments
UNION ALL SELECT 'reservation_events', COUNT(*) FROM reservation_events
UNION ALL SELECT 'trip_incidents', COUNT(*) FROM trip_incidents;

-- J.2 No deben existir conflictos de vehiculo ni conductor.
SELECT * FROM vw_schedule_conflicts;
