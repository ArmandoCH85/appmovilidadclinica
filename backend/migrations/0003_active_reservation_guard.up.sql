-- ============================================================================
-- 0003_active_reservation_guard.up.sql (FIX MySQL 8.0)
-- Backstop de BD para la regla "1 reserva activa por trabajador por viaje".
--
-- NOTA DE COMPATIBILIDAD: la version original usaba `ADD COLUMN IF NOT
-- EXISTS` con `GENERATED ALWAYS AS ... STORED`. Esa sintaxis NO esta
-- soportada en MySQL 8.0 puro (solo en MariaDB). Este fix reemplaza esa
-- sentencia por un SELECT contra INFORMATION_SCHEMA + ALTER CONDICIONAL,
-- que es portable a MySQL 8.0, MariaDB y Percona.
--
-- Requisito previo: 0001_schema.up.sql y 0002_cancel_sps.up.sql aplicados
-- (el status CANCELLED debe existir en el ENUM).
-- ============================================================================

USE transporte_corporativo_mvp;

-- Solo agregar la columna si NO existe ya (portable MySQL 8.0 / MariaDB).
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'reservations'
       AND COLUMN_NAME = 'active_worker_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE reservations ADD COLUMN active_worker_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status IN (''CONFIRMED'', ''BOARDED'') THEN worker_id ELSE NULL END) STORED',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Solo agregar el indice si NO existe.
SET @idx_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'reservations'
       AND INDEX_NAME = 'uq_reservations_active_per_trip_worker'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE reservations ADD UNIQUE INDEX uq_reservations_active_per_trip_worker (trip_id, active_worker_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;