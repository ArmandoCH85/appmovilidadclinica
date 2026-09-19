-- ============================================================================
-- backfill_actual_end_from_marks.sql
-- Correccion puntual de actual_end_at en los 2 viajes que cerro un ADMIN antes
-- de la 0021 (viajes 133 y 195, 2026-09-15).
--
-- Contexto: backfill_closed_trip_manifest.sql reconstruyo su actual_end_at con
-- scheduled_end_at (19:35) porque no habia dato real de cierre. Al rellenar
-- despues actual_start_at con la primera marca de parada (10:32:57 en el 133,
-- 12:29:34 en el 195), el reporte de desviacion de duracion quedo disparatado:
-- +467 y +365 minutos (622% y 608%), porque el fin venia de la grilla y el
-- inicio de las marcas reales.
--
-- Los dos viajes tienen TODAS sus paradas marcadas, asi que el fin real se
-- puede reconstruir con la ultima llegada registrada:
--   viaje 133: 10:32:57 -> 11:43:07 (4 paradas)  => 70 min vs 75 programados
--   viaje 195: 12:29:34 -> 13:24:30 (7 paradas)  => 55 min vs 60 programados
--
-- Se alinean tambien reservation_events.event_at y reservations.completed_at
-- para mantener el invariante de sp_complete_trip / sp_admin_close_trip: el
-- cierre del viaje y el cierre de sus reservas comparten timestamp.
--
-- Guardas (doble): solo esos dos ids Y solo si actual_end_at sigue siendo
-- exactamente scheduled_end_at (o sea, todavia el valor reconstruido).
--
-- Idempotente: la segunda corrida no encuentra filas (actual_end_at ya difiere
-- de scheduled_end_at). Reproduce el caso real de admin con 0021: 2 trips.
--
-- Uso:
--   mariadb -u appuser -p transporte_corporativo_mvp < backfill_actual_end_from_marks.sql
-- ============================================================================

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_recon_end (
    trip_id  BIGINT UNSIGNED PRIMARY KEY,
    real_end DATETIME NOT NULL
) ENGINE=InnoDB;

INSERT INTO tmp_recon_end (trip_id, real_end)
SELECT t.id, MAX(tst.actual_arrival_at)
  FROM trip_instances t
  JOIN trip_stop_times tst ON tst.trip_id = t.id
 WHERE t.id IN (133, 195)
   AND t.actual_end_at IS NOT NULL
   AND t.scheduled_end_at IS NOT NULL
   AND t.actual_end_at = t.scheduled_end_at
   AND tst.actual_arrival_at IS NOT NULL
 GROUP BY t.id;

-- Eventos de auditoria del cierre retroactivo.
UPDATE reservation_events e
  JOIN reservations r ON r.id = e.reservation_id
  JOIN tmp_recon_end x ON x.trip_id = r.trip_id
   SET e.event_at = x.real_end
 WHERE e.details LIKE 'Cierre retroactivo (backfill)%';

-- Cierre de las reservas del viaje.
UPDATE reservations r
  JOIN tmp_recon_end x ON x.trip_id = r.trip_id
   SET r.completed_at = x.real_end
 WHERE r.completed_at IS NOT NULL;

-- Invitados cerrados con un timestamp posterior al fin real (los que quedaron
-- antes del fin, como el invitado del viaje 133, se respetan).
UPDATE guest_occupants g
  JOIN tmp_recon_end x ON x.trip_id = g.trip_id
   SET g.completed_at = x.real_end
 WHERE g.completed_at IS NOT NULL
   AND g.completed_at > x.real_end;

-- Fin real del viaje.
UPDATE trip_instances t
  JOIN tmp_recon_end x ON x.trip_id = t.id
   SET t.actual_end_at = x.real_end;

DROP TEMPORARY TABLE tmp_recon_end;

COMMIT;

-- Verificacion: desviacion de duracion de esos dos viajes.
SELECT trip_id, service_date, scheduled_duration_minutes, actual_duration_minutes,
       delta_minutes, delta_pct
  FROM vw_duration_deviation
 WHERE trip_id IN (133, 195)
 ORDER BY trip_id;
