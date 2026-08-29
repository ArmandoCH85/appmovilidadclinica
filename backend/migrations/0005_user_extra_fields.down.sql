-- ============================================================================
-- 0005_user_extra_fields.down.sql
-- Rollback: elimina columnas agregadas por 0005_user_extra_fields.up.sql
-- y sus constraints/indices asociados.
-- ============================================================================

USE transporte_corporativo_mvp;

ALTER TABLE users
    DROP INDEX idx_users_username,
    DROP CONSTRAINT uq_users_username;

ALTER TABLE users
    DROP COLUMN site,
    DROP COLUMN management,
    DROP COLUMN personal_email,
    DROP COLUMN ceco,
    DROP COLUMN username;