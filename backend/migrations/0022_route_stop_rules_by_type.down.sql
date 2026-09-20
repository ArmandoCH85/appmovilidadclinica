-- ============================================================================
-- 0022_route_stop_rules_by_type.down.sql
-- Revierte a la política previa: sin subida en paraderos VUELTA y sin bajada
-- en sedes intermedias VUELTA.
-- ============================================================================

UPDATE route_stops rs
JOIN transport_routes r ON r.id = rs.route_id
JOIN transport_stops s ON s.id = rs.stop_id
   SET rs.pickup_allowed = 0
 WHERE r.direction = 'VUELTA'
   AND s.stop_type = 'PARADERO';

UPDATE route_stops rs
JOIN transport_routes r ON r.id = rs.route_id
JOIN transport_stops s ON s.id = rs.stop_id
   SET rs.dropoff_allowed = 0
 WHERE r.direction = 'VUELTA'
   AND s.stop_type = 'SEDE'
   AND rs.stop_order > 1;
