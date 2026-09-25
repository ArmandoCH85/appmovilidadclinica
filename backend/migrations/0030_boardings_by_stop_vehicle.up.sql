-- ============================================================================
-- 0030_boardings_by_stop_vehicle.up.sql
-- Agrega el BUS (vehiculo) al reporte "Abordajes por parada" para poder
-- filtrar por placa.
--
-- Hasta 0029 la vista agregaba por (fecha, ruta, sentido, parada) y por eso
-- no habia forma de filtrar por placa: el bus desaparecia en el GROUP BY.
-- Esta migracion suma vehicle_id / vehicle_plate / vehicle_internal_code
-- al grano de la vista (una fila por fecha x ruta x sentido x parada x bus).
--
-- Efecto en el reporte: si una misma parada la sirven varios buses el mismo
-- dia, ahora aparece una fila por bus en vez de una sola sumada. Es el mismo
-- criterio que ya usa el reporte de "Llegadas por sede/paradero"
-- (vw_trip_stop_arrivals), que tambien es por vehiculo.
--
-- El bus sale de trip_instances.vehicle_id -> vehicles. Es NOT NULL y con FK,
-- asi que INNER JOIN no pierde filas.
-- ============================================================================

DROP VIEW IF EXISTS vw_boardings_by_stop;

CREATE VIEW vw_boardings_by_stop AS
SELECT service_date,
       route_id,
       route_code,
       route_name,
       direction,
       stop_id,
       stop_name,
       stop_type,
       vehicle_id,
       vehicle_plate,
       vehicle_internal_code,
       SUM(app_passengers)                         AS app_passengers,
       SUM(guest_passengers)                       AS guest_passengers,
       SUM(app_passengers) + SUM(guest_passengers) AS total_passengers,
       GROUP_CONCAT(app_name   ORDER BY app_name   SEPARATOR ', ') AS app_passenger_names,
       GROUP_CONCAT(guest_name ORDER BY guest_name SEPARATOR ', ') AS guest_passenger_names
  FROM (
        -- Reservas de la app con abordaje confirmado
        SELECT t.service_date AS service_date,
               r.id           AS route_id,
               r.code         AS route_code,
               r.name         AS route_name,
               r.direction    AS direction,
               s.id           AS stop_id,
               s.name         AS stop_name,
               s.stop_type    AS stop_type,
               v.id           AS vehicle_id,
               v.plate        AS vehicle_plate,
               v.internal_code AS vehicle_internal_code,
               u.full_name    AS app_name,
               NULL           AS guest_name,
               1              AS app_passengers,
               0              AS guest_passengers
          FROM reservations res
          JOIN trip_instances t ON t.id = res.trip_id
          JOIN transport_routes r ON r.id = t.route_id
          JOIN vehicles v ON v.id = t.vehicle_id
          JOIN trip_stop_times tst
            ON tst.trip_id = res.trip_id
           AND tst.stop_order = res.origin_stop_order
          JOIN transport_stops s ON s.id = tst.stop_id
          LEFT JOIN users u ON u.id = res.worker_id
         WHERE res.status IN ('BOARDED', 'COMPLETED')

        UNION ALL

        -- Invitados registrados por el conductor
        SELECT t.service_date,
               r.id,
               r.code,
               r.name,
               r.direction,
               s.id,
               s.name,
               s.stop_type,
               v.id,
               v.plate,
               v.internal_code,
               NULL AS app_name,
               CONCAT(g.first_name, ' ', g.last_name) AS guest_name,
               0,
               1
          FROM guest_occupants g
          JOIN trip_instances t ON t.id = g.trip_id
          JOIN transport_routes r ON r.id = t.route_id
          JOIN vehicles v ON v.id = t.vehicle_id
          JOIN trip_stop_times tst
            ON tst.trip_id = g.trip_id
           AND tst.stop_order = g.origin_stop_order
          JOIN transport_stops s ON s.id = tst.stop_id
         WHERE g.status IN ('BOARDED', 'COMPLETED')
       ) x
 GROUP BY service_date, route_id, route_code, route_name, direction,
          stop_id, stop_name, stop_type,
          vehicle_id, vehicle_plate, vehicle_internal_code;
