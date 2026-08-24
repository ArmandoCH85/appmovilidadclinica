-- ============================================================================
-- 0000_schema_tracking.down.sql
-- Revierte la tabla schema_migrations. ADVERTENCIA: ejecutar este down NO
-- restaura el comportamiento "todo se corre en cada arranque" — eso lo
-- controla migrate.go. Pero al ejecutar este down, el proximo arranque
-- del backend (que SI ejecuta migrate.go) va a:
--   1. Re-crear la tabla (CREATE TABLE IF NOT EXISTS en migrate.go)
--   2. No encontrarla registrada (aca la acabamos de borrar)
--   3. Re-aplicar 0000 (no-op + INSERT de 0001..0006)
--   4. Skipear 0001..0006
--
-- En resumen: este down es seguro. Pero si lo que queres es "volver al
-- esquema que borraba todo en cada arranque", tenes que revertir tambien
-- los cambios en migrate.go (commit donde se introduce schema_migrations).
-- ============================================================================

DROP TABLE IF EXISTS schema_migrations;