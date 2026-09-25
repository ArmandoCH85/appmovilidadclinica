-- ============================================================================
-- 0031_trip_incidents_reporter_role.down.sql
-- Vuelve vw_trip_incidents a la definicion de 0004 (sin reported_by_role).
-- ============================================================================
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
