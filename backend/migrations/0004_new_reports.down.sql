-- ============================================================================
-- 0004_new_reports.down.sql
-- Reversa las 6 vistas creadas en 0004_new_reports.up.sql.
-- ============================================================================

DROP VIEW IF EXISTS vw_route_occupancy;
DROP VIEW IF EXISTS vw_trips_status_summary;
DROP VIEW IF EXISTS vw_duration_deviation;
DROP VIEW IF EXISTS vw_delays_by_route_day;
DROP VIEW IF EXISTS vw_reservation_changes;
DROP VIEW IF EXISTS vw_trip_incidents;