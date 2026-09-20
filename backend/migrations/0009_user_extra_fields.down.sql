-- ============================================================================
-- 0009_user_extra_fields.down.sql
-- Revierte los cambios de 0009_user_extra_fields.up.sql.
-- ============================================================================


ALTER TABLE users DROP INDEX idx_users_username;
ALTER TABLE users DROP CONSTRAINT uq_users_username;
ALTER TABLE users
    DROP COLUMN site,
    DROP COLUMN management,
    DROP COLUMN personal_email,
    DROP COLUMN ceco,
    DROP COLUMN username;