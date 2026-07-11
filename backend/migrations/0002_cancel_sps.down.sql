-- ============================================================================
-- 0002_cancel_sps.down.sql
-- Revierte los SPs de cancelacion y los ENUM ampliados por 0002.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_cancel_trip;
DROP PROCEDURE IF EXISTS sp_cancel_reservation;

-- Restaura el ENUM de eventos sin 'CANCELLED'.
ALTER TABLE reservation_events
    MODIFY COLUMN event_type ENUM(
        'CONFIRMED', 'BOARDED', 'ALIGHTED', 'NO_SHOW', 'SEGMENTS_RELEASED'
    ) NOT NULL;

-- Restaura el ENUM de reservas sin 'CANCELLED'.
ALTER TABLE reservations
    MODIFY COLUMN status ENUM(
        'CONFIRMED', 'BOARDED', 'COMPLETED', 'NO_SHOW'
    ) NOT NULL DEFAULT 'CONFIRMED';
