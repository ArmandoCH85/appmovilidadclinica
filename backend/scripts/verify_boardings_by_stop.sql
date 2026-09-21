-- ============================================================================
-- verify_boardings_by_stop.sql
-- Verifica vw_boardings_by_stop (abordajes por parada) en una base DESCARTABLE.
-- NO toca ninguna base existente: crea y borra `transporte_verify_boardings`.
--
-- Uso (desde el directorio de migraciones, para que resuelva el `source`):
--   cd /opt/appmovilidadclinica/backend/migrations
--   mariadb -u root < ../scripts/verify_boardings_by_stop.sql
--
-- Resultado esperado: todos los SELECT de TEST deben dar OK.
-- ============================================================================

DROP DATABASE IF EXISTS transporte_verify_boardings;
CREATE DATABASE transporte_verify_boardings CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE transporte_verify_boardings;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- Esquema mínimo (solo lo que toca la vista)
-- ---------------------------------------------------------------------------

CREATE TABLE transport_stops (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40),
    name VARCHAR(120) NOT NULL,
    stop_type ENUM('SEDE','PARADERO') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE transport_routes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40),
    name VARCHAR(120) NOT NULL,
    direction ENUM('IDA','VUELTA') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE trip_instances (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    service_date DATE NOT NULL,
    route_id BIGINT UNSIGNED NOT NULL
) ENGINE=InnoDB;

CREATE TABLE trip_stop_times (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    stop_id BIGINT UNSIGNED NOT NULL,
    stop_order SMALLINT UNSIGNED NOT NULL
) ENGINE=InnoDB;

CREATE TABLE reservations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    origin_stop_order SMALLINT UNSIGNED NOT NULL,
    status ENUM('CONFIRMED','BOARDED','COMPLETED','NO_SHOW','CANCELLED') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE guest_occupants (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    origin_stop_order SMALLINT UNSIGNED NOT NULL,
    status ENUM('BOARDED','COMPLETED','CANCELLED') NOT NULL
) ENGINE=InnoDB;

-- Vista real del deploy
source 0023_boardings_by_stop.up.sql

-- ---------------------------------------------------------------------------
-- Fixtures: viaje VUELTA con 2 paradas (Surco SEDE orden 1, Angamos PARADERO orden 2)
-- ---------------------------------------------------------------------------

INSERT INTO transport_stops (id, code, name, stop_type) VALUES
    (5, 'SEDE_SURCO', 'Sede Surco', 'SEDE'),
    (7, 'PAR_ANGAMOS', 'Est. Angamos', 'PARADERO');
INSERT INTO transport_routes (id, code, name, direction) VALUES
    (3, 'VUELTA_SURCO_LIMA', 'VUELTA Surco -> Lima', 'VUELTA');
INSERT INTO trip_instances (id, service_date, route_id) VALUES (10, '2026-09-20', 3);
INSERT INTO trip_stop_times (id, trip_id, stop_id, stop_order) VALUES
    (101, 10, 5, 1), (102, 10, 7, 2);

-- App (reservas): en Surco 2 BOARDED + 1 COMPLETED + 1 CONFIRMED (no cuenta);
-- en Angamos 1 BOARDED.
INSERT INTO reservations (trip_id, origin_stop_order, status) VALUES
    (10, 1, 'BOARDED'), (10, 1, 'BOARDED'), (10, 1, 'COMPLETED'), (10, 1, 'CONFIRMED'),
    (10, 2, 'BOARDED');

-- Invitados del conductor: en Surco 2 BOARDED; en Angamos 1 COMPLETED.
INSERT INTO guest_occupants (trip_id, origin_stop_order, status) VALUES
    (10, 1, 'BOARDED'), (10, 1, 'BOARDED'),
    (10, 2, 'COMPLETED');

-- ---------------------------------------------------------------------------
-- TEST 1: Surco -> app 3, invitados 2, total 5
-- ---------------------------------------------------------------------------
SELECT 'TEST 1 - Surco' AS test,
       CASE WHEN app_passengers = 3 AND guest_passengers = 2 AND total_passengers = 5
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       app_passengers, guest_passengers, total_passengers
  FROM vw_boardings_by_stop WHERE stop_id = 5;

-- ---------------------------------------------------------------------------
-- TEST 2: Angamos -> app 1, invitados 1, total 2
-- ---------------------------------------------------------------------------
SELECT 'TEST 2 - Angamos' AS test,
       CASE WHEN app_passengers = 1 AND guest_passengers = 1 AND total_passengers = 2
            THEN 'OK' ELSE 'FALLO' END AS resultado,
       app_passengers, guest_passengers, total_passengers
  FROM vw_boardings_by_stop WHERE stop_id = 7;

-- ---------------------------------------------------------------------------
-- TEST 3: CONFIRMED no cuenta como abordaje (Surco app = 3, no 4)
-- ---------------------------------------------------------------------------
SELECT 'TEST 3 - CONFIRMED excluido' AS test,
       CASE WHEN app_passengers = 3 THEN 'OK' ELSE 'FALLO' END AS resultado,
       app_passengers
  FROM vw_boardings_by_stop WHERE stop_id = 5;

-- ---------------------------------------------------------------------------
-- TEST 4: tipo de parada presente (SEDE / PARADERO)
-- ---------------------------------------------------------------------------
SELECT 'TEST 4 - tipo parada' AS test,
       CASE WHEN (SELECT stop_type FROM vw_boardings_by_stop WHERE stop_id = 5) = 'SEDE'
             AND (SELECT stop_type FROM vw_boardings_by_stop WHERE stop_id = 7) = 'PARADERO'
            THEN 'OK' ELSE 'FALLO' END AS resultado;

-- ---------------------------------------------------------------------------
-- Limpieza
-- ---------------------------------------------------------------------------
DROP DATABASE transporte_verify_boardings;
