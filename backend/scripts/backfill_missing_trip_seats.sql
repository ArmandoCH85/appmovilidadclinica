-- ============================================================================
-- backfill_missing_trip_seats.sql
-- ----------------------------------------------------------------------------
-- Corrige viajes FUTUROS a los que les faltan asientos porque el vehículo
-- ganó un `vehicle_seat` DESPUÉS de que el viaje se generó. El generador
-- crea los `trip_seats` (y su inventario por tramo) al materializar el viaje
-- y salta los viajes ya existentes (`ALREADY_EXISTS`), así que los viajes
-- viejos quedan con menos asientos y con `seat_capacity_snapshot` viejo.
--
-- Qué hace (idempotente, por vehículo):
--   1. Inserta los `trip_seats` faltantes (los `vehicle_seats` no RETIRED que
--      el viaje no tiene), con is_blocked según el status del asiento.
--   2. Inserta los `trip_seat_segments` faltantes para TODOS los tramos del
--      viaje (sin esto el asiento aparece pero NO es reservable).
--   3. Actualiza `seat_capacity_snapshot` a la cantidad de asientos ACTIVE
--      del vehículo (mismo criterio que sp_generate_trip_instance).
--
-- NO toca viajes pasados. NO borra reservas. Se puede re-ejecutar sin daño.
--
-- Uso:
--   mariadb -u root transporte_corporativo_mvp < backfill_missing_trip_seats.sql
--   (ajustar @vehicle_code abajo)
-- ============================================================================

USE transporte_corporativo_mvp;

-- Forzar utf8mb4 en la conexion: sin esto el cliente puede conectar en
-- utf8mb3 y el COLLATE utf8mb4_unicode_ci de mas abajo falla.
SET NAMES utf8mb4;

SET @vehicle_code = 'CJI-579';
-- Colación explícita: la variable de usuario hereda utf8mb4_general_ci y la
-- columna es utf8mb4_unicode_ci; sin esto MySQL/MariaDB tira "Illegal mix of
-- collations" al comparar.

-- ---------------------------------------------------------------------------
-- 0) Estado ANTES
-- ---------------------------------------------------------------------------
SELECT 'ANTES' AS momento,
       t.id AS trip_id, t.trip_code, t.service_date, t.scheduled_start_at,
       t.seat_capacity_snapshot,
       (SELECT COUNT(*) FROM trip_seats ts WHERE ts.trip_id = t.id) AS trip_seats
  FROM trip_instances t
  JOIN vehicles v ON v.id = t.vehicle_id
 WHERE v.internal_code = @vehicle_code COLLATE utf8mb4_unicode_ci
   AND t.service_date >= CURDATE()
 ORDER BY t.service_date, t.scheduled_start_at;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 1) trip_seats faltantes
-- ---------------------------------------------------------------------------
INSERT INTO trip_seats (trip_id, vehicle_seat_id, seat_number, seat_label,
                        is_blocked, block_reason)
SELECT t.id, vs.id, vs.seat_number, vs.seat_label,
       CASE WHEN vs.status = 'BLOCKED' THEN 1 ELSE 0 END, vs.block_reason
  FROM trip_instances t
  JOIN vehicles v ON v.id = t.vehicle_id AND v.internal_code = @vehicle_code COLLATE utf8mb4_unicode_ci
  JOIN vehicle_seats vs ON vs.vehicle_id = t.vehicle_id AND vs.status <> 'RETIRED'
  LEFT JOIN trip_seats ts ON ts.trip_id = t.id AND ts.vehicle_seat_id = vs.id
 WHERE t.service_date >= CURDATE()
   AND ts.id IS NULL;

-- ---------------------------------------------------------------------------
-- 2) trip_seat_segments faltantes (inventario por tramo de cada asiento)
-- ---------------------------------------------------------------------------
INSERT INTO trip_seat_segments (trip_seat_id, trip_segment_id, state)
SELECT ts.id, seg.id,
       CASE WHEN ts.is_blocked = 1 THEN 'BLOCKED' ELSE 'AVAILABLE' END
  FROM trip_seats ts
  JOIN trip_instances t ON t.id = ts.trip_id
  JOIN vehicles v ON v.id = t.vehicle_id AND v.internal_code = @vehicle_code COLLATE utf8mb4_unicode_ci
  JOIN trip_segments seg ON seg.trip_id = t.id
  LEFT JOIN trip_seat_segments tss
         ON tss.trip_seat_id = ts.id AND tss.trip_segment_id = seg.id
 WHERE t.service_date >= CURDATE()
   AND tss.id IS NULL;

-- ---------------------------------------------------------------------------
-- 3) seat_capacity_snapshot = asientos ACTIVE del vehículo (criterio del SP)
-- ---------------------------------------------------------------------------
UPDATE trip_instances t
  JOIN vehicles v ON v.id = t.vehicle_id AND v.internal_code = @vehicle_code COLLATE utf8mb4_unicode_ci
   SET t.seat_capacity_snapshot = (
       SELECT COUNT(*) FROM vehicle_seats vs
        WHERE vs.vehicle_id = t.vehicle_id AND vs.status = 'ACTIVE'
   )
 WHERE t.service_date >= CURDATE();

COMMIT;

-- ---------------------------------------------------------------------------
-- 4) Estado DESPUÉS (debe quedar trip_seats = snapshot = asientos activos)
-- ---------------------------------------------------------------------------
SELECT 'DESPUES' AS momento,
       t.id AS trip_id, t.trip_code, t.service_date, t.scheduled_start_at,
       t.seat_capacity_snapshot,
       (SELECT COUNT(*) FROM trip_seats ts WHERE ts.trip_id = t.id) AS trip_seats
  FROM trip_instances t
  JOIN vehicles v ON v.id = t.vehicle_id
 WHERE v.internal_code = @vehicle_code COLLATE utf8mb4_unicode_ci
   AND t.service_date >= CURDATE()
 ORDER BY t.service_date, t.scheduled_start_at;
