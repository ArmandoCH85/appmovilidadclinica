-- ============================================================================
-- assign_bus_drivers.sql
-- ----------------------------------------------------------------------------
-- Asigna los conductores reales a la operación:
--   BUS-25 (vehicle 2) -> FREDI ANTONIO VASQUEZ ALMEIDA
--   BUS-16 (vehicle 1) -> AURELIO CRUZ PERALTA
--
-- El conductor va con SU BUS durante toda la rotación (mañana + noche), que es
-- lo único físicamente viable: el que termina en Surco no puede salir de Lima
-- 5 minutos después.
--
-- Reutiliza los 2 usuarios DRIVER placeholder (18=COND-IDA, 19=COND-VUELTA).
-- Documento / usuario / contraseña quedan como estaban: cargar los reales.
--
-- Uso:  mariadb -u root transporte_corporativo_mvp < assign_bus_drivers.sql
-- ============================================================================

USE transporte_corporativo_mvp;

START TRANSACTION;

-- 1) Nombres reales de los conductores
UPDATE users SET full_name = 'FREDI ANTONIO VASQUEZ ALMEIDA', employee_code = 'COND-25' WHERE id = 18;
UPDATE users SET full_name = 'AURELIO CRUZ PERALTA',         employee_code = 'COND-16' WHERE id = 19;

-- 2) Asignación por bus (no por sentido)
--    BUS-25 (veh 2): TPL 1, 4, 5, 8  -> conductor 18 (Fredi)
UPDATE trip_templates SET default_driver_id = 18 WHERE id IN (1,4,5,8);
--    BUS-16 (veh 1): TPL 2, 3, 6, 7  -> conductor 19 (Aurelio)
UPDATE trip_templates SET default_driver_id = 19 WHERE id IN (2,3,6,7);

-- 3) Coherencia en los viajes ya generados (la marcha blanca los borra igual)
UPDATE trip_instances
   SET driver_id = CASE WHEN vehicle_id = 2 THEN 18 ELSE 19 END
 WHERE service_date >= CURDATE();

-- 4) El bus es de 16 asientos: BUS-15 -> BUS-16 en las plantillas
UPDATE trip_templates
   SET code = REPLACE(code, 'BUS15', 'BUS16'),
       name = REPLACE(name, 'BUS-15', 'BUS-16')
 WHERE code LIKE '%BUS15%';

COMMIT;
