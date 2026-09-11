-- =============================================================
-- 0013_guest_occupants.down.sql
-- =============================================================
DROP PROCEDURE IF EXISTS sp_register_guest_occupant;
ALTER TABLE trip_seat_segments DROP FOREIGN KEY fk_trip_seat_segments_guest;
ALTER TABLE trip_seat_segments DROP COLUMN guest_occupant_id;
DROP TABLE IF EXISTS guest_occupants;
