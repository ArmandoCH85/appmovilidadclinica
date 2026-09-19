-- ============================================================================
-- backfill_closed_trip_manifest.sql
-- Reparacion de datos, UNA SOLA VEZ, para viajes que quedaron COMPLETED con el
-- manifiesto abierto.
--
-- Por que existe: hasta la 0021, POST /admin/trips/{id}/status hacia un UPDATE
-- pelado de trip_instances.status. Un ADMIN podia marcar COMPLETED sin ejecutar
-- sp_complete_trip, asi que:
--   - actual_end_at quedaba NULL (nunca se paso por la SP),
--   - las reservas BOARDED quedaban BOARDED para siempre (con el evento
--     ALIGHTED sin registrar y el asiento OCCUPIED),
--   - los invitados BOARDED quedaban igual.
-- Casos reales en produccion: viajes 133 y 195 (2026-09-15).
--
-- Aplica el mismo criterio que sp_complete_trip (0017) y sp_admin_close_trip
-- (0021): reservas e invitados BOARDED -> COMPLETED + completed_at, segmentos
-- de asiento -> USED, evento ALIGHTED por reserva.
--
-- NO toca las reservas CONFIRMED: esas las cierra el job de NO_SHOW
-- (platform/jobs/noshow_checker.go), que es el unico mecanismo que las limpia.
--
-- Alcance: viajes en estado COMPLETED. Los CANCELLED no se tocan (en produccion
-- no hay ninguno; sus reservas/invitados se cancelan con sp_cancel_trip).
--
-- Idempotente: si no hay filas colgadas, no cambia nada. Corrida en produccion
-- el 2026-09-19; backup previo /root/backup_pre_backfill_manifest_*.sql.
--
-- Uso:
--   mariadb -u appuser -p transporte_corporativo_mvp < backfill_closed_trip_manifest.sql
-- ============================================================================

START TRANSACTION;

-- 0) actual_end_at faltante en viajes cerrados sin pasar por la SP. Se usa el
--    fin programado como aproximacion (no hay dato real de cuando termino).
UPDATE trip_instances t
   SET t.actual_end_at = t.scheduled_end_at
 WHERE t.status = 'COMPLETED'
   AND t.actual_end_at IS NULL
   AND t.scheduled_end_at IS NOT NULL;

-- 1) Evento ALIGHTED por cada reserva BOARDED de un viaje cerrado. Va ANTES de
--    cerrar la reserva: el SELECT filtra por status = 'BOARDED'.
--    actor_user_id = driver del viaje (la columna es NOT NULL; el job de
--    NO_SHOW usa el mismo criterio).
INSERT INTO reservation_events (
    reservation_id, event_type, actor_user_id, event_at, details
)
SELECT r.id, 'ALIGHTED', t.driver_id, t.actual_end_at,
       'Cierre retroactivo (backfill): viaje ya estaba COMPLETED'
  FROM reservations r
  JOIN trip_instances t ON t.id = r.trip_id
 WHERE t.status = 'COMPLETED'
   AND r.status = 'BOARDED'
   AND t.actual_end_at IS NOT NULL;

-- 2) Segmentos de asiento de esas reservas -> USED (antes de cerrarlas).
UPDATE trip_seat_segments s
  JOIN reservations r ON r.id = s.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id
   SET s.state = 'USED'
 WHERE s.state = 'OCCUPIED'
   AND t.status = 'COMPLETED'
   AND r.status = 'BOARDED';

UPDATE reservation_segments rs
  JOIN reservations r ON r.id = rs.reservation_id
  JOIN trip_instances t ON t.id = r.trip_id
   SET rs.allocation_status = 'USED'
 WHERE rs.allocation_status = 'OCCUPIED'
   AND t.status = 'COMPLETED'
   AND r.status = 'BOARDED';

-- 3) Segmentos de asiento de invitados BOARDED de viajes cerrados -> USED.
UPDATE trip_seat_segments s
  JOIN guest_occupants g ON g.id = s.guest_occupant_id
  JOIN trip_instances t ON t.id = g.trip_id
   SET s.state = 'USED'
 WHERE s.state = 'OCCUPIED'
   AND t.status = 'COMPLETED'
   AND g.status = 'BOARDED';

-- 4) Cierre de las reservas y de los invitados.
UPDATE reservations r
  JOIN trip_instances t ON t.id = r.trip_id
   SET r.status = 'COMPLETED',
       r.completed_at = t.actual_end_at
 WHERE t.status = 'COMPLETED'
   AND r.status = 'BOARDED'
   AND t.actual_end_at IS NOT NULL;

UPDATE guest_occupants g
  JOIN trip_instances t ON t.id = g.trip_id
   SET g.status = 'COMPLETED',
       g.completed_at = t.actual_end_at
 WHERE t.status = 'COMPLETED'
   AND g.status = 'BOARDED'
   AND t.actual_end_at IS NOT NULL;

COMMIT;

-- Verificacion: las cuatro consultas deben dar 0.
SELECT 'viajes COMPLETED sin actual_end_at' AS chequeo, COUNT(*) AS filas
  FROM trip_instances WHERE status = 'COMPLETED' AND actual_end_at IS NULL
UNION ALL
SELECT 'reservas BOARDED en viajes COMPLETED', COUNT(*)
  FROM reservations r JOIN trip_instances t ON t.id = r.trip_id
 WHERE r.status = 'BOARDED' AND t.status = 'COMPLETED'
UNION ALL
SELECT 'invitados BOARDED en viajes COMPLETED', COUNT(*)
  FROM guest_occupants g JOIN trip_instances t ON t.id = g.trip_id
 WHERE g.status = 'BOARDED' AND t.status = 'COMPLETED'
UNION ALL
SELECT 'asientos de invitado OCCUPIED en viajes COMPLETED', COUNT(*)
  FROM trip_seat_segments s
  JOIN guest_occupants g ON g.id = s.guest_occupant_id
  JOIN trip_instances t ON t.id = g.trip_id
 WHERE s.state = 'OCCUPIED' AND t.status = 'COMPLETED';
