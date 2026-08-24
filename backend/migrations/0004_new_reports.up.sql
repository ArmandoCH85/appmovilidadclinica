-- ============================================================================
-- 0004_new_reports.up.sql
-- Vistas SQL de solo lectura para los reportes:
--   #5  Ocupación por ruta
--   #11 Viajes completados vs cancelados
--   #12 Duración real vs estimada
--   #13 Retrasos por ruta/día
--   #26 Cambios en reservas (historial de eventos)
--   #27 Tickets / quejas de usuarios (incidentes de viaje)
-- Cada vista está pensada para que el repositorio Go la consuma directo,
-- con WHERE opcionales que matchean columnas existentes (mismo patrón que
-- vw_schedule_conflicts / vw_route_time_matrix).
--
-- No usa DELIMITER: son CREATE VIEW, no stored procedures. El parser de
-- migraciones (internal/platform/database/migrate.go) corta por ";" cuando
-- no hay DELIMITER activo.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- #5 — Ocupación por ruta
-- Una fila por (ruta, dirección, fecha de servicio). Para cada viaje generado
-- en esa fecha sumamos los cupos ofrecidos (seat_capacity_snapshot) y los
-- cupos reservados (reservas CONFIRMED+BOARDED+COMPLETED, NO_SHOW se
-- cuenta como NO reservado porque la app libera los segmentos al pasarlo).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_route_occupancy AS
SELECT route.id                              AS route_id,
       route.code                            AS route_code,
       route.name                            AS route_name,
       route.direction                       AS direction,
       trip.service_date                     AS service_date,
       COUNT(DISTINCT trip.id)               AS trip_count,
       COALESCE(SUM(trip.seat_capacity_snapshot), 0) AS seats_offered,
       COALESCE((
           SELECT COUNT(*)
             FROM reservations r
            WHERE r.trip_id = trip.id
              AND r.status IN ('CONFIRMED', 'BOARDED', 'COMPLETED')
       ), 0)                                 AS seats_reserved,
       CASE
           WHEN COALESCE(SUM(trip.seat_capacity_snapshot), 0) = 0 THEN 0
           ELSE ROUND(
               COALESCE((
                   SELECT COUNT(*)
                     FROM reservations r
                    WHERE r.trip_id = trip.id
                      AND r.status IN ('CONFIRMED', 'BOARDED', 'COMPLETED')
               ), 0) * 100.0
               / SUM(trip.seat_capacity_snapshot),
               2
           )
       END                                   AS occupancy_pct
  FROM trip_instances trip
  JOIN transport_routes route
    ON route.id = trip.route_id
 GROUP BY route.id, route.code, route.name, route.direction, trip.service_date;

-- ----------------------------------------------------------------------------
-- #11 — Viajes completados vs cancelados (resumen por día)
-- Una fila por (fecha, status). Útil para barras stacked de "cómo termina
-- la operación". Filtros típicos: date_from, date_to, status.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_trips_status_summary AS
SELECT trip.service_date AS service_date,
       route.code        AS route_code,
       route.name        AS route_name,
       route.direction   AS direction,
       trip.status       AS status,
       COUNT(*)          AS trip_count
  FROM trip_instances trip
  JOIN transport_routes route
    ON route.id = trip.route_id
 GROUP BY trip.service_date, route.code, route.name, route.direction, trip.status;

-- ----------------------------------------------------------------------------
-- #12 — Duración real vs estimada de viaje
-- Compara TIMESTAMPDIFF entre scheduled_end_at-scheduled_start_at y
-- actual_end_at-actual_start_at. Solo incluye viajes con actual_start_at
-- y actual_end_at cargados (los que el conductor cerró).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_duration_deviation AS
SELECT trip.id                              AS trip_id,
       trip.trip_code                       AS trip_code,
       trip.service_date                    AS service_date,
       route.id                             AS route_id,
       route.code                           AS route_code,
       route.name                           AS route_name,
       route.direction                      AS direction,
       TIMESTAMPDIFF(MINUTE, trip.scheduled_start_at, trip.scheduled_end_at)
                                            AS scheduled_duration_minutes,
       TIMESTAMPDIFF(MINUTE, trip.actual_start_at, trip.actual_end_at)
                                            AS actual_duration_minutes,
       TIMESTAMPDIFF(MINUTE, trip.actual_start_at, trip.actual_end_at)
       - TIMESTAMPDIFF(MINUTE, trip.scheduled_start_at, trip.scheduled_end_at)
                                            AS delta_minutes,
       CASE
           WHEN TIMESTAMPDIFF(MINUTE, trip.scheduled_start_at, trip.scheduled_end_at) = 0 THEN NULL
           ELSE ROUND(
               (TIMESTAMPDIFF(MINUTE, trip.actual_start_at, trip.actual_end_at)
                - TIMESTAMPDIFF(MINUTE, trip.scheduled_start_at, trip.scheduled_end_at)) * 100.0
               / TIMESTAMPDIFF(MINUTE, trip.scheduled_start_at, trip.scheduled_end_at),
               2
           )
       END                                  AS delta_pct
  FROM trip_instances trip
  JOIN transport_routes route
    ON route.id = trip.route_id
 WHERE trip.actual_start_at IS NOT NULL
   AND trip.actual_end_at IS NOT NULL;

-- ----------------------------------------------------------------------------
-- #13 — Retrasos por ruta / día
-- Agrega la desviación del horario programado de salida
-- (actual_start_at vs scheduled_start_at) por (ruta, fecha). Útil para
-- detectar rutas que sistemáticamente arrancan tarde.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_delays_by_route_day AS
SELECT route.id                             AS route_id,
       route.code                           AS route_code,
       route.name                           AS route_name,
       route.direction                      AS direction,
       trip.service_date                    AS service_date,
       COUNT(*)                             AS trip_count,
       ROUND(AVG(TIMESTAMPDIFF(MINUTE, trip.scheduled_start_at, trip.actual_start_at)), 2)
                                            AS avg_delay_minutes,
       MAX(TIMESTAMPDIFF(MINUTE, trip.scheduled_start_at, trip.actual_start_at))
                                            AS max_delay_minutes,
       SUM(CASE WHEN trip.actual_start_at > trip.scheduled_start_at THEN 1 ELSE 0 END)
                                            AS late_trip_count,
       SUM(CASE WHEN trip.actual_start_at <= trip.scheduled_start_at THEN 1 ELSE 0 END)
                                            AS on_time_trip_count
  FROM trip_instances trip
  JOIN transport_routes route
    ON route.id = trip.route_id
 WHERE trip.actual_start_at IS NOT NULL
 GROUP BY route.id, route.code, route.name, route.direction, trip.service_date;

-- ----------------------------------------------------------------------------
-- #26 — Cambios en reservas (historial)
-- Espejo de reservation_events con datos legibles (código de reserva,
-- nombre del trabajador, nombre del actor que produjo el cambio). Una fila
-- por evento: CONFIRMED, BOARDED, ALIGHTED, NO_SHOW, SEGMENTS_RELEASED,
-- CANCELLED, etc.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_reservation_changes AS
SELECT re.id                                AS event_id,
       re.reservation_id                    AS reservation_id,
       reservation.reservation_code         AS reservation_code,
       re.event_type                        AS event_type,
       re.event_at                          AS event_at,
       re.actor_user_id                     AS actor_user_id,
       COALESCE(actor_user.full_name, '—')  AS actor_name,
       worker.id                            AS worker_id,
       COALESCE(worker.full_name, '—')      AS worker_name,
       trip.id                              AS trip_id,
       trip.trip_code                       AS trip_code,
       trip.service_date                    AS service_date,
       route.code                           AS route_code,
       re.trip_stop_time_id                 AS trip_stop_time_id,
       COALESCE(re.details, '')             AS details
  FROM reservation_events re
  JOIN reservations reservation
    ON reservation.id = re.reservation_id
  JOIN trip_instances trip
    ON trip.id = reservation.trip_id
  JOIN transport_routes route
    ON route.id = trip.route_id
  JOIN users worker
    ON worker.id = reservation.worker_id
  LEFT JOIN users actor_user
    ON actor_user.id = re.actor_user_id;

-- ----------------------------------------------------------------------------
-- #27 — Tickets / quejas de usuarios (incidentes de viaje)
-- Una fila por incidente. Tipos: BREAKDOWN, DELAY, ACCIDENT, OTHER.
-- Estados: OPEN, IN_REVIEW, RESOLVED. Permite filtrar por estado y tipo.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_trip_incidents AS
SELECT ti.id                                AS incident_id,
       ti.trip_id                           AS trip_id,
       trip.trip_code                       AS trip_code,
       trip.service_date                    AS service_date,
       route.id                             AS route_id,
       route.code                           AS route_code,
       route.name                           AS route_name,
       route.direction                      AS direction,
       ti.incident_type                     AS incident_type,
       ti.description                       AS description,
       ti.status                            AS status,
       ti.reported_by_user_id               AS reported_by_user_id,
       COALESCE(reporter.full_name, '—')    AS reported_by_name,
       ti.reported_at                       AS reported_at,
       ti.resolved_at                       AS resolved_at,
       COALESCE(ti.resolution_notes, '')    AS resolution_notes
  FROM trip_incidents ti
  JOIN trip_instances trip
    ON trip.id = ti.trip_id
  JOIN transport_routes route
    ON route.id = trip.route_id
  LEFT JOIN users reporter
    ON reporter.id = ti.reported_by_user_id;