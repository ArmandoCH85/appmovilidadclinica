-- =============================================================
-- 0011_allow_paradero_pickup_vuelta.down.sql
-- Revierte el cambio: paraderos de VUELTA vuelven a pickup=0.
-- =============================================================
DROP PROCEDURE IF EXISTS sp_search_trips;
DELIMITER $$
CREATE PROCEDURE sp_search_trips(
    IN p_service_date DATE,
    IN p_direction VARCHAR(10),
    IN p_origin_stop_id BIGINT UNSIGNED,
    IN p_destination_stop_id BIGINT UNSIGNED
)
BEGIN
    SELECT trip.id AS trip_id,
           trip.trip_code,
           route.code AS route_code,
           route.name AS route_name,
           route.direction,
           origin_stop.stop_order AS origin_order,
           origin_place.name AS origin_name,
           origin_stop.scheduled_departure_at AS origin_departure_at,
           destination_stop.stop_order AS destination_order,
           destination_place.name AS destination_name,
           destination_stop.scheduled_arrival_at AS destination_arrival_at,
           vehicle.internal_code AS vehicle_code,
           vehicle.plate,
           trip.booking_opens_at,
           trip.booking_closes_at,
           CASE
               WHEN CURRENT_TIMESTAMP < trip.booking_opens_at THEN 'NOT_OPEN'
               WHEN CURRENT_TIMESTAMP >= trip.booking_closes_at THEN 'CLOSED'
               ELSE 'OPEN'
           END AS booking_state,
           (
               SELECT COUNT(*)
                 FROM trip_seats candidate_seat
                WHERE candidate_seat.trip_id = trip.id
                  AND candidate_seat.is_blocked = 0
                  AND NOT EXISTS (
                      SELECT 1
                        FROM trip_segments requested_segment
                        JOIN trip_seat_segments inventory
                          ON inventory.trip_segment_id = requested_segment.id
                         AND inventory.trip_seat_id = candidate_seat.id
                       WHERE requested_segment.trip_id = trip.id
                         AND requested_segment.segment_order >= origin_stop.stop_order
                         AND requested_segment.segment_order < destination_stop.stop_order
                         AND inventory.state <> 'AVAILABLE'
                  )
           ) AS available_seats
      FROM trip_instances trip
      JOIN transport_routes route
        ON route.id = trip.route_id
      JOIN trip_stop_times origin_stop
        ON origin_stop.trip_id = trip.id
       AND origin_stop.stop_id = p_origin_stop_id
      JOIN trip_stop_times destination_stop
        ON destination_stop.trip_id = trip.id
       AND destination_stop.stop_id = p_destination_stop_id
      JOIN route_stops origin_rule
        ON origin_rule.id = origin_stop.route_stop_id
      JOIN route_stops destination_rule
        ON destination_rule.id = destination_stop.route_stop_id
      JOIN transport_stops origin_place
        ON origin_place.id = origin_stop.stop_id
      JOIN transport_stops destination_place
        ON destination_place.id = destination_stop.stop_id
      JOIN vehicles vehicle
        ON vehicle.id = trip.vehicle_id
     WHERE trip.service_date = p_service_date
       AND route.direction = p_direction
       AND trip.status = 'PUBLISHED'
       AND origin_stop.stop_order < destination_stop.stop_order
       AND origin_rule.pickup_allowed = 1
       AND destination_rule.dropoff_allowed = 1
       AND (
            (route.direction = 'IDA' AND destination_place.stop_type = 'SEDE')
            OR (route.direction = 'VUELTA' AND origin_place.stop_type = 'SEDE')
       )
     ORDER BY origin_stop.scheduled_departure_at;
END$$
DELIMITER ;
UPDATE route_stops rs
  JOIN transport_routes r ON r.id = rs.route_id
  JOIN transport_stops s ON s.id = rs.stop_id
   SET rs.pickup_allowed = 0
 WHERE r.direction = 'VUELTA'
   AND s.code IN ('PAR_PTE_PRIMAVERA', 'PAR_ANGAMOS', 'PAR_SB_NORTE');
