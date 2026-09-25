-- ============================================================================
-- reset_trips_from_date.sql
-- Opcion A: reinicia (limpia el estado de) los viajes desde una fecha dada
-- hacia adelante, SIN borrar los trip_instances. Se conserva la grilla de
-- viajes (incluidos los que estan fuera de la ventana del generador).
--
-- Que hace, para los viajes con service_date >= @from_date:
--   1) Libera los asientos: trip_seat_segments -> AVAILABLE (null refs).
--   2) Borra reservation_events de esas reservas.
--   3) Borra reservation_segments de esas reservas.
--   4) Borra guest_occupants (invitados) de esos viajes.
--   5) Borra reservations de esos viajes.
--   6) Resetea trip_stop_times: sin marcas reales, status PENDING.
--   7) Resetea trip_instances: status PUBLISHED, sin actual_* ni cancelacion.
--
-- NO toca: trip_incidents (auditoria de operacion), trip_seats, trip_segments,
--          ni nada con service_date < @from_date.
--
-- Idempotente. Hacer mariadb-dump ANTES.
-- Correr con: mariadb -u appuser -p <db> < reset_trips_from_date.sql
-- ============================================================================

SET @from_date = '2026-09-24';

-- ---------------------------------------------------------------------------
-- 1) PREVIEW (antes)
-- ---------------------------------------------------------------------------
SELECT 'PREVIEW: a limpiar (service_date >= @from_date)' AS fase;
SELECT 'reservations' AS tabla, COUNT(*) AS n
  FROM reservations r JOIN trip_instances t ON t.id = r.trip_id
 WHERE t.service_date >= @from_date
UNION ALL SELECT '  CONFIRMED (reservas reales pendientes)', COUNT(*)
  FROM reservations r JOIN trip_instances t ON t.id = r.trip_id
 WHERE t.service_date >= @from_date AND r.status = 'CONFIRMED'
UNION ALL SELECT 'reservation_events', COUNT(*)
  FROM reservation_events re JOIN reservations r ON r.id = re.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id WHERE t.service_date >= @from_date
UNION ALL SELECT 'reservation_segments', COUNT(*)
  FROM reservation_segments rs JOIN reservations r ON r.id = rs.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id WHERE t.service_date >= @from_date
UNION ALL SELECT 'guest_occupants', COUNT(*)
  FROM guest_occupants g JOIN trip_instances t ON t.id = g.trip_id
 WHERE t.service_date >= @from_date
UNION ALL SELECT 'trip_seat_segments a liberar', COUNT(*)
  FROM trip_seat_segments s JOIN trip_seats ts ON ts.id = s.trip_seat_id
  JOIN trip_instances t ON t.id = ts.trip_id
 WHERE t.service_date >= @from_date
   AND (s.state <> 'AVAILABLE' OR s.reservation_id IS NOT NULL OR s.guest_occupant_id IS NOT NULL)
UNION ALL SELECT 'trip_stop_times a resetear', COUNT(*)
  FROM trip_stop_times st JOIN trip_instances t ON t.id = st.trip_id
 WHERE t.service_date >= @from_date
   AND (st.actual_arrival_at IS NOT NULL OR st.actual_departure_at IS NOT NULL
        OR st.arrival_marked_by_user_id IS NOT NULL OR st.status <> 'PENDING')
UNION ALL SELECT 'trip_instances a resetear', COUNT(*)
  FROM trip_instances
 WHERE service_date >= @from_date
   AND (status <> 'PUBLISHED' OR actual_start_at IS NOT NULL
        OR actual_end_at IS NOT NULL OR cancellation_reason IS NOT NULL);

-- ---------------------------------------------------------------------------
-- 2) RESET
-- ---------------------------------------------------------------------------
START TRANSACTION;

-- 2.1) Liberar asientos.
UPDATE trip_seat_segments s
  JOIN trip_seats     ts ON ts.id = s.trip_seat_id
  JOIN trip_instances t  ON t.id  = ts.trip_id
   SET s.state            = 'AVAILABLE',
       s.reservation_id   = NULL,
       s.reserved_at      = NULL,
       s.released_at      = NULL,
       s.guest_occupant_id = NULL
 WHERE t.service_date >= @from_date
   AND (s.state <> 'AVAILABLE' OR s.reservation_id IS NOT NULL OR s.guest_occupant_id IS NOT NULL);

-- 2.2) Eventos de reserva.
DELETE re FROM reservation_events re
  JOIN reservations   r ON r.id = re.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id
 WHERE t.service_date >= @from_date;

-- 2.3) Segmentos de reserva (historial).
DELETE rs FROM reservation_segments rs
  JOIN reservations   r ON r.id = rs.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id
 WHERE t.service_date >= @from_date;

-- 2.4) Invitados.
DELETE g FROM guest_occupants g
  JOIN trip_instances t ON t.id = g.trip_id
 WHERE t.service_date >= @from_date;

-- 2.5) Reservas.
DELETE r FROM reservations r
  JOIN trip_instances t ON t.id = r.trip_id
 WHERE t.service_date >= @from_date;

-- 2.6) Marcas reales de paradas.
UPDATE trip_stop_times st
  JOIN trip_instances t ON t.id = st.trip_id
   SET st.actual_arrival_at        = NULL,
       st.actual_departure_at      = NULL,
       st.arrival_marked_by_user_id = NULL,
       st.status                    = 'PENDING'
 WHERE t.service_date >= @from_date
   AND (st.actual_arrival_at IS NOT NULL OR st.actual_departure_at IS NOT NULL
        OR st.arrival_marked_by_user_id IS NOT NULL OR st.status <> 'PENDING');

-- 2.7) Cabecera del viaje.
UPDATE trip_instances
   SET status              = 'PUBLISHED',
       actual_start_at     = NULL,
       actual_end_at       = NULL,
       cancellation_reason = NULL
 WHERE service_date >= @from_date
   AND (status <> 'PUBLISHED' OR actual_start_at IS NOT NULL
        OR actual_end_at IS NOT NULL OR cancellation_reason IS NOT NULL);

COMMIT;

-- ---------------------------------------------------------------------------
-- 3) VERIFICACION (despues: todo debe dar 0 salvo el conteo de viajes)
-- ---------------------------------------------------------------------------
SELECT 'VERIFY: debe dar 0' AS fase;
SELECT 'reservations' AS tabla, COUNT(*) AS n
  FROM reservations r JOIN trip_instances t ON t.id = r.trip_id
 WHERE t.service_date >= @from_date
UNION ALL SELECT 'reservation_events', COUNT(*)
  FROM reservation_events re JOIN reservations r ON r.id = re.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id WHERE t.service_date >= @from_date
UNION ALL SELECT 'reservation_segments', COUNT(*)
  FROM reservation_segments rs JOIN reservations r ON r.id = rs.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id WHERE t.service_date >= @from_date
UNION ALL SELECT 'guest_occupants', COUNT(*)
  FROM guest_occupants g JOIN trip_instances t ON t.id = g.trip_id
 WHERE t.service_date >= @from_date
UNION ALL SELECT 'trip_seat_segments no disponibles', COUNT(*)
  FROM trip_seat_segments s JOIN trip_seats ts ON ts.id = s.trip_seat_id
  JOIN trip_instances t ON t.id = ts.trip_id
 WHERE t.service_date >= @from_date AND s.state <> 'AVAILABLE'
UNION ALL SELECT 'trip_stop_times no PENDING', COUNT(*)
  FROM trip_stop_times st JOIN trip_instances t ON t.id = st.trip_id
 WHERE t.service_date >= @from_date AND st.status <> 'PENDING'
UNION ALL SELECT 'trip_instances no PUBLISHED', COUNT(*)
  FROM trip_instances WHERE service_date >= @from_date AND status <> 'PUBLISHED';

-- Control: lo de <= 2026-09-23 NO se toca.
SELECT 'CONTROL: 2026-09-23 y anteriores intactos' AS fase,
       COUNT(*) AS viajes,
       SUM(status <> 'PUBLISHED') AS no_publicados
  FROM trip_instances WHERE service_date < @from_date;
