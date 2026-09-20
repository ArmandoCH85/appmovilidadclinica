-- ============================================================================
-- reorder_bus_routes.sql
-- ----------------------------------------------------------------------------
-- Ordena el modelo de asignación de buses de la operación real:
--
--   1. Consolida las 8 rutas en 4 recorridos canónigos. Los "duplicados" NO
--      eran idénticos: compartían paradas pero llevaban tiempos de viaje
--      distintos por franja horaria.
--   2. Mueve esa variación a travel_time_profiles (franjas), que es para lo
--      que existe la matriz route_segment_travel_times.
--   3. Saca el bus y el turno del nombre de la RUTA; quedan solo en la
--      PLANTILLA. La ruta describe el recorrido + sentido.
--   4. Corrige el horario actualizado: BUS-15 mañana sale de Lima 07:00 y los
--      tramos de la mañana quedan 20/10/20.
--
-- Idempotente: se puede re-ejecutar. Aun así, hacer respaldo antes porque
-- borra las rutas duplicadas.
--
-- Uso:  mariadb -u root transporte_corporativo_mvp < reorder_bus_routes.sql
-- ============================================================================

USE transporte_corporativo_mvp;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 1) Perfiles por franja (la variación de tiempos deja de vivir en la ruta)
-- ---------------------------------------------------------------------------
INSERT INTO travel_time_profiles
    (code, name, start_time, end_time, is_all_day, priority, is_default, active)
VALUES
    ('AM_TEMPRANO', 'Franja AM temprano (05:00-07:00)', '05:00:00', '07:00:00', 0, 100, 0, 1),
    ('AM_PUNTA',    'Punta AM (07:00-09:00)',           '07:00:00', '09:00:00', 0, 100, 0, 1),
    ('PM_PUNTA',    'Punta PM (17:00-19:30)',           '17:00:00', '19:30:00', 0, 100, 0, 1),
    ('NOCHE',       'Noche (19:30-23:59)',              '19:30:00', '23:59:59', 0, 100, 0, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), start_time = VALUES(start_time), end_time = VALUES(end_time),
    is_all_day = VALUES(is_all_day), priority = VALUES(priority),
    is_default = VALUES(is_default), active = VALUES(active);

SET @p_amt = (SELECT id FROM travel_time_profiles WHERE code = 'AM_TEMPRANO');
SET @p_amp = (SELECT id FROM travel_time_profiles WHERE code = 'AM_PUNTA');
SET @p_pmp = (SELECT id FROM travel_time_profiles WHERE code = 'PM_PUNTA');
SET @p_noc = (SELECT id FROM travel_time_profiles WHERE code = 'NOCHE');

-- ---------------------------------------------------------------------------
-- 2) Matriz de tiempos por franja en las 4 rutas canónigas
--    route_segments: R1=12-15, R2=45-47, R3=20-22, R4=32-37
-- ---------------------------------------------------------------------------
-- R1 · Lima -> S.Isidro -> S.Borja -> La Cultura -> Surco  (solo AM temprano)
INSERT INTO route_segment_travel_times (route_segment_id, profile_id, travel_minutes, notes) VALUES
    (12, @p_amt, 20, 'R1 AM temprano'), (13, @p_amt, 10, 'R1 AM temprano'),
    (14, @p_amt,  5, 'R1 AM temprano'), (15, @p_amt, 20, 'R1 AM temprano')
ON DUPLICATE KEY UPDATE travel_minutes = VALUES(travel_minutes), notes = VALUES(notes);

-- R2 · Lima -> S.Isidro -> S.Borja -> Surco (directo)
INSERT INTO route_segment_travel_times (route_segment_id, profile_id, travel_minutes, notes) VALUES
    (45, @p_amp, 20, 'R2 punta AM'), (46, @p_amp, 10, 'R2 punta AM'), (47, @p_amp, 20, 'R2 punta AM'),
    (45, @p_pmp, 45, 'R2 punta PM'), (46, @p_pmp,  5, 'R2 punta PM'), (47, @p_pmp, 25, 'R2 punta PM'),
    (45, @p_noc, 15, 'R2 noche'),    (46, @p_noc, 20, 'R2 noche'),    (47, @p_noc, 15, 'R2 noche')
ON DUPLICATE KEY UPDATE travel_minutes = VALUES(travel_minutes), notes = VALUES(notes);

-- R3 · Surco -> S.Borja -> S.Isidro -> Lima (directo)
INSERT INTO route_segment_travel_times (route_segment_id, profile_id, travel_minutes, notes) VALUES
    (20, @p_amt, 20, 'R3 AM temprano'), (21, @p_amt, 10, 'R3 AM temprano'), (22, @p_amt, 20, 'R3 AM temprano'),
    (20, @p_amp, 20, 'R3 punta AM'),    (21, @p_amp,  5, 'R3 punta AM'),    (22, @p_amp, 20, 'R3 punta AM')
ON DUPLICATE KEY UPDATE travel_minutes = VALUES(travel_minutes), notes = VALUES(notes);

-- R4 · Surco -> Pte.Primavera -> Angamos -> S.B.Sur -> S.Borja -> S.Isidro -> Lima
INSERT INTO route_segment_travel_times (route_segment_id, profile_id, travel_minutes, notes) VALUES
    (32, @p_pmp, 5, 'R4 punta PM'), (33, @p_pmp, 10, 'R4 punta PM'), (34, @p_pmp, 5, 'R4 punta PM'),
    (35, @p_pmp, 5, 'R4 punta PM'), (36, @p_pmp,  5, 'R4 punta PM'), (37, @p_pmp, 30, 'R4 punta PM'),
    (32, @p_noc, 5, 'R4 noche'),    (33, @p_noc, 10, 'R4 noche'),    (34, @p_noc, 5, 'R4 noche'),
    (35, @p_noc, 5, 'R4 noche'),    (36, @p_noc,  5, 'R4 noche'),    (37, @p_noc, 20, 'R4 noche')
ON DUPLICATE KEY UPDATE travel_minutes = VALUES(travel_minutes), notes = VALUES(notes);

-- ---------------------------------------------------------------------------
-- 3) Reapuntar plantillas a las rutas canónigas + corregir el horario
-- ---------------------------------------------------------------------------
UPDATE trip_templates SET route_id = 2, departure_time = '07:00:00' WHERE id = 2; -- BUS-15 mañana IDA
UPDATE trip_templates SET route_id = 2 WHERE id = 5;  -- BUS-25 noche IDA
UPDATE trip_templates SET route_id = 2 WHERE id = 6;  -- BUS-15 noche IDA
UPDATE trip_templates SET route_id = 3 WHERE id = 4;  -- BUS-25 mañana VUELTA
UPDATE trip_templates SET route_id = 7 WHERE id = 8;  -- BUS-25 noche VUELTA

-- ---------------------------------------------------------------------------
-- 4) Reapuntar viajes existentes a las rutas canónigas
-- ---------------------------------------------------------------------------
UPDATE trip_instances SET route_id = 2 WHERE route_id IN (5,6);
UPDATE trip_instances SET route_id = 3 WHERE route_id = 4;
UPDATE trip_instances SET route_id = 7 WHERE route_id = 8;

-- Remapear trip_stop_times.route_stop_id de las rutas duplicadas a las
-- canónigas (mismo stop_order: las secuencias de paradas son idénticas).
UPDATE trip_stop_times tst
JOIN route_stops old_rs ON old_rs.id = tst.route_stop_id
JOIN route_stops new_rs ON new_rs.route_id = 2 AND new_rs.stop_order = old_rs.stop_order
SET tst.route_stop_id = new_rs.id
WHERE old_rs.route_id IN (5,6);

UPDATE trip_stop_times tst
JOIN route_stops old_rs ON old_rs.id = tst.route_stop_id
JOIN route_stops new_rs ON new_rs.route_id = 3 AND new_rs.stop_order = old_rs.stop_order
SET tst.route_stop_id = new_rs.id
WHERE old_rs.route_id = 4;

UPDATE trip_stop_times tst
JOIN route_stops old_rs ON old_rs.id = tst.route_stop_id
JOIN route_stops new_rs ON new_rs.route_id = 7 AND new_rs.stop_order = old_rs.stop_order
SET tst.route_stop_id = new_rs.id
WHERE old_rs.route_id = 8;

-- ---------------------------------------------------------------------------
-- 5) Borrar las rutas duplicadas (ya sin referencias)
-- ---------------------------------------------------------------------------
DELETE tt FROM route_segment_travel_times tt
JOIN route_segments seg ON seg.id = tt.route_segment_id
WHERE seg.route_id IN (4,5,6,8);

DELETE FROM route_segments WHERE route_id IN (4,5,6,8);
DELETE FROM route_stops    WHERE route_id IN (4,5,6,8);
DELETE FROM transport_routes WHERE id IN (4,5,6,8);

-- ---------------------------------------------------------------------------
-- 6) Renombrar rutas (sin bus) y plantillas (con bus + turno + sentido)
-- ---------------------------------------------------------------------------
UPDATE transport_routes SET code = 'IDA_LIMA_SURCO_VIA_CULTURA',  name = 'IDA Lima -> Surco (vía La Cultura)' WHERE id = 1;
UPDATE transport_routes SET code = 'IDA_LIMA_SURCO_DIRECTO',      name = 'IDA Lima -> Surco (directo)'        WHERE id = 2;
UPDATE transport_routes SET code = 'VUELTA_SURCO_LIMA_DIRECTO',   name = 'VUELTA Surco -> Lima (directo)'     WHERE id = 3;
UPDATE transport_routes SET code = 'VUELTA_SURCO_LIMA_PARADEROS', name = 'VUELTA Surco -> Lima (paraderos)'   WHERE id = 7;

UPDATE trip_templates SET code = 'TPL_BUS25_MANANA_IDA_0605',    name = 'BUS-25 · Mañana · IDA 06:05 (Lima->Surco vía Cultura)' WHERE id = 1;
UPDATE trip_templates SET code = 'TPL_BUS15_MANANA_IDA_0700',    name = 'BUS-15 · Mañana · IDA 07:00 (Lima->Surco)'             WHERE id = 2;
UPDATE trip_templates SET code = 'TPL_BUS15_MANANA_VUELTA_0605', name = 'BUS-15 · Mañana · VUELTA 06:05 (Surco->Lima)'          WHERE id = 3;
UPDATE trip_templates SET code = 'TPL_BUS25_MANANA_VUELTA_0705', name = 'BUS-25 · Mañana · VUELTA 07:05 (Surco->Lima)'          WHERE id = 4;
UPDATE trip_templates SET code = 'TPL_BUS25_NOCHE_IDA_1820',     name = 'BUS-25 · Noche · IDA 18:20 (Lima->Surco)'              WHERE id = 5;
UPDATE trip_templates SET code = 'TPL_BUS15_NOCHE_IDA_2005',     name = 'BUS-15 · Noche · IDA 20:05 (Lima->Surco)'              WHERE id = 6;
UPDATE trip_templates SET code = 'TPL_BUS15_NOCHE_VUELTA_1835',  name = 'BUS-15 · Noche · VUELTA 18:35 (Surco->Lima paraderos)' WHERE id = 7;
UPDATE trip_templates SET code = 'TPL_BUS25_NOCHE_VUELTA_2010',  name = 'BUS-25 · Noche · VUELTA 20:10 (Surco->Lima paraderos)' WHERE id = 8;

-- ---------------------------------------------------------------------------
-- 7) Recalcular horarios de los viajes FUTUROS de las 2 plantillas cambiadas
--    (los pasados se dejan como están: son historia)
-- ---------------------------------------------------------------------------
-- BUS-15 mañana IDA (TPL 2): Lima 07:00, tramos 20/10/20
UPDATE trip_stop_times tst
JOIN trip_instances t ON t.id = tst.trip_id
SET tst.scheduled_arrival_at   = TIMESTAMP(t.service_date, '07:00:00') + INTERVAL (CASE tst.stop_order WHEN 1 THEN 0 WHEN 2 THEN 20 WHEN 3 THEN 30 WHEN 4 THEN 50 END) MINUTE,
    tst.scheduled_departure_at = TIMESTAMP(t.service_date, '07:00:00') + INTERVAL (CASE tst.stop_order WHEN 1 THEN 0 WHEN 2 THEN 20 WHEN 3 THEN 30 WHEN 4 THEN 50 END) MINUTE,
    tst.applied_travel_minutes = CASE tst.stop_order WHEN 1 THEN 0 WHEN 2 THEN 20 WHEN 3 THEN 10 WHEN 4 THEN 20 END,
    tst.applied_profile_id     = CASE WHEN tst.stop_order = 1 THEN NULL ELSE @p_amp END
WHERE t.trip_template_id = 2 AND t.service_date >= CURDATE();

UPDATE trip_instances
SET scheduled_start_at = TIMESTAMP(service_date, '07:00:00'),
    scheduled_end_at   = TIMESTAMP(service_date, '07:50:00')
WHERE trip_template_id = 2 AND service_date >= CURDATE();

-- BUS-15 mañana VUELTA (TPL 3): Surco 06:05, tramos 20/10/20
UPDATE trip_stop_times tst
JOIN trip_instances t ON t.id = tst.trip_id
SET tst.scheduled_arrival_at   = TIMESTAMP(t.service_date, '06:05:00') + INTERVAL (CASE tst.stop_order WHEN 1 THEN 0 WHEN 2 THEN 20 WHEN 3 THEN 30 WHEN 4 THEN 50 END) MINUTE,
    tst.scheduled_departure_at = TIMESTAMP(t.service_date, '06:05:00') + INTERVAL (CASE tst.stop_order WHEN 1 THEN 0 WHEN 2 THEN 20 WHEN 3 THEN 30 WHEN 4 THEN 50 END) MINUTE,
    tst.applied_travel_minutes = CASE tst.stop_order WHEN 1 THEN 0 WHEN 2 THEN 20 WHEN 3 THEN 10 WHEN 4 THEN 20 END,
    tst.applied_profile_id     = CASE WHEN tst.stop_order = 1 THEN NULL ELSE @p_amt END
WHERE t.trip_template_id = 3 AND t.service_date >= CURDATE();

UPDATE trip_instances
SET scheduled_start_at = TIMESTAMP(service_date, '06:05:00'),
    scheduled_end_at   = TIMESTAMP(service_date, '06:55:00')
WHERE trip_template_id = 3 AND service_date >= CURDATE();

COMMIT;
