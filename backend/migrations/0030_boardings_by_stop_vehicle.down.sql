-- ============================================================================
-- 0030_boardings_by_stop_vehicle.down.sql
-- Vuelve vw_boardings_by_stop a la definicion de 0029 (nombres de pasajeros,
-- sin vehiculo). El reporte deja de poder filtrarse por placa.
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
               u.full_name    AS app_name,
               NULL           AS guest_name,
               1              AS app_passengers,
               0              AS guest_passengers
          FROM reservations res
          JOIN trip_instances t ON t.id = res.trip_id
          JOIN transport_routes r ON r.id = t.route_id
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
               NULL AS app_name,
               CONCAT(g.first_name, ' ', g.last_name) AS guest_name,
               0,
               1
          FROM guest_occupants g
          JOIN trip_instances t ON t.id = g.trip_id
          JOIN transport_routes r ON r.id = t.route_id
          JOIN trip_stop_times tst
            ON tst.trip_id = g.trip_id
           AND tst.stop_order = g.origin_stop_order
          JOIN transport_stops s ON s.id = tst.stop_id
         WHERE g.status IN ('BOARDED', 'COMPLETED')
       ) x
 GROUP BY service_date, route_id, route_code, route_name, direction,
          stop_id, stop_name, stop_type;
