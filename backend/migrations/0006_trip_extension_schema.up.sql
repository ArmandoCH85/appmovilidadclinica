-- ============================================================================
-- 0006_trip_extension_schema.up.sql
-- Extensión de viaje del pasajero: agrega el evento EXTENDED al ENUM.
-- Requisito previo: 0001..0005 aplicados. Nota: el ENUM de reservation_events
-- ya fue redefinido por 0002, que corre DESPUÉS de 0001 — por eso este ALTER
-- va en un archivo posterior y no en el DDL base.
-- ============================================================================

ALTER TABLE reservation_events
    MODIFY COLUMN event_type ENUM(
        'CONFIRMED', 'BOARDED', 'ALIGHTED', 'NO_SHOW',
        'SEGMENTS_RELEASED', 'CANCELLED', 'EXTENDED'
    ) NOT NULL;
