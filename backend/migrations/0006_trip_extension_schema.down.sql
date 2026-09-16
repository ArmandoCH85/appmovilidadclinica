ALTER TABLE reservation_events
    MODIFY COLUMN event_type ENUM(
        'CONFIRMED', 'BOARDED', 'ALIGHTED', 'NO_SHOW',
        'SEGMENTS_RELEASED', 'CANCELLED'
    ) NOT NULL;
