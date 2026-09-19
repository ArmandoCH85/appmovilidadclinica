-- ============================================================================
-- backfill_actual_start_at.sql
-- Reparacion de datos, UNA SOLA VEZ, de trip_instances.actual_start_at.
--
-- Por que existe: nadie escribia actual_start_at (ni StartTrip ni el panel de
-- admin), asi que los reportes que dependen de esa columna salian vacios:
--   vw_duration_deviation      -> GET /admin/reports/duration-deviation (#12)
--   vw_delays_by_route_day     -> GET /admin/reports/delays-by-route-day
-- Ambas vistas filtran WHERE trip.actual_start_at IS NOT NULL. En produccion
-- habia 0 de 928 viajes con el valor.
--
-- El fix hacia adelante esta en el codigo (StartTrip y el cambio de estado del
-- admin setean actual_start_at = COALESCE(actual_start_at, CURRENT_TIMESTAMP)).
-- Este script rellena los viajes historicos usando la primera marca real de
-- parada: el MIN(actual_arrival_at) del viaje, con fallback a
-- MIN(actual_departure_at). Es una aproximacion: si el conductor no marco la
-- primera parada, queda la marca de la mas temprana que si marco.
--
-- Idempotente (solo toca filas con actual_start_at NULL). Los viajes sin
-- ninguna marca quedan como estan: no hay dato para reconstruirlos.
--
-- Uso:
--   mariadb -u appuser -p transporte_corporativo_mvp < backfill_actual_start_at.sql
-- ============================================================================

UPDATE trip_instances t
  JOIN (
        SELECT trip_id,
               MIN(actual_arrival_at)   AS first_arrival,
               MIN(actual_departure_at) AS first_departure
          FROM trip_stop_times
         GROUP BY trip_id
       ) marcas ON marcas.trip_id = t.id
   SET t.actual_start_at = COALESCE(marcas.first_arrival, marcas.first_departure)
 WHERE t.actual_start_at IS NULL
   AND COALESCE(marcas.first_arrival, marcas.first_departure) IS NOT NULL;

-- Verificacion: viajes con marcas de parada pero sin actual_start_at (debe ser 0).
SELECT 'con marcas de parada y sin actual_start_at' AS chequeo, COUNT(*) AS filas
  FROM trip_instances t
 WHERE t.actual_start_at IS NULL
   AND EXISTS (
        SELECT 1 FROM trip_stop_times tst
         WHERE tst.trip_id = t.id
           AND (tst.actual_arrival_at IS NOT NULL OR tst.actual_departure_at IS NOT NULL)
   );

-- Verificacion: cuantos viajes quedaron con actual_start_at.
SELECT COUNT(*) AS viajes_con_actual_start_at FROM trip_instances WHERE actual_start_at IS NOT NULL;
