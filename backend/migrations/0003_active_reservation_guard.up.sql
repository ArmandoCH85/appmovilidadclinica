-- ============================================================================
-- 0003_active_reservation_guard.up.sql
-- Backstop de BD para la regla "1 reserva activa por trabajador por viaje".
--
-- Go valida esta regla con un SELECT EXISTS (booking/repository.go,
-- CheckActiveReservation) ANTES de llamar a sp_confirm_reservation. Esa
-- validacion dejaba una ventana de carrera: dos requests concurrentes del
-- mismo trabajador para el mismo viaje pueden pasar ambas el chequeo antes de
-- que cualquiera inserte la reserva.
--
-- active_worker_id es NULL salvo que status sea CONFIRMED o BOARDED (activo).
-- MySQL/MariaDB no cuenta NULLs como duplicados en un indice UNIQUE, asi que
-- el indice solo choca cuando el MISMO trabajador tiene DOS reservas activas
-- simultaneas en el MISMO viaje -- exactamente la regla de negocio. Reservas
-- CANCELLED/NO_SHOW/COMPLETED no participan (pueden coexistir muchas por
-- trabajador+viaje sin chocar), preservando la posibilidad de re-reservar
-- tras cancelar.
--
-- Requisito previo: 0001_schema.up.sql y 0002_cancel_sps.up.sql aplicados
-- (el status CANCELLED debe existir en el ENUM).
-- ============================================================================

ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS active_worker_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN status IN ('CONFIRMED', 'BOARDED') THEN worker_id ELSE NULL END
        ) STORED;

ALTER TABLE reservations
    ADD UNIQUE INDEX IF NOT EXISTS uq_reservations_active_per_trip_worker
    (trip_id, active_worker_id);
