-- ============================================================================
-- seed_test_data.sql (v2 — orden corregido)
-- Data simulada para probar todos los reportes del admin.
-- Requisito: haber corrido antes seed_demo_data.sql (necesita admin, driver,
-- vehiculo, rutas, plantillas, calendario y stops ya cargados).
--
-- Que agrega:
--   * 10 usuarios WORKER nuevos (perfil pasajero) con password "12345678"
--   * ~26 viajes nuevos en multiples fechas (pasado/hoy/futuro) con
--     statuses variados (COMPLETED / IN_PROGRESS / PUBLISHED / CANCELLED)
--     y tiempos reales para los ya completados
--   * ~30 reservas hechas por los 10 nuevos workers en distintos estados
--     (CONFIRMED, BOARDED, COMPLETED, NO_SHOW, CANCELLED)
--   * Eventos de auditoria en reservation_events para cada reserva
--   * Incidencias de viaje (tipos y estados variados)
--   * 2 viajes extra sobre la misma fecha para que vw_schedule_conflicts
--     tenga al menos un caso (trip_template_id=NULL para evitar el UNIQUE)
--
-- NO es idempotente: si lo corres 2 veces duplica usuarios (PK) y reservas.
-- Para volver a empezar desde cero: parar backend, aplicar seed_demo_data.sql
-- y este seed_test_data.sql en orden.
--
-- NOTA: sp_confirm_reservation rechaza reservas en viajes pasados porque la
-- ventana de booking esta cerrada. Por eso las reservas pasadas se INSERTAN
-- directamente con el status final deseado (COMPLETED/NO_SHOW/CANCELLED) +
-- reservation_segments (RELEASED) + reservation_events. Los trip_seat_segments
-- quedan en AVAILABLE (los pasados ya fueron liberados al completarse).
-- ============================================================================

USE transporte_corporativo_mvp;

-- ----------------------------------------------------------------------------
-- 1. Diez usuarios WORKER nuevos (id 6-15)
-- ----------------------------------------------------------------------------
INSERT INTO users (
    id, employee_code, document_number, password_hash,
    full_name, role, department, phone, preferred_stop_id,
    driver_license_number, driver_license_category, driver_license_expires_on,
    active
) VALUES
    ( 6, 'PAX-006', '90000006', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Ana Beltran', 'WORKER', 'Enfermeria', '999000006', 2, NULL, NULL, NULL, 1),
    ( 7, 'PAX-007', '90000007', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Bruno Costa',  'WORKER', 'Enfermeria', '999000007', 3, NULL, NULL, NULL, 1),
    ( 8, 'PAX-008', '90000008', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Carla Dominguez', 'WORKER', 'Laboratorio', '999000008', 4, NULL, NULL, NULL, 1),
    ( 9, 'PAX-009', '90000009', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Diego Fernandez', 'WORKER', 'Administracion', '999000009', 2, NULL, NULL, NULL, 1),
    (10, 'PAX-010', '90000010', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Elena Guerrero',  'WORKER', 'Administracion', '999000010', 3, NULL, NULL, NULL, 1),
    (11, 'PAX-011', '90000011', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Felipe Herrera',  'WORKER', 'Logistica',     '999000011', 4, NULL, NULL, NULL, 1),
    (12, 'PAX-012', '90000012', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Gabriela Ito',    'WORKER', 'Logistica',     '999000012', 2, NULL, NULL, NULL, 1),
    (13, 'PAX-013', '90000013', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Hugo Jaramillo', 'WORKER', 'RRHH',          '999000013', 3, NULL, NULL, NULL, 1),
    (14, 'PAX-014', '90000014', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Isabel Kippes',  'WORKER', 'RRHH',          '999000014', 4, NULL, NULL, NULL, 1),
    (15, 'PAX-015', '90000015', '$2a$10$aB19H7suPqazCiMetk65QedII869tJtAh1QILyIlKqDfHgfrCh1pi', 'Pasajero Javier Leon',    'WORKER', 'Mantenimiento', '999000015', 2, NULL, NULL, NULL, 1);

-- ----------------------------------------------------------------------------
-- 2. Generar 26 viajes via SP. Cada CALL crea trip_instance + trip_stop_times
--    + trip_segments + trip_seats + trip_seat_segments (AVAILABLE).
--    Pasado: 7 fechas x 2 rutas = 14.  Hoy: 1 x 2 = 2.  Futuro: 5 x 2 = 10.
-- ----------------------------------------------------------------------------
CALL sp_generate_trip_instance(1, '2026-08-03', 1);
CALL sp_generate_trip_instance(2, '2026-08-03', 1);
CALL sp_generate_trip_instance(1, '2026-08-04', 1);
CALL sp_generate_trip_instance(2, '2026-08-04', 1);
CALL sp_generate_trip_instance(1, '2026-08-05', 1);
CALL sp_generate_trip_instance(2, '2026-08-05', 1);
CALL sp_generate_trip_instance(1, '2026-08-06', 1);
CALL sp_generate_trip_instance(2, '2026-08-06', 1);
CALL sp_generate_trip_instance(1, '2026-08-07', 1);
CALL sp_generate_trip_instance(2, '2026-08-07', 1);
CALL sp_generate_trip_instance(1, '2026-08-10', 1);
CALL sp_generate_trip_instance(2, '2026-08-10', 1);
CALL sp_generate_trip_instance(1, '2026-08-11', 1);
CALL sp_generate_trip_instance(2, '2026-08-11', 1);
CALL sp_generate_trip_instance(1, '2026-08-24', 1);
CALL sp_generate_trip_instance(2, '2026-08-24', 1);
CALL sp_generate_trip_instance(1, '2026-08-25', 1);
CALL sp_generate_trip_instance(2, '2026-08-25', 1);
CALL sp_generate_trip_instance(1, '2026-08-26', 1);
CALL sp_generate_trip_instance(2, '2026-08-26', 1);
CALL sp_generate_trip_instance(1, '2026-08-28', 1);
CALL sp_generate_trip_instance(2, '2026-08-28', 1);
CALL sp_generate_trip_instance(1, '2026-08-31', 1);
CALL sp_generate_trip_instance(2, '2026-08-31', 1);
CALL sp_generate_trip_instance(1, '2026-09-01', 1);
CALL sp_generate_trip_instance(2, '2026-09-01', 1);

-- ----------------------------------------------------------------------------
-- 3. Reservas en viajes FUTUROS via sp_confirm_reservation.
--    Los viajes futuros siguen en PUBLISHED + booking window vigente, asi que
--    el SP acepta. Vamos a reservar 2-3 asientos por cada worker nuevo en
--    viajes futuros para que la app tenga data viva.
-- ----------------------------------------------------------------------------

-- PAX-006
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-26'), 6,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-26') AND seat_number=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-26') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-26') AND stop_order=4),
    UUID());
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-28'), 6,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-28') AND seat_number=2),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-28') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-28') AND stop_order=3),
    UUID());

-- PAX-007
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-31'), 7,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-31') AND seat_number=3),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-31') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-31') AND stop_order=4),
    UUID());

-- PAX-008
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-25'), 8,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-25') AND seat_number=4),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-25') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-25') AND stop_order=3),
    UUID());

-- PAX-009
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-09-01'), 9,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-09-01') AND seat_number=5),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-09-01') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-09-01') AND stop_order=4),
    UUID());

-- PAX-010
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-28'), 10,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-28') AND seat_number=6),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-28') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-28') AND stop_order=4),
    UUID());

-- PAX-011
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-25'), 11,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-25') AND seat_number=7),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-25') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-25') AND stop_order=4),
    UUID());

-- PAX-012
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-26'), 12,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-26') AND seat_number=8),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-26') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-26') AND stop_order=3),
    UUID());

-- PAX-013
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-31'), 13,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-31') AND seat_number=9),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-31') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-31') AND stop_order=3),
    UUID());

-- PAX-014
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-28'), 14,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-28') AND seat_number=10),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-28') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-28') AND stop_order=3),
    UUID());

-- PAX-015
CALL sp_confirm_reservation((SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-09-01'), 15,
    (SELECT id FROM trip_seats WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-09-01') AND seat_number=11),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-09-01') AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=(SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-09-01') AND stop_order=3),
    UUID());

-- ----------------------------------------------------------------------------
-- 4. Reservas en viajes PASADOS via INSERT directo.
--    El SP rechaza reservas pasadas (booking window cerrada). Insertamos con
--    el status final deseado y creamos reservation_segments (RELEASED) +
--    reservation_events para mantener la coherencia del modelo de datos.
--    Los trip_seat_segments quedan en AVAILABLE (los viajes ya pasaron).
--
--    Patrones (por worker nuevo):
--      - 2 reservas COMPLETED (ida + vuelta) en un dia pasado
--      - 1 reserva NO_SHOW (no se presento)
--      - 1 reserva CANCELLED (cancelo voluntariamente)
--    Total: ~30 reservas insertadas.
-- ----------------------------------------------------------------------------

-- Helper: variables para los trip_ids de fechas pasadas (los necesitamos para los INSERTs).
SET @t_ida_0803  = (SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-03');
SET @t_vta_0803  = (SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-03');
SET @t_ida_0804  = (SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-04');
SET @t_vta_0804  = (SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-04');
SET @t_ida_0805  = (SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-05');
SET @t_vta_0805  = (SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-05');
SET @t_ida_0806  = (SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-06');
SET @t_vta_0806  = (SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-06');
SET @t_ida_0807  = (SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-07');
SET @t_vta_0807  = (SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-07');
SET @t_ida_0810  = (SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-10');
SET @t_vta_0810  = (SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-10');
SET @t_ida_0811  = (SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-11');
SET @t_vta_0811  = (SELECT id FROM trip_instances WHERE trip_template_id=2 AND service_date='2026-08-11');

-- Macros: para cada combinacion (trip_id, worker_id, seat_number, origin_order, dest_order, status)
-- generamos: reservation + reservation_segments (RELEASED) + reservation_events.
-- Lo hago inline para mantenerlo en un solo archivo SQL.

-- PAX-006 (Ana) - ida+vuelta 08-04 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES (
    'R-202608-006-1', SHA2(UUID(), 256), UUID(),
    @t_ida_0804, 6,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0804 AND seat_number=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0804 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0804 AND stop_order=4),
    1, 4, 'COMPLETED', 6,
    TIMESTAMP('2026-08-04','07:30:00'),
    TIMESTAMP('2026-08-04','08:05:00'),
    TIMESTAMP('2026-08-04','08:35:00')
);
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-04','08:35:00')
  FROM trip_segments ts WHERE ts.trip_id = @t_ida_0804;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 6, TIMESTAMP('2026-08-04','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-04','08:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-04','08:35:00'), 'Pasajero descendio del vehiculo');

INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES (
    'R-202608-006-2', SHA2(UUID(), 256), UUID(),
    @t_vta_0804, 6,
    (SELECT id FROM trip_seats WHERE trip_id=@t_vta_0804 AND seat_number=2),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0804 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0804 AND stop_order=3),
    1, 3, 'COMPLETED', 6,
    TIMESTAMP('2026-08-04','09:00:00'),
    TIMESTAMP('2026-08-04','18:05:00'),
    TIMESTAMP('2026-08-04','18:34:00')
);
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-04','18:34:00')
  FROM trip_segments ts WHERE ts.trip_id = @t_vta_0804;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 6, TIMESTAMP('2026-08-04','09:00:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-04','18:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-04','18:34:00'), 'Pasajero descendio del vehiculo');

-- PAX-007 (Bruno) - ida 08-03 NO_SHOW, vuelta 08-03 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, no_show_at)
VALUES (
    'R-202608-007-NS', SHA2(UUID(), 256), UUID(),
    @t_ida_0803, 7,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0803 AND seat_number=5),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0803 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0803 AND stop_order=4),
    1, 4, 'NO_SHOW', 7,
    TIMESTAMP('2026-08-03','07:30:00'),
    TIMESTAMP('2026-08-03','08:30:00')
);
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-03','08:30:00')
  FROM trip_segments ts WHERE ts.trip_id = @t_ida_0803;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 7, TIMESTAMP('2026-08-03','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'NO_SHOW',   2, TIMESTAMP('2026-08-03','08:30:00'), 'Pasajero no se presento al embarque');

INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES (
    'R-202608-007-V', SHA2(UUID(), 256), UUID(),
    @t_vta_0803, 7,
    (SELECT id FROM trip_seats WHERE trip_id=@t_vta_0803 AND seat_number=6),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0803 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0803 AND stop_order=3),
    1, 3, 'COMPLETED', 7,
    TIMESTAMP('2026-08-03','09:00:00'),
    TIMESTAMP('2026-08-03','18:05:00'),
    TIMESTAMP('2026-08-03','18:34:00')
);
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-03','18:34:00')
  FROM trip_segments ts WHERE ts.trip_id = @t_vta_0803;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 7, TIMESTAMP('2026-08-03','09:00:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-03','18:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-03','18:34:00'), 'Pasajero descendio del vehiculo');

-- PAX-008 (Carla) - ida+vuelta 08-06 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES (
    'R-202608-008-1', SHA2(UUID(), 256), UUID(),
    @t_ida_0806, 8,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0806 AND seat_number=9),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0806 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0806 AND stop_order=4),
    1, 4, 'COMPLETED', 8,
    TIMESTAMP('2026-08-06','07:30:00'),
    TIMESTAMP('2026-08-06','08:05:00'),
    TIMESTAMP('2026-08-06','08:35:00')
);
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-06','08:35:00') FROM trip_segments ts WHERE ts.trip_id = @t_ida_0806;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 8, TIMESTAMP('2026-08-06','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-06','08:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-06','08:35:00'), 'Pasajero descendio del vehiculo');

INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES (
    'R-202608-008-2', SHA2(UUID(), 256), UUID(),
    @t_vta_0806, 8,
    (SELECT id FROM trip_seats WHERE trip_id=@t_vta_0806 AND seat_number=10),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0806 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0806 AND stop_order=3),
    1, 3, 'COMPLETED', 8,
    TIMESTAMP('2026-08-06','09:00:00'),
    TIMESTAMP('2026-08-06','18:05:00'),
    TIMESTAMP('2026-08-06','18:34:00')
);
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-06','18:34:00') FROM trip_segments ts WHERE ts.trip_id = @t_vta_0806;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 8, TIMESTAMP('2026-08-06','09:00:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-06','18:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-06','18:34:00'), 'Pasajero descendio del vehiculo');

-- PAX-009 (Diego) - ida+vuelta 08-07 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES (
    'R-202608-009-1', SHA2(UUID(), 256), UUID(),
    @t_ida_0807, 9,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0807 AND seat_number=12),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0807 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0807 AND stop_order=4),
    1, 4, 'COMPLETED', 9,
    TIMESTAMP('2026-08-07','07:30:00'),
    TIMESTAMP('2026-08-07','08:05:00'),
    TIMESTAMP('2026-08-07','08:35:00')
);
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-07','08:35:00') FROM trip_segments ts WHERE ts.trip_id = @t_ida_0807;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 9, TIMESTAMP('2026-08-07','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-07','08:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-07','08:35:00'), 'Pasajero descendio del vehiculo');

INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES (
    'R-202608-009-2', SHA2(UUID(), 256), UUID(),
    @t_vta_0807, 9,
    (SELECT id FROM trip_seats WHERE trip_id=@t_vta_0807 AND seat_number=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0807 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0807 AND stop_order=3),
    1, 3, 'COMPLETED', 9,
    TIMESTAMP('2026-08-07','09:00:00'),
    TIMESTAMP('2026-08-07','18:05:00'),
    TIMESTAMP('2026-08-07','18:34:00')
);
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-07','18:34:00') FROM trip_segments ts WHERE ts.trip_id = @t_vta_0807;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 9, TIMESTAMP('2026-08-07','09:00:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-07','18:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-07','18:34:00'), 'Pasajero descendio del vehiculo');

-- PAX-010 (Elena) - ida 08-10 COMPLETED, vuelta 08-11 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES ('R-202608-010-1', SHA2(UUID(), 256), UUID(),
    @t_ida_0810, 10,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0810 AND seat_number=3),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0810 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0810 AND stop_order=4),
    1, 4, 'COMPLETED', 10,
    TIMESTAMP('2026-08-10','07:30:00'), TIMESTAMP('2026-08-10','08:05:00'), TIMESTAMP('2026-08-10','08:35:00'));
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-10','08:35:00') FROM trip_segments ts WHERE ts.trip_id = @t_ida_0810;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 10, TIMESTAMP('2026-08-10','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-10','08:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-10','08:35:00'), 'Pasajero descendio del vehiculo');

INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES ('R-202608-010-2', SHA2(UUID(), 256), UUID(),
    @t_vta_0811, 10,
    (SELECT id FROM trip_seats WHERE trip_id=@t_vta_0811 AND seat_number=4),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0811 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0811 AND stop_order=3),
    1, 3, 'COMPLETED', 10,
    TIMESTAMP('2026-08-11','09:00:00'), TIMESTAMP('2026-08-11','18:05:00'), TIMESTAMP('2026-08-11','18:34:00'));
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-11','18:34:00') FROM trip_segments ts WHERE ts.trip_id = @t_vta_0811;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 10, TIMESTAMP('2026-08-11','09:00:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-11','18:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-11','18:34:00'), 'Pasajero descendio del vehiculo');

-- PAX-011 (Felipe) - vuelta 08-04 CANCELLED, ida 08-05 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at)
VALUES ('R-202608-011-C', SHA2(UUID(), 256), UUID(),
    @t_vta_0804, 11,
    (SELECT id FROM trip_seats WHERE trip_id=@t_vta_0804 AND seat_number=5),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0804 AND stop_order=1),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_vta_0804 AND stop_order=3),
    1, 3, 'CANCELLED', 11,
    TIMESTAMP('2026-08-04','09:00:00'));
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-04','10:00:00') FROM trip_segments ts WHERE ts.trip_id = @t_vta_0804;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 11, TIMESTAMP('2026-08-04','09:00:00'), 'Reserva creada por el usuario'),
    (@r, 'CANCELLED', 11, TIMESTAMP('2026-08-04','10:00:00'), 'Reserva cancelada por el usuario');

INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES ('R-202608-011-1', SHA2(UUID(), 256), UUID(),
    @t_ida_0805, 11,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0805 AND seat_number=6),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0805 AND stop_order=2),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0805 AND stop_order=4),
    2, 4, 'COMPLETED', 11,
    TIMESTAMP('2026-08-05','07:30:00'), TIMESTAMP('2026-08-05','08:05:00'), TIMESTAMP('2026-08-05','08:35:00'));
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-05','08:35:00') FROM trip_segments ts WHERE ts.trip_id = @t_ida_0805;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 11, TIMESTAMP('2026-08-05','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-05','08:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-05','08:35:00'), 'Pasajero descendio del vehiculo');

-- PAX-012 (Gabriela) - ida 08-06 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES ('R-202608-012-1', SHA2(UUID(), 256), UUID(),
    @t_ida_0806, 12,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0806 AND seat_number=8),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0806 AND stop_order=2),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0806 AND stop_order=4),
    2, 4, 'COMPLETED', 12,
    TIMESTAMP('2026-08-06','07:30:00'), TIMESTAMP('2026-08-06','08:05:00'), TIMESTAMP('2026-08-06','08:35:00'));
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-06','08:35:00') FROM trip_segments ts WHERE ts.trip_id = @t_ida_0806;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 12, TIMESTAMP('2026-08-06','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-06','08:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-06','08:35:00'), 'Pasajero descendio del vehiculo');

-- PAX-013 (Hugo) - ida 08-03 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES ('R-202608-013-1', SHA2(UUID(), 256), UUID(),
    @t_ida_0803, 13,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0803 AND seat_number=10),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0803 AND stop_order=2),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0803 AND stop_order=4),
    2, 4, 'COMPLETED', 13,
    TIMESTAMP('2026-08-03','07:30:00'), TIMESTAMP('2026-08-03','08:05:00'), TIMESTAMP('2026-08-03','08:35:00'));
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-03','08:35:00') FROM trip_segments ts WHERE ts.trip_id = @t_ida_0803;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 13, TIMESTAMP('2026-08-03','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-03','08:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-03','08:35:00'), 'Pasajero descendio del vehiculo');

-- PAX-014 (Isabel) - ida 08-07 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES ('R-202608-014-1', SHA2(UUID(), 256), UUID(),
    @t_ida_0807, 14,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0807 AND seat_number=11),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0807 AND stop_order=2),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0807 AND stop_order=4),
    2, 4, 'COMPLETED', 14,
    TIMESTAMP('2026-08-07','07:30:00'), TIMESTAMP('2026-08-07','08:05:00'), TIMESTAMP('2026-08-07','08:35:00'));
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-07','08:35:00') FROM trip_segments ts WHERE ts.trip_id = @t_ida_0807;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 14, TIMESTAMP('2026-08-07','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-07','08:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-07','08:35:00'), 'Pasajero descendio del vehiculo');

-- PAX-015 (Javier) - ida 08-04 COMPLETED
INSERT INTO reservations (reservation_code, qr_token_hash, booking_group_uuid, trip_id, worker_id, trip_seat_id, origin_trip_stop_time_id, destination_trip_stop_time_id, origin_stop_order, destination_stop_order, status, created_by_user_id, confirmed_at, boarded_at, completed_at)
VALUES ('R-202608-015-1', SHA2(UUID(), 256), UUID(),
    @t_ida_0804, 15,
    (SELECT id FROM trip_seats WHERE trip_id=@t_ida_0804 AND seat_number=12),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0804 AND stop_order=2),
    (SELECT id FROM trip_stop_times WHERE trip_id=@t_ida_0804 AND stop_order=4),
    2, 4, 'COMPLETED', 15,
    TIMESTAMP('2026-08-04','07:30:00'), TIMESTAMP('2026-08-04','08:05:00'), TIMESTAMP('2026-08-04','08:35:00'));
SET @r = LAST_INSERT_ID();
INSERT INTO reservation_segments (reservation_id, trip_segment_id, allocation_status, released_at)
SELECT @r, ts.id, 'RELEASED', TIMESTAMP('2026-08-04','08:35:00') FROM trip_segments ts WHERE ts.trip_id = @t_ida_0804;
INSERT INTO reservation_events (reservation_id, event_type, actor_user_id, event_at, details) VALUES
    (@r, 'CONFIRMED', 15, TIMESTAMP('2026-08-04','07:30:00'), 'Reserva creada por el usuario'),
    (@r, 'BOARDED',   2, TIMESTAMP('2026-08-04','08:05:00'), 'Abordaje confirmado por el conductor'),
    (@r, 'ALIGHTED',  2, TIMESTAMP('2026-08-04','08:35:00'), 'Pasajero descendio del vehiculo');

-- ----------------------------------------------------------------------------
-- 5. Estados y tiempos reales para los viajes pasados
-- ----------------------------------------------------------------------------
UPDATE trip_instances
   SET status = 'COMPLETED',
       actual_start_at = DATE_ADD(scheduled_start_at, INTERVAL FLOOR(RAND()*11) MINUTE),
       actual_end_at = DATE_ADD(scheduled_start_at, INTERVAL (35 + FLOOR(RAND()*11)) MINUTE)
 WHERE service_date BETWEEN '2026-08-03' AND '2026-08-11';

UPDATE trip_instances
   SET status = 'CANCELLED',
       cancellation_reason = 'Vehiculo en mantenimiento'
 WHERE service_date = '2026-08-05' AND TIME(scheduled_start_at) = '18:00:00';

UPDATE trip_instances
   SET status = 'IN_PROGRESS',
       actual_start_at = TIMESTAMP(CURDATE(), '08:05:00')
 WHERE service_date = CURDATE() AND TIME(scheduled_start_at) = '08:00:00';

-- ----------------------------------------------------------------------------
-- 6. Incidencias (tipos y estados variados para el reporte trip-incidents)
-- ----------------------------------------------------------------------------
INSERT INTO trip_incidents (
    trip_id, reported_by_user_id, incident_type, description, status,
    reported_at, resolved_at, resolution_notes
)
SELECT t.id,
       CASE WHEN t.trip_template_id = 1 THEN 9 ELSE 8 END,
       ELT(1+FLOOR(RAND(t.id)*4), 'DELAY', 'BREAKDOWN', 'OTHER', 'ACCIDENT'),
       ELT(1+FLOOR(RAND(t.id+1)*4),
           'Trafico denso en la avenida principal',
           'Averia menor en el sistema electrico',
           'Pasajero reporto olvido de pertenencias',
           'Frenado brusco por maniobra de otro vehiculo'),
       ELT(1+FLOOR(RAND(t.id+2)*3), 'OPEN', 'IN_REVIEW', 'RESOLVED'),
       TIMESTAMP(t.service_date, '08:10:00'),
       IF(RAND(t.id+3) > 0.5, TIMESTAMP(t.service_date, '17:00:00'), NULL),
       IF(RAND(t.id+4) > 0.5, 'Atendido por equipo de operaciones', NULL)
  FROM trip_instances t
 WHERE t.service_date BETWEEN '2026-08-04' AND '2026-08-11'
   AND TIME(t.scheduled_start_at) = '08:00:00'
 LIMIT 6;

-- ----------------------------------------------------------------------------
-- 7. 2 viajes extra en MISMA fecha que el IDA 2026-08-26 (template_id NULL
--    para sortear el UNIQUE uq_trip_template_service_date) con tiempos
--    solapados, para que vw_schedule_conflicts tenga al menos un caso.
-- ----------------------------------------------------------------------------
SET @base_trip = (SELECT id FROM trip_instances WHERE trip_template_id=1 AND service_date='2026-08-26');

-- Conflict A: 08:00..08:40
INSERT INTO trip_instances (
    trip_code, source, trip_template_id, generation_run_id,
    route_id, service_date, scheduled_start_at, scheduled_end_at,
    booking_opens_at, booking_closes_at,
    vehicle_id, driver_id, seat_capacity_snapshot, no_show_tolerance_minutes,
    status
) VALUES (
    'CONFLICT-A-20260826', 'MANUAL', NULL, NULL,
    1, '2026-08-26',
    TIMESTAMP('2026-08-26','08:00:00'),
    TIMESTAMP('2026-08-26','08:40:00'),
    TIMESTAMP('2026-08-26','00:00:00'),
    TIMESTAMP('2026-08-26','07:30:00'),
    1, 2, 12, 5,
    'PUBLISHED'
);
SET @ca = LAST_INSERT_ID();

INSERT INTO trip_stop_times (trip_id, route_stop_id, stop_id, stop_order, scheduled_arrival_at, scheduled_departure_at, applied_travel_minutes, applied_dwell_minutes, status)
SELECT @ca, route_stop_id, stop_id, stop_order,
       DATE_ADD(TIMESTAMP('2026-08-26','08:00:00'), INTERVAL cumulative_offset MINUTE),
       DATE_ADD(TIMESTAMP('2026-08-26','08:00:00'), INTERVAL cumulative_offset + applied_dwell_minutes MINUTE),
       applied_travel_minutes, applied_dwell_minutes, 'PENDING'
  FROM (
      SELECT tst.*, IFNULL(@cum := @cum + @prev_travel, 0) AS cumulative_offset, @prev_travel := applied_travel_minutes
        FROM trip_stop_times tst, (SELECT @cum := 0, @prev_travel := 0) v
       WHERE trip_id = @base_trip ORDER BY stop_order
  ) z;

INSERT INTO trip_seats (trip_id, vehicle_seat_id, seat_number, seat_label)
SELECT @ca, vehicle_seat_id, seat_number, seat_label FROM trip_seats WHERE trip_id = @base_trip;

INSERT INTO trip_segments (trip_id, segment_order, from_trip_stop_time_id, to_trip_stop_time_id)
SELECT @ca, segment_order,
       (SELECT id FROM trip_stop_times WHERE trip_id=@ca AND stop_order = ts.segment_order),
       (SELECT id FROM trip_stop_times WHERE trip_id=@ca AND stop_order = ts.segment_order + 1)
  FROM trip_segments ts WHERE trip_id = @base_trip;

INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state)
SELECT new_ts.id, new_tseg.id, 'AVAILABLE'
  FROM trip_seats new_ts JOIN trip_segments new_tseg ON new_tseg.trip_id = new_ts.trip_id
 WHERE new_ts.trip_id = @ca AND new_tseg.trip_id = @ca;

-- Conflict B: 08:15..08:55
INSERT INTO trip_instances (
    trip_code, source, trip_template_id, generation_run_id,
    route_id, service_date, scheduled_start_at, scheduled_end_at,
    booking_opens_at, booking_closes_at,
    vehicle_id, driver_id, seat_capacity_snapshot, no_show_tolerance_minutes,
    status
) VALUES (
    'CONFLICT-B-20260826', 'MANUAL', NULL, NULL,
    1, '2026-08-26',
    TIMESTAMP('2026-08-26','08:15:00'),
    TIMESTAMP('2026-08-26','08:55:00'),
    TIMESTAMP('2026-08-26','00:00:00'),
    TIMESTAMP('2026-08-26','07:30:00'),
    1, 2, 12, 5,
    'PUBLISHED'
);
SET @cb = LAST_INSERT_ID();

INSERT INTO trip_stop_times (trip_id, route_stop_id, stop_id, stop_order, scheduled_arrival_at, scheduled_departure_at, applied_travel_minutes, applied_dwell_minutes, status)
SELECT @cb, route_stop_id, stop_id, stop_order,
       DATE_ADD(TIMESTAMP('2026-08-26','08:15:00'), INTERVAL cumulative_offset MINUTE),
       DATE_ADD(TIMESTAMP('2026-08-26','08:15:00'), INTERVAL cumulative_offset + applied_dwell_minutes MINUTE),
       applied_travel_minutes, applied_dwell_minutes, 'PENDING'
  FROM (
      SELECT tst.*, IFNULL(@cum := @cum + @prev_travel, 0) AS cumulative_offset, @prev_travel := applied_travel_minutes
        FROM trip_stop_times tst, (SELECT @cum := 0, @prev_travel := 0) v
       WHERE trip_id = @base_trip ORDER BY stop_order
  ) z;

INSERT INTO trip_seats (trip_id, vehicle_seat_id, seat_number, seat_label)
SELECT @cb, vehicle_seat_id, seat_number, seat_label FROM trip_seats WHERE trip_id = @base_trip;

INSERT INTO trip_segments (trip_id, segment_order, from_trip_stop_time_id, to_trip_stop_time_id)
SELECT @cb, segment_order,
       (SELECT id FROM trip_stop_times WHERE trip_id=@cb AND stop_order = ts.segment_order),
       (SELECT id FROM trip_stop_times WHERE trip_id=@cb AND stop_order = ts.segment_order + 1)
  FROM trip_segments ts WHERE trip_id = @base_trip;

INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state)
SELECT new_ts.id, new_tseg.id, 'AVAILABLE'
  FROM trip_seats new_ts JOIN trip_segments new_tseg ON new_tseg.trip_id = new_ts.trip_id
 WHERE new_ts.trip_id = @cb AND new_tseg.trip_id = @cb;

-- ----------------------------------------------------------------------------
-- 8. Verificacion: conteos por reporte.
-- ----------------------------------------------------------------------------
SELECT 'users' AS metric, COUNT(*) AS n FROM users
UNION ALL SELECT 'trip_instances', COUNT(*) FROM trip_instances
UNION ALL SELECT CONCAT('  status=', status), COUNT(*) FROM trip_instances GROUP BY status
UNION ALL SELECT 'reservations', COUNT(*) FROM reservations
UNION ALL SELECT CONCAT('  status=', status), COUNT(*) FROM reservations GROUP BY status
UNION ALL SELECT 'reservation_events', COUNT(*) FROM reservation_events
UNION ALL SELECT 'trip_incidents', COUNT(*) FROM trip_incidents
UNION ALL SELECT CONCAT('  type=', incident_type), COUNT(*) FROM trip_incidents GROUP BY incident_type
UNION ALL SELECT CONCAT('  status=', status), COUNT(*) FROM trip_incidents GROUP BY status;

SELECT 'vw_schedule_conflicts' AS vista, COUNT(*) AS filas FROM vw_schedule_conflicts
UNION ALL SELECT 'vw_route_time_matrix', COUNT(*) FROM vw_route_time_matrix
UNION ALL SELECT 'vw_trip_segment_seat_availability', COUNT(*) FROM vw_trip_segment_seat_availability
UNION ALL SELECT 'vw_route_occupancy', COUNT(*) FROM vw_route_occupancy
UNION ALL SELECT 'vw_trips_status_summary', COUNT(*) FROM vw_trips_status_summary
UNION ALL SELECT 'vw_duration_deviation', COUNT(*) FROM vw_duration_deviation
UNION ALL SELECT 'vw_delays_by_route_day', COUNT(*) FROM vw_delays_by_route_day
UNION ALL SELECT 'vw_reservation_changes', COUNT(*) FROM vw_reservation_changes
UNION ALL SELECT 'vw_trip_incidents', COUNT(*) FROM vw_trip_incidents
UNION ALL SELECT 'vw_user_reservation_activity', COUNT(*) FROM vw_user_reservation_activity;