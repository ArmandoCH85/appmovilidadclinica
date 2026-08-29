-- ============================================================================
-- 0005_user_extra_fields.up.sql
-- Cambios pedidos por el cliente:
--   DNI         -> ya existe como document_number (sin cambios)
--   Usuario     -> NUEVO username (login alternativo, NULL permitido)
--   CeCo        -> NUEVO ceco (centro de costo, NULL permitido)
--   Correo Pers -> NUEVO personal_email (NULL permitido)
--   Area        -> ya existe como department (sin cambios)
--   Gerencia    -> NUEVO management (NULL permitido)
--   SEDE        -> NUEVO site (NULL permitido)
--   Celular     -> ya existe como phone (sin cambios)
--
-- Estrategia: agregar columnas NULL con UNIQUE solo en username. Ningún
-- usuario existente se rompe. El admin web y los scripts internos pueden
-- luego popular los campos via UI o migración opcional.
-- ============================================================================

USE transporte_corporativo_mvp;

ALTER TABLE users
    ADD COLUMN username       VARCHAR(60)  NULL AFTER document_number,
    ADD COLUMN ceco           VARCHAR(30)  NULL AFTER department,
    ADD COLUMN personal_email VARCHAR(150) NULL AFTER phone,
    ADD COLUMN management     VARCHAR(100) NULL AFTER personal_email,
    ADD COLUMN site           VARCHAR(100) NULL AFTER management;

-- Username único pero con varios NULL permitidos (regla MySQL: NULL no
-- cuenta como duplicado en UNIQUE). Asi usuarios viejos sin username
-- conviven con usuarios nuevos que si lo tienen.
ALTER TABLE users
    ADD CONSTRAINT uq_users_username UNIQUE (username);

-- Indice para acelerar el lookup por username en el login.
CREATE INDEX idx_users_username ON users (username);