-- ============================================================================
-- backfill_0018_orphan_guests.sql
-- Reparacion de datos, UNA SOLA VEZ, posterior a la 0018.
--
-- La 0018 cambia los SPs hacia adelante, pero no puede cerrar los invitados que
-- YA quedaron colgados: un viaje COMPLETED nunca vuelve a emitir una llegada ni
-- un sp_complete_trip, asi que esos invitados se habrian quedado BOARDED con sus
-- asientos OCCUPIED para siempre (fuga de inventario: nadie libera el asiento).
--
-- Ejecutado en produccion el 2026-09-19 (backup previo:
-- /root/backup_pre_0018_2026-09-19_1402.sql). Afecto a 2 invitados
-- (guest_occupants.id = 3 y 4, viajes 133 y 14) y libero 7 segmentos.
--
-- Idempotente: si no hay filas colgadas, no cambia nada.
--
-- Uso:
--   mariadb -u appuser -p transporte_corporativo_mvp < backfill_0018_orphan_guests.sql
-- ============================================================================

START TRANSACTION;

-- 1) Liberar los segmentos de asiento de invitados cuyo viaje ya esta cerrado.
UPDATE trip_seat_segments s
  JOIN guest_occupants g ON g.id = s.guest_occupant_id
  JOIN trip_instances t ON t.id = g.trip_id
   SET s.state = 'USED'
 WHERE s.state = 'OCCUPIED'
   AND g.status = 'BOARDED'
   AND t.status IN ('COMPLETED', 'CANCELLED');

-- 2) Cerrar a esos invitados, usando el fin real del viaje como completed_at
--    (COALESCE por si el viaje quedo cerrado sin actual_end_at).
UPDATE guest_occupants g
  JOIN trip_instances t ON t.id = g.trip_id
   SET g.status = 'COMPLETED',
       g.completed_at = COALESCE(t.actual_end_at, g.created_at)
 WHERE g.status = 'BOARDED'
   AND t.status IN ('COMPLETED', 'CANCELLED');

COMMIT;

-- Verificacion: ambas consultas deben dar 0.
SELECT COUNT(*) AS invitados_boarded_en_viajes_cerrados
  FROM guest_occupants g
  JOIN trip_instances t ON t.id = g.trip_id
 WHERE g.status = 'BOARDED'
   AND t.status IN ('COMPLETED', 'CANCELLED');

SELECT COUNT(*) AS segmentos_invitado_ocupados_en_viajes_cerrados
  FROM trip_seat_segments s
  JOIN guest_occupants g ON g.id = s.guest_occupant_id
  JOIN trip_instances t ON t.id = g.trip_id
 WHERE s.state = 'OCCUPIED'
   AND t.status IN ('COMPLETED', 'CANCELLED');
