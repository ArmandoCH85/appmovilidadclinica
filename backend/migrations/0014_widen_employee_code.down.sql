-- ============================================================================
-- 0014_widen_employee_code.down.sql
-- Rollback: Legajo vuelve a VARCHAR(30).
--
-- ATENCION: falla si existe algun employee_code con mas de 30 caracteres
-- (los 2 usuarios de la planilla masiva). En ese caso, primero reasignar
-- o dar de baja esos legajos.
-- ============================================================================

USE transporte_corporativo_mvp;

ALTER TABLE users MODIFY COLUMN employee_code VARCHAR(30) NOT NULL;
