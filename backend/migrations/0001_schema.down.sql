-- ============================================================================
-- 0001_schema.down.sql -- Reversión completa del schema MVP
-- Elimina en orden inverso de dependencia: vistas → SPs → funciones → tablas.
-- Finalmente elimina la base de datos por completo.
-- ============================================================================

DROP VIEW IF EXISTS vw_schedule_conflicts;
DROP VIEW IF EXISTS vw_trip_segment_seat_availability;
DROP VIEW IF EXISTS vw_route_time_matrix;

DROP PROCEDURE IF EXISTS sp_mark_reservation_alighted;
DROP PROCEDURE IF EXISTS sp_mark_reservation_no_show;
DROP PROCEDURE IF EXISTS sp_mark_reservation_boarded;
DROP PROCEDURE IF EXISTS sp_mark_reservation_boarded_self;
DROP PROCEDURE IF EXISTS sp_mark_trip_stop_arrival;
DROP PROCEDURE IF EXISTS sp_confirm_reservation;
DROP PROCEDURE IF EXISTS sp_list_trip_seats;
DROP PROCEDURE IF EXISTS sp_search_trips;
DROP PROCEDURE IF EXISTS sp_generate_trip_instance;

DROP FUNCTION IF EXISTS fn_select_travel_time_profile;
DROP FUNCTION IF EXISTS fn_service_operates;

SET FOREIGN_KEY_CHECKS = 0;

-- Orden inverso de dependencia: dependientes antes que padres.
DROP TABLE IF EXISTS trip_incidents;
DROP TABLE IF EXISTS reservation_events;
DROP TABLE IF EXISTS reservation_segments;
DROP TABLE IF EXISTS trip_seat_segments;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS trip_seats;
DROP TABLE IF EXISTS trip_segments;
DROP TABLE IF EXISTS trip_stop_times;
DROP TABLE IF EXISTS trip_instances;
DROP TABLE IF EXISTS trip_generation_runs;
DROP TABLE IF EXISTS trip_templates;
DROP TABLE IF EXISTS service_calendar_exceptions;
DROP TABLE IF EXISTS service_calendars;
DROP TABLE IF EXISTS route_segment_travel_times;
DROP TABLE IF EXISTS travel_time_profiles;
DROP TABLE IF EXISTS route_segments;
DROP TABLE IF EXISTS route_stops;
DROP TABLE IF EXISTS transport_routes;
DROP TABLE IF EXISTS vehicle_seats;
DROP TABLE IF EXISTS vehicles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS transport_stops;

SET FOREIGN_KEY_CHECKS = 1;

DROP DATABASE IF EXISTS transporte_corporativo_mvp;