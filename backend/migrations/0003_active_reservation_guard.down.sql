-- Revierte 0003_active_reservation_guard.up.sql
ALTER TABLE reservations DROP INDEX IF EXISTS uq_reservations_active_per_trip_worker;
ALTER TABLE reservations DROP COLUMN IF EXISTS active_worker_id;
