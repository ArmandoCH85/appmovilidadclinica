-- =============================================================
-- 0012_allow_sede_dropoff_vuelta.up.sql
-- Permite que las sedes intermedias en VUELTA (San Borja, Westin)
-- permitan dropoff (bajada de pasajeros), no solo pickup. Antes
-- solo Lima (destino final) y paraderos permitian bajada.
-- =============================================================
UPDATE route_stops rs
  JOIN transport_routes r ON r.id = rs.route_id
  JOIN transport_stops s ON s.id = rs.stop_id
   SET rs.dropoff_allowed = 1
 WHERE r.direction = 'VUELTA'
   AND s.code IN ('SEDE_SAN_BORJA', 'SEDE_SI_WESTIN');
