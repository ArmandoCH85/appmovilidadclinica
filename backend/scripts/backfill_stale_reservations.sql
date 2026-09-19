-- ============================================================================
-- backfill_stale_reservations.sql
-- Reparacion de datos, UNA SOLA VEZ, de reservas que quedaron CONFIRMED en
-- viajes de fechas pasadas y que NADIE puede limpiar.
--
-- Por que existe: el marcador automatico de NO_SHOW
-- (platform/jobs/noshow_checker.go) exige que el conductor haya marcado la
-- llegada al punto de subida (trip_stop_times.actual_arrival_at IS NOT NULL).
-- Los viajes de fechas pasadas que nunca se iniciaron ni cerraron no tienen
-- ninguna marca, asi que sus reservas CONFIRMED quedan para siempre ocupando
-- inventario: 9 en produccion (viajes 4, 5, 6, 64, 128, 136, 157, 166), detras
-- de 91 viajes vencidos sin cerrar.
--
-- Criterio exacto (deliberadamente conservador): solo las reservas CONFIRMED
-- de viajes con service_date < hoy cuya llegada al origen NUNCA se marco. Las
-- que si tienen marca las sigue manejando el job de NO_SHOW, que es el camino
-- correcto (las pasa a NO_SHOW).
--
-- Replica las sentencias de sp_cancel_reservation (0002) de forma set-based:
-- libera los segmentos, deja el historial en reservation_segments, cancela la
-- reserva y registra el evento CANCELLED. actor_user_id = driver del viaje
-- (la columna es NOT NULL; mismo criterio que el job de NO_SHOW). No se usa la
-- SP porque necesita un CALL por reserva y esto es una limpieza puntual.
--
-- Idempotente: la segunda corrida no encuentra filas CONFIRMED que cumplan.
--
-- Uso:
--   mariadb -u appuser -p transporte_corporativo_mvp < backfill_stale_reservations.sql
-- ============================================================================

START TRANSACTION;

-- 1) Libera el inventario vivo (vuelve a AVAILABLE).
UPDATE trip_seat_segments s
  JOIN reservations r ON r.id = s.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id
   SET s.state = 'AVAILABLE',
       s.reservation_id = NULL,
       s.released_at = CURRENT_TIMESTAMP
 WHERE s.state IN ('RESERVED', 'OCCUPIED')
   AND r.status = 'CONFIRMED'
   AND t.service_date < CURRENT_DATE()
   AND NOT EXISTS (
        SELECT 1 FROM trip_stop_times tst
         WHERE tst.id = r.origin_trip_stop_time_id
           AND tst.actual_arrival_at IS NOT NULL
   );

-- 2) Historial de segmentos: queda auditable como liberado.
UPDATE reservation_segments rs
  JOIN reservations r ON r.id = rs.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id
   SET rs.allocation_status = 'RELEASED',
       rs.released_at = CURRENT_TIMESTAMP
 WHERE rs.allocation_status IN ('RESERVED', 'OCCUPIED')
   AND r.status = 'CONFIRMED'
   AND t.service_date < CURRENT_DATE()
   AND NOT EXISTS (
        SELECT 1 FROM trip_stop_times tst
         WHERE tst.id = r.origin_trip_stop_time_id
           AND tst.actual_arrival_at IS NOT NULL
   );

-- 3) Evento de auditoria (antes de cambiar el estado).
INSERT INTO reservation_events (
    reservation_id, event_type, actor_user_id, details
)
SELECT r.id, 'CANCELLED', t.driver_id,
       'Limpieza retroactiva: viaje vencido sin cierre ni llegada registrada'
  FROM reservations r
  JOIN trip_instances t ON t.id = r.trip_id
 WHERE r.status = 'CONFIRMED'
   AND t.service_date < CURRENT_DATE()
   AND NOT EXISTS (
        SELECT 1 FROM trip_stop_times tst
         WHERE tst.id = r.origin_trip_stop_time_id
           AND tst.actual_arrival_at IS NOT NULL
   );

-- 4) Cancela las reservas.
UPDATE reservations r
  JOIN trip_instances t ON t.id = r.trip_id
   SET r.status = 'CANCELLED'
 WHERE r.status = 'CONFIRMED'
   AND t.service_date < CURRENT_DATE()
   AND NOT EXISTS (
        SELECT 1 FROM trip_stop_times tst
         WHERE tst.id = r.origin_trip_stop_time_id
           AND tst.actual_arrival_at IS NOT NULL
   );

COMMIT;

-- Verificacion: debe dar 0.
SELECT 'reservas CONFIRMED en viajes vencidos sin llegada marcada' AS chequeo, COUNT(*) AS filas
  FROM reservations r
  JOIN trip_instances t ON t.id = r.trip_id
 WHERE r.status = 'CONFIRMED'
   AND t.service_date < CURRENT_DATE()
   AND NOT EXISTS (
        SELECT 1 FROM trip_stop_times tst
         WHERE tst.id = r.origin_trip_stop_time_id
           AND tst.actual_arrival_at IS NOT NULL
   );

-- Verificacion: inventario sin fugas.
SELECT 'segmentos OCCUPIED/RESERVED con reserva no activa' AS chequeo, COUNT(*) AS filas
  FROM trip_seat_segments s
  JOIN reservations r ON r.id = s.reservation_id
 WHERE s.state IN ('OCCUPIED', 'RESERVED')
   AND ((s.state = 'OCCUPIED' AND r.status <> 'BOARDED')
     OR (s.state = 'RESERVED' AND r.status <> 'CONFIRMED'));
