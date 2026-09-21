-- ============================================================================
-- 0025_trip_stop_arrivals.up.sql
-- Vista SQL de solo lectura para el reporte "Llegadas por sede/paradero".
--
-- Una fila por (viaje × parada): la hora programada y la hora real de llegada
-- de cada bus a cada sede o paradero, con el desvío en minutos. Es el detalle
-- por parada que hoy no existe: vw_delays_by_route_day (#13) solo mide el
-- atraso de la SALIDA del viaje, agregado por ruta/día.
--
-- Fuentes (todas ya materializadas, no crea tablas):
--   trip_stop_times   -> scheduled/actual arrival+departure, status, orden
--   trip_instances    -> trip_code, service_date, turno (scheduled_start_at)
--   transport_routes  -> code, name, direction (IDA/VUELTA)
--   transport_stops   -> code, name, stop_type (SEDE | PARADERO)
--   vehicles / users  -> placa del bus y nombre del conductor
--
-- MAÑANA Y NOCHE (importante):
--   Los viajes cruzan el día: uno nocturno que sale 23:00 puede llegar
--   00:30 del día calendario siguiente. Por eso:
--     * El filtro de fechas se hace por service_date (día operativo del
--       viaje), NO por DATE(scheduled_arrival_at). Así un viaje nocturno
--       del 10/02 con llegada 00:30 del 11/02 sigue apareciendo bajo el
--       10/02, que es como lo opera la gente.
--     * `time_slot` (turno) se deriva de trip.scheduled_start_at, la hora de
--       SALIDA del viaje. Un viaje nocturno que cruza medianoche sigue siendo
--       NOCHE aunque su llegada caiga en la madrugada. Si se derivara de la
--       llegada, ese mismo viaje se etiquetaría MADRUGADA y el reporte
--       mentiría sobre el turno.
--
-- No usa DELIMITER: son CREATE VIEW, no stored procedures. El parser de
-- migraciones (internal/platform/database/migrate.go) corta por ";" cuando
-- no hay DELIMITER activo.
-- ============================================================================

DROP VIEW IF EXISTS vw_trip_stop_arrivals;

CREATE VIEW vw_trip_stop_arrivals AS
SELECT tst.id                                       AS trip_stop_time_id,
       trip.id                                      AS trip_id,
       trip.trip_code                               AS trip_code,
       trip.service_date                            AS service_date,
       trip.status                                  AS trip_status,
       route.id                                     AS route_id,
       route.code                                   AS route_code,
       route.name                                   AS route_name,
       route.direction                              AS direction,
       veh.id                                       AS vehicle_id,
       veh.internal_code                            AS vehicle_internal_code,
       veh.plate                                    AS vehicle_plate,
       drv.id                                       AS driver_id,
       drv.full_name                                AS driver_name,
       stop.id                                      AS stop_id,
       stop.code                                    AS stop_code,
       stop.name                                    AS stop_name,
       stop.stop_type                               AS stop_type,
       tst.stop_order                               AS stop_order,
       tst.scheduled_arrival_at                     AS scheduled_arrival_at,
       tst.scheduled_departure_at                   AS scheduled_departure_at,
       tst.actual_arrival_at                        AS actual_arrival_at,
       tst.actual_departure_at                      AS actual_departure_at,
       -- Desvío de llegada en minutos. NULL si aún no hay llegada real.
       -- Negativo = llegó antes de lo programado; positivo = llegó tarde.
       TIMESTAMPDIFF(MINUTE, tst.scheduled_arrival_at, tst.actual_arrival_at)
                                                    AS arrival_delay_minutes,
       -- Clasificación lista para colorear/exportar sin recalcular en el
       -- cliente. PENDING = el conductor todavía no marcó la llegada.
       CASE
           WHEN tst.actual_arrival_at IS NULL THEN 'PENDING'
           WHEN tst.actual_arrival_at <= tst.scheduled_arrival_at THEN 'ON_TIME'
           ELSE 'LATE'
       END                                          AS arrival_classification,
       -- Turno del viaje, derivado de la hora de salida (ver nota arriba).
       -- ASCII a propósito (MANANA) para que el filtro del frontend viaje
       -- sin sorpresas de encoding; la etiqueta con tilde la pone la UI.
       CASE
           WHEN HOUR(trip.scheduled_start_at) < 6  THEN 'MADRUGADA'
           WHEN HOUR(trip.scheduled_start_at) < 12 THEN 'MANANA'
           WHEN HOUR(trip.scheduled_start_at) < 18 THEN 'TARDE'
           ELSE 'NOCHE'
       END                                          AS time_slot,
       tst.status                                   AS stop_status
  FROM trip_stop_times tst
  JOIN trip_instances trip    ON trip.id = tst.trip_id
  JOIN transport_routes route ON route.id = trip.route_id
  JOIN vehicles veh           ON veh.id = trip.vehicle_id
  JOIN users drv              ON drv.id = trip.driver_id
  JOIN transport_stops stop   ON stop.id = tst.stop_id;
