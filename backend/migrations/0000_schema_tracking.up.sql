-- ============================================================================
-- 0000_schema_tracking.up.sql
-- Crea la tabla de control de migraciones y registra todas las migraciones
-- existentes como aplicadas. Es el bootstrap del nuevo sistema de tracking
-- que reemplaza el esquema "todo se corre en cada arranque".
--
-- Por que este archivo existe:
--   Antes de este cambio, 0001_schema.up.sql contenia 22 DROP TABLE IF EXISTS
--   al inicio, y migrate.go ejecutaba todos los .up.sql en cada restart. Esto
--   borraba TODOS los datos del usuario en cada restart. Para resolverlo,
--   migrate.go ahora consulta schema_migrations y solo aplica archivos no
--   registrados.
--
--   El primer arranque despues de este deploy hara lo siguiente:
--     1. Crea schema_migrations (CREATE TABLE IF NOT EXISTS en migrate.go)
--     2. Lee las versiones aplicadas (vacio, primera vez)
--     3. Aplica 0000 (este archivo):
--        - CREATE TABLE IF NOT EXISTS schema_migrations (no-op)
--        - INSERT IGNORE de todas las versiones existentes (0001..0006)
--     4. migrate.go registra 0000 como aplicada
--     5. Itera 0001..0006: ya estan en la tabla, las SALTA
--     6. Tablas y datos del usuario: intactos
--
--   En arranques subsiguientes, schema_migrations tiene 0000..0006, asi que
--   NINGUNA migration se ejecuta y los datos sobreviven.
--
-- Notas:
--   * INSERT IGNORE es defensivo: si por algun motivo el archivo corre mas
--     de una vez, no falla por PK duplicada.
--   * Esta migration no usa DROP/CREATE de tablas del usuario. Solo crea
--     schema_migrations (con IF NOT EXISTS) y la puebla.
-- ============================================================================

CREATE TABLE IF NOT EXISTS schema_migrations (
    version    VARCHAR(255) NOT NULL PRIMARY KEY,
    applied_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Registrar como aplicadas todas las migraciones que existian antes de este
-- cambio. Asi, en una BD pre-existente, NO se vuelven a correr (no se borran
-- las tablas del usuario).
INSERT IGNORE INTO schema_migrations (version) VALUES
    ('0001_schema'),
    ('0002_cancel_sps'),
    ('0003_active_reservation_guard'),
    ('0004_new_reports'),
    ('0005_user_reservation_activity'),
    ('0006_user_reservation_activity_filters');