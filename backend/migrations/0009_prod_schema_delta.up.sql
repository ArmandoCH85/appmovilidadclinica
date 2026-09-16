-- ============================================================================
-- 0009_prod_schema_delta.up.sql
-- Delta de esquema idempotente para bases de PRODUCCION con datos.
--
-- Por que existe: 0001_schema.up.sql hace DROP TABLE + CREATE TABLE de todo en
-- cada arranque. En un servidor con datos reales NO se puede correr 0001.
-- Este archivo aplica SOLO los cambios de esquema que la feature de extension
-- de viaje necesita, sin tocar datos.
--
-- Requiere MariaDB (usa ADD COLUMN IF NOT EXISTS). El server de produccion es
-- MariaDB 11.4.5. En MySQL 8.0 puro no funciona: usar el guard de 0003.
-- ============================================================================

-- 1. Columna que guarda el destino original antes de la primera extension.
ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS original_destination_stop_order SMALLINT UNSIGNED NULL;

-- 2. Vista de disponibilidad enriquecida (DROP + CREATE es idempotente).
DROP VIEW IF EXISTS vw_trip_segment_seat_availability;

CREATE VIEW vw_trip_segment_seat_availability AS
SELECT trip.id AS trip_id,
       trip.trip_code,
       trip.service_date,
       route.direction,
       seat.id AS trip_seat_id,
       seat.seat_number,
       seat.seat_label,
       segment.segment_order,
       from_place.name AS available_or_occupied_from,
       to_place.name AS available_or_occupied_until,
       inventory.state,
       inventory.reservation_id,
       reservation.reservation_code,
       inventory.reserved_at,
       inventory.released_at,
       CASE WHEN reservation.original_destination_stop_order IS NOT NULL
            THEN 1 ELSE 0 END AS reservation_extended,
       original_place.name AS original_destination_name,
       current_place.name AS current_destination_name
  FROM trip_seat_segments inventory
  JOIN trip_seats seat
    ON seat.id = inventory.trip_seat_id
  JOIN trip_instances trip
    ON trip.id = seat.trip_id
  JOIN transport_routes route
    ON route.id = trip.route_id
  JOIN trip_segments segment
    ON segment.id = inventory.trip_segment_id
  JOIN trip_stop_times from_stop
    ON from_stop.id = segment.from_trip_stop_time_id
  JOIN trip_stop_times to_stop
    ON to_stop.id = segment.to_trip_stop_time_id
  JOIN transport_stops from_place
    ON from_place.id = from_stop.stop_id
  JOIN transport_stops to_place
    ON to_place.id = to_stop.stop_id
  LEFT JOIN reservations reservation
    ON reservation.id = inventory.reservation_id
  LEFT JOIN trip_stop_times original_stop
    ON original_stop.trip_id = trip.id
   AND original_stop.stop_order = reservation.original_destination_stop_order
  LEFT JOIN transport_stops original_place
    ON original_place.id = original_stop.stop_id
  LEFT JOIN trip_stop_times current_stop
    ON current_stop.id = reservation.destination_trip_stop_time_id
  LEFT JOIN transport_stops current_place
    ON current_place.id = current_stop.stop_id;
