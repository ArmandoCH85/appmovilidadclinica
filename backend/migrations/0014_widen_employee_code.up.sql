-- ============================================================================
-- 0014_widen_employee_code.up.sql
-- Legajo (employee_code) 30 -> 60 caracteres.
--
-- Motivo: la planilla masiva de usuarios (3.199) trae usernames de hasta
-- 32 caracteres que tambien son Legajo (Legajo = Usuario). Con VARCHAR(30)
-- 2 usuarios no entraban:
--   100367 lic.radiologia.arcayo.quinteros (31)
--   102602 fiorella.estefany.guerrero.suica (32)
-- `username` ya es VARCHAR(60), asi que 60 iguala ambas columnas.
--
-- Seguro online: solo ensancha un VARCHAR en tabla pequena (~3k filas),
-- no toca el UNIQUE uq_users_employee_code ni datos existentes.
-- Trackeada por schema_migrations (0000..0013 ya aplicadas).
-- ============================================================================


ALTER TABLE users MODIFY COLUMN employee_code VARCHAR(60) NOT NULL;
