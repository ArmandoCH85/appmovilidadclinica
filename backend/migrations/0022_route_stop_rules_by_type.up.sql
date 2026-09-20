-- ============================================================================
-- 0022_route_stop_rules_by_type.up.sql
-- Reemplaza las reglas de subida/bajada que quedaron atadas a CÓDIGOS de
-- parada (0011/0012: SEDE_SI_WESTIN, PAR_SB_NORTE, PAR_ANGAMOS, ...) por
-- reglas por TIPO de parada. Los códigos cambian entre ambientes (hoy la BD
-- real usa SEDE_SI_POSITIVA y PAR_SB_SUR), así que los UPDATE por código de
-- 0011/0012 no aplican a nada.
--
-- Regla de negocio VUELTA (vuelta a casa):
--   - Los PARADERO permiten SUBIDA (el trabajador toma el bus en la calle).
--   - Las SEDE intermedias (orden > 1) permiten BAJADA.
-- El origen (SEDE Surco, orden 1) ya permite subida desde el seed/0001.
-- ============================================================================

UPDATE route_stops rs
JOIN transport_routes r ON r.id = rs.route_id
JOIN transport_stops s ON s.id = rs.stop_id
   SET rs.pickup_allowed = 1
 WHERE r.direction = 'VUELTA'
   AND s.stop_type = 'PARADERO';

UPDATE route_stops rs
JOIN transport_routes r ON r.id = rs.route_id
JOIN transport_stops s ON s.id = rs.stop_id
   SET rs.dropoff_allowed = 1
 WHERE r.direction = 'VUELTA'
   AND s.stop_type = 'SEDE'
   AND rs.stop_order > 1;
