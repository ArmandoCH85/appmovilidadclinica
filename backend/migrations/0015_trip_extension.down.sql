-- Revert de 0015_trip_extension.up.sql.
DROP VIEW IF EXISTS vw_trip_segment_seat_availability;

ALTER TABLE reservations
    DROP COLUMN IF EXISTS original_destination_stop_order;

ALTER TABLE reservation_events
    MODIFY COLUMN event_type ENUM(
        'CONFIRMED', 'BOARDED', 'ALIGHTED', 'NO_SHOW',
        'SEGMENTS_RELEASED', 'CANCELLED'
    ) NOT NULL;
