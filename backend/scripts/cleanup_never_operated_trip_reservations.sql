-- ============================================================================
-- cleanup_never_operated_trip_reservations.sql
-- Elimina los datos de PRUEBA: reservas, eventos, segmentos e invitados
-- asociados a viajes que NUNCA se operaron (status DRAFT/PUBLISHED).
--
-- Por que es seguro: un viaje que no arranco no pudo tener pasajeros reales.
-- Las reservas en estado terminal (COMPLETED/NO_SHOW) sobre esos viajes son
-- imposibles (se auto-cierran al marcar llegada, y el viaje nunca se marcó).
-- No hay reservas CONFIRMED (pendientes reales) sobre estos viajes.
--
-- Orden de borrado (dependencias primero): eventos -> segmentos de reserva ->
-- segmentos de asiento -> invitados -> reservas.
--
-- Idempotente. Correr con: mysql <db> < cleanup_never_operated_trip_reservations.sql
-- Hacer mysqldump ANTES (ver /root/backups_appmovilidad/).
-- ============================================================================

-- 1) PREVIEW
SELECT 'reservations' AS tabla, COUNT(*) AS a_borrar FROM reservations res
  JOIN trip_instances t ON t.id = res.trip_id WHERE t.status IN ('DRAFT','PUBLISHED')
UNION ALL SELECT 'reservation_events', COUNT(*) FROM reservation_events re
  JOIN reservations res ON res.id = re.reservation_id
  JOIN trip_instances t ON t.id = res.trip_id WHERE t.status IN ('DRAFT','PUBLISHED')
UNION ALL SELECT 'reservation_segments', COUNT(*) FROM reservation_segments rs
  JOIN reservations res ON res.id = rs.reservation_id
  JOIN trip_instances t ON t.id = res.trip_id WHERE t.status IN ('DRAFT','PUBLISHED')
UNION ALL SELECT 'guest_occupants', COUNT(*) FROM guest_occupants g
  JOIN trip_instances t ON t.id = g.trip_id WHERE t.status IN ('DRAFT','PUBLISHED');

-- 2) BORRADO
START TRANSACTION;

DELETE re FROM reservation_events re
  JOIN reservations res ON res.id = re.reservation_id
  JOIN trip_instances t   ON t.id = res.trip_id
 WHERE t.status IN ('DRAFT','PUBLISHED');

DELETE rs FROM reservation_segments rs
  JOIN reservations res ON res.id = rs.reservation_id
  JOIN trip_instances t   ON t.id = res.trip_id
 WHERE t.status IN ('DRAFT','PUBLISHED');

DELETE tss FROM trip_seat_segments tss
  JOIN reservations res ON res.id = tss.reservation_id
  JOIN trip_instances t   ON t.id = res.trip_id
 WHERE t.status IN ('DRAFT','PUBLISHED');

DELETE tss FROM trip_seat_segments tss
  JOIN guest_occupants g ON g.id = tss.guest_occupant_id
  JOIN trip_instances t  ON t.id = g.trip_id
 WHERE t.status IN ('DRAFT','PUBLISHED');

DELETE g FROM guest_occupants g
  JOIN trip_instances t ON t.id = g.trip_id
 WHERE t.status IN ('DRAFT','PUBLISHED');

DELETE res FROM reservations res
  JOIN trip_instances t ON t.id = res.trip_id
 WHERE t.status IN ('DRAFT','PUBLISHED');

COMMIT;

-- 3) VERIFICACION: todo debe dar 0.
SELECT 'reservations' AS tabla, COUNT(*) AS quedan FROM reservations res
  JOIN trip_instances t ON t.id = res.trip_id WHERE t.status IN ('DRAFT','PUBLISHED')
UNION ALL SELECT 'guest_occupants', COUNT(*) FROM guest_occupants g
  JOIN trip_instances t ON t.id = g.trip_id WHERE t.status IN ('DRAFT','PUBLISHED');
