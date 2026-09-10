-- =============================================================
-- 0012_allow_sede_dropoff_vuelta.down.sql
-- Revierte: San Borja y Westin en VUELTA vuelven a dropoff=0.
-- =============================================================
UPDATE route_stops rs
  JOIN transport_routes r ON r.id = rs.route_id
  JOIN transport_stops s ON s.id = rs.stop_id
   SET rs.dropoff_allowed = 0
 WHERE r.direction = 'VUELTA'
   AND s.code IN ('SEDE_SAN_BORJA', 'SEDE_SI_WESTIN');
