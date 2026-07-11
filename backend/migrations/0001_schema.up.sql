-- ============================================================================
  -- 0001_schema.up.sql -- Schema MVP transporte_corporativo
  -- Origen: transporte_corporativo_mvp.sql lineas 19-2101 + 2103-2211
  -- Ejecucion idempotente (DROP IF EXISTS + CREATE). Se aplica con db.Exec al
  -- arranque del backend; NO se usa golang-migrate (decision ponytail-audit).
  -- Se omiten las lineas 2213-2601 (datos demo y queries de verificacion).
  -- ============================================================================
  
SET NAMES utf8mb4;
SET time_zone = '-05:00';
SET sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE DATABASE IF NOT EXISTS transporte_corporativo_mvp
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE transporte_corporativo_mvp;

-- --------------------------------------------------------------------------
-- LIMPIEZA PARA EJECUCIÓN REPETIBLE
-- --------------------------------------------------------------------------

DROP VIEW IF EXISTS vw_schedule_conflicts;
DROP VIEW IF EXISTS vw_trip_segment_seat_availability;
DROP VIEW IF EXISTS vw_route_time_matrix;

DROP PROCEDURE IF EXISTS sp_mark_reservation_alighted;
DROP PROCEDURE IF EXISTS sp_mark_reservation_no_show;
DROP PROCEDURE IF EXISTS sp_mark_reservation_boarded;
DROP PROCEDURE IF EXISTS sp_mark_trip_stop_arrival;
DROP PROCEDURE IF EXISTS sp_confirm_reservation;
DROP PROCEDURE IF EXISTS sp_list_trip_seats;
DROP PROCEDURE IF EXISTS sp_search_trips;
DROP PROCEDURE IF EXISTS sp_generate_trip_instance;

DROP FUNCTION IF EXISTS fn_select_travel_time_profile;
DROP FUNCTION IF EXISTS fn_service_operates;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS trip_incidents;
DROP TABLE IF EXISTS reservation_events;
DROP TABLE IF EXISTS reservation_segments;
DROP TABLE IF EXISTS trip_seat_segments;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS trip_seats;
DROP TABLE IF EXISTS trip_segments;
DROP TABLE IF EXISTS trip_stop_times;
DROP TABLE IF EXISTS trip_instances;
DROP TABLE IF EXISTS trip_generation_runs;
DROP TABLE IF EXISTS trip_templates;
DROP TABLE IF EXISTS service_calendar_exceptions;
DROP TABLE IF EXISTS service_calendars;
DROP TABLE IF EXISTS route_segment_travel_times;
DROP TABLE IF EXISTS travel_time_profiles;
DROP TABLE IF EXISTS route_segments;
DROP TABLE IF EXISTS route_stops;
DROP TABLE IF EXISTS transport_routes;
DROP TABLE IF EXISTS vehicle_seats;
DROP TABLE IF EXISTS vehicles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS transport_stops;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- A. TABLAS FUERTES / MAESTRAS
-- ============================================================================

-- 1. Puntos físicos ingresados manualmente.
CREATE TABLE transport_stops (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(30) NOT NULL,
    name                VARCHAR(150) NOT NULL,
    stop_type           ENUM('SEDE', 'PARADERO') NOT NULL,
    reference_text      VARCHAR(255) NULL,
    latitude            DECIMAL(10, 8) NULL,
    longitude           DECIMAL(11, 8) NULL,
    active              TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_transport_stops_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Actores del sistema. Para el MVP se usa una sola tabla de personas.
CREATE TABLE users (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_code               VARCHAR(30) NOT NULL,
    document_number             VARCHAR(20) NOT NULL,
    password_hash               VARCHAR(255) NOT NULL,
    full_name                   VARCHAR(150) NOT NULL,
    role                        ENUM('ADMIN', 'DRIVER', 'WORKER') NOT NULL,
    department                  VARCHAR(100) NULL,
    phone                       VARCHAR(25) NULL,
    preferred_stop_id           BIGINT UNSIGNED NULL,
    driver_license_number       VARCHAR(50) NULL,
    driver_license_category     VARCHAR(20) NULL,
    driver_license_expires_on   DATE NULL,
    fcm_token                   VARCHAR(255) NULL,
    active                      TINYINT(1) NOT NULL DEFAULT 1,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                              ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_employee_code UNIQUE (employee_code),
    CONSTRAINT uq_users_document_number UNIQUE (document_number),
    CONSTRAINT fk_users_preferred_stop
        FOREIGN KEY (preferred_stop_id) REFERENCES transport_stops(id)
        ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Vehículos físicos.
CREATE TABLE vehicles (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    internal_code       VARCHAR(30) NOT NULL,
    plate               VARCHAR(15) NOT NULL,
    description         VARCHAR(120) NULL,
    seat_capacity       SMALLINT UNSIGNED NOT NULL,
    active              TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_vehicles_internal_code UNIQUE (internal_code),
    CONSTRAINT uq_vehicles_plate UNIQUE (plate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Una fila representa una dirección concreta. La ruta inversa se enlaza.
CREATE TABLE transport_routes (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(40) NOT NULL,
    name                VARCHAR(150) NOT NULL,
    direction           ENUM('IDA', 'VUELTA') NOT NULL,
    paired_route_id     BIGINT UNSIGNED NULL,
    active              TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_transport_routes_code UNIQUE (code),
    CONSTRAINT fk_transport_routes_pair
        FOREIGN KEY (paired_route_id) REFERENCES transport_routes(id)
        ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- B. TABLAS DÉBILES / CONFIGURACIÓN DE MAESTROS
-- ============================================================================

-- 5. Inventario físico de asientos del vehículo.
CREATE TABLE vehicle_seats (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vehicle_id          BIGINT UNSIGNED NOT NULL,
    seat_number         SMALLINT UNSIGNED NOT NULL,
    seat_label          VARCHAR(10) NOT NULL,
    status              ENUM('ACTIVE', 'BLOCKED', 'RETIRED') NOT NULL DEFAULT 'ACTIVE',
    block_reason        VARCHAR(255) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_vehicle_seats_number UNIQUE (vehicle_id, seat_number),
    CONSTRAINT uq_vehicle_seats_label UNIQUE (vehicle_id, seat_label),
    CONSTRAINT fk_vehicle_seats_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Orden de paradas de cada ruta y permisos operativos de subida/bajada.
CREATE TABLE route_stops (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_id            BIGINT UNSIGNED NOT NULL,
    stop_id             BIGINT UNSIGNED NOT NULL,
    stop_order          SMALLINT UNSIGNED NOT NULL,
    dwell_minutes       SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    pickup_allowed      TINYINT(1) NOT NULL DEFAULT 1,
    dropoff_allowed     TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_route_stops_order UNIQUE (route_id, stop_order),
    CONSTRAINT fk_route_stops_route
        FOREIGN KEY (route_id) REFERENCES transport_routes(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_route_stops_stop
        FOREIGN KEY (stop_id) REFERENCES transport_stops(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Tramos consecutivos de una ruta. N paradas generan N-1 tramos.
CREATE TABLE route_segments (
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_id                BIGINT UNSIGNED NOT NULL,
    segment_order           SMALLINT UNSIGNED NOT NULL,
    from_route_stop_id      BIGINT UNSIGNED NOT NULL,
    to_route_stop_id        BIGINT UNSIGNED NOT NULL,
    active                  TINYINT(1) NOT NULL DEFAULT 1,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_route_segments_order UNIQUE (route_id, segment_order),
    CONSTRAINT uq_route_segments_pair
        UNIQUE (route_id, from_route_stop_id, to_route_stop_id),
    CONSTRAINT fk_route_segments_route
        FOREIGN KEY (route_id) REFERENCES transport_routes(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_route_segments_from
        FOREIGN KEY (from_route_stop_id) REFERENCES route_stops(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_route_segments_to
        FOREIGN KEY (to_route_stop_id) REFERENCES route_stops(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Perfiles que permiten variar tiempos por día, hora y vigencia.
CREATE TABLE travel_time_profiles (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(40) NOT NULL,
    name                VARCHAR(120) NOT NULL,
    valid_from          DATE NULL,
    valid_until         DATE NULL,
    start_time          TIME NULL,
    end_time            TIME NULL,
    is_all_day          TINYINT(1) NOT NULL DEFAULT 0,
    monday              TINYINT(1) NOT NULL DEFAULT 1,
    tuesday             TINYINT(1) NOT NULL DEFAULT 1,
    wednesday           TINYINT(1) NOT NULL DEFAULT 1,
    thursday            TINYINT(1) NOT NULL DEFAULT 1,
    friday              TINYINT(1) NOT NULL DEFAULT 1,
    saturday            TINYINT(1) NOT NULL DEFAULT 1,
    sunday              TINYINT(1) NOT NULL DEFAULT 1,
    priority            SMALLINT NOT NULL DEFAULT 0,
    is_default          TINYINT(1) NOT NULL DEFAULT 0,
    active              TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_travel_time_profiles_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Matriz manual: minutos de cada tramo bajo cada perfil aplicable.
CREATE TABLE route_segment_travel_times (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_segment_id    BIGINT UNSIGNED NOT NULL,
    profile_id          BIGINT UNSIGNED NOT NULL,
    travel_minutes      SMALLINT UNSIGNED NOT NULL,
    notes               VARCHAR(255) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_route_segment_profile UNIQUE (route_segment_id, profile_id),
    CONSTRAINT fk_route_segment_times_segment
        FOREIGN KEY (route_segment_id) REFERENCES route_segments(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_route_segment_times_profile
        FOREIGN KEY (profile_id) REFERENCES travel_time_profiles(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Días regulares en los que opera un servicio.
CREATE TABLE service_calendars (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(40) NOT NULL,
    name                VARCHAR(120) NOT NULL,
    valid_from          DATE NOT NULL,
    valid_until         DATE NOT NULL,
    monday              TINYINT(1) NOT NULL DEFAULT 1,
    tuesday             TINYINT(1) NOT NULL DEFAULT 1,
    wednesday           TINYINT(1) NOT NULL DEFAULT 1,
    thursday            TINYINT(1) NOT NULL DEFAULT 1,
    friday              TINYINT(1) NOT NULL DEFAULT 1,
    saturday            TINYINT(1) NOT NULL DEFAULT 0,
    sunday              TINYINT(1) NOT NULL DEFAULT 0,
    active              TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_service_calendars_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Excepciones: habilitar o retirar una fecha específica.
CREATE TABLE service_calendar_exceptions (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    calendar_id         BIGINT UNSIGNED NOT NULL,
    exception_date      DATE NOT NULL,
    operation           ENUM('ADD', 'REMOVE') NOT NULL,
    reason              VARCHAR(255) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_calendar_exception UNIQUE (calendar_id, exception_date),
    CONSTRAINT fk_calendar_exceptions_calendar
        FOREIGN KEY (calendar_id) REFERENCES service_calendars(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Regla recurrente que el motor materializa como viajes futuros.
CREATE TABLE trip_templates (
    id                              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code                            VARCHAR(50) NOT NULL,
    name                            VARCHAR(150) NOT NULL,
    route_id                        BIGINT UNSIGNED NOT NULL,
    service_calendar_id             BIGINT UNSIGNED NOT NULL,
    departure_time                  TIME NOT NULL,
    default_vehicle_id              BIGINT UNSIGNED NOT NULL,
    default_driver_id               BIGINT UNSIGNED NOT NULL,
    profile_reference_mode          ENUM('TRIP_DEPARTURE', 'SEGMENT_DEPARTURE')
                                        NOT NULL DEFAULT 'SEGMENT_DEPARTURE',
    booking_open_days_before        SMALLINT UNSIGNED NOT NULL DEFAULT 14,
    booking_close_minutes_before    SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    no_show_tolerance_minutes       SMALLINT UNSIGNED NOT NULL DEFAULT 5,
    automatic_publish               TINYINT(1) NOT NULL DEFAULT 1,
    active                          TINYINT(1) NOT NULL DEFAULT 1,
    created_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                  ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_trip_templates_code UNIQUE (code),
    CONSTRAINT fk_trip_templates_route
        FOREIGN KEY (route_id) REFERENCES transport_routes(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_templates_calendar
        FOREIGN KEY (service_calendar_id) REFERENCES service_calendars(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_templates_vehicle
        FOREIGN KEY (default_vehicle_id) REFERENCES vehicles(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_templates_driver
        FOREIGN KEY (default_driver_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- C. TABLAS INTERMEDIAS GENERADAS POR EL MOTOR
-- ============================================================================

-- 13. Auditoría de cada ejecución del generador.
CREATE TABLE trip_generation_runs (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    window_start        DATE NOT NULL,
    window_end          DATE NOT NULL,
    status              ENUM('RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')
                            NOT NULL DEFAULT 'RUNNING',
    generated_count     INT UNSIGNED NOT NULL DEFAULT 0,
    skipped_count       INT UNSIGNED NOT NULL DEFAULT 0,
    failed_count        INT UNSIGNED NOT NULL DEFAULT 0,
    error_summary       TEXT NULL,
    triggered_by_user_id BIGINT UNSIGNED NULL,
    started_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at         DATETIME NULL,

    CONSTRAINT fk_generation_runs_user
        FOREIGN KEY (triggered_by_user_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. Viaje concreto en una fecha. Es lo que consulta y reserva el pasajero.
CREATE TABLE trip_instances (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_code                   VARCHAR(60) NOT NULL,
    source                      ENUM('GENERATED', 'MANUAL') NOT NULL DEFAULT 'GENERATED',
    trip_template_id            BIGINT UNSIGNED NULL,
    generation_run_id           BIGINT UNSIGNED NULL,
    route_id                    BIGINT UNSIGNED NOT NULL,
    service_date                DATE NOT NULL,
    scheduled_start_at          DATETIME NOT NULL,
    scheduled_end_at            DATETIME NOT NULL,
    booking_opens_at            DATETIME NOT NULL,
    booking_closes_at           DATETIME NOT NULL,
    vehicle_id                  BIGINT UNSIGNED NOT NULL,
    driver_id                   BIGINT UNSIGNED NOT NULL,
    seat_capacity_snapshot      SMALLINT UNSIGNED NOT NULL,
    no_show_tolerance_minutes   SMALLINT UNSIGNED NOT NULL,
    status                      ENUM(
                                    'DRAFT', 'PUBLISHED', 'BOARDING', 'IN_PROGRESS',
                                    'COMPLETED', 'CANCELLED'
                                ) NOT NULL DEFAULT 'DRAFT',
    actual_start_at             DATETIME NULL,
    actual_end_at               DATETIME NULL,
    cancellation_reason         VARCHAR(255) NULL,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                              ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_trip_instances_code UNIQUE (trip_code),
    CONSTRAINT uq_trip_template_service_date UNIQUE (trip_template_id, service_date),
    CONSTRAINT fk_trip_instances_template
        FOREIGN KEY (trip_template_id) REFERENCES trip_templates(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_instances_run
        FOREIGN KEY (generation_run_id) REFERENCES trip_generation_runs(id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_trip_instances_route
        FOREIGN KEY (route_id) REFERENCES transport_routes(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_instances_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_instances_driver
        FOREIGN KEY (driver_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. Cronograma calculado de cada parada del viaje.
CREATE TABLE trip_stop_times (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id                     BIGINT UNSIGNED NOT NULL,
    route_stop_id               BIGINT UNSIGNED NOT NULL,
    stop_id                     BIGINT UNSIGNED NOT NULL,
    stop_order                  SMALLINT UNSIGNED NOT NULL,
    scheduled_arrival_at        DATETIME NOT NULL,
    scheduled_departure_at      DATETIME NOT NULL,
    applied_profile_id          BIGINT UNSIGNED NULL,
    applied_travel_minutes      SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    applied_dwell_minutes       SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    actual_arrival_at           DATETIME NULL,
    actual_departure_at         DATETIME NULL,
    arrival_marked_by_user_id   BIGINT UNSIGNED NULL,
    status                      ENUM('PENDING', 'ARRIVED', 'DEPARTED', 'SKIPPED')
                                    NOT NULL DEFAULT 'PENDING',
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                              ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_trip_stop_order UNIQUE (trip_id, stop_order),
    CONSTRAINT fk_trip_stop_times_trip
        FOREIGN KEY (trip_id) REFERENCES trip_instances(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_stop_times_route_stop
        FOREIGN KEY (route_stop_id) REFERENCES route_stops(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_stop_times_stop
        FOREIGN KEY (stop_id) REFERENCES transport_stops(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_stop_times_profile
        FOREIGN KEY (applied_profile_id) REFERENCES travel_time_profiles(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_stop_times_arrival_user
        FOREIGN KEY (arrival_marked_by_user_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. Segmentos concretos del viaje. Son la unidad matemática de ocupación.
CREATE TABLE trip_segments (
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id                 BIGINT UNSIGNED NOT NULL,
    segment_order           SMALLINT UNSIGNED NOT NULL,
    from_trip_stop_time_id  BIGINT UNSIGNED NOT NULL,
    to_trip_stop_time_id    BIGINT UNSIGNED NOT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_trip_segments_order UNIQUE (trip_id, segment_order),
    CONSTRAINT fk_trip_segments_trip
        FOREIGN KEY (trip_id) REFERENCES trip_instances(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_segments_from
        FOREIGN KEY (from_trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_segments_to
        FOREIGN KEY (to_trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. Copia inmutable de los asientos asignados al viaje.
CREATE TABLE trip_seats (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id             BIGINT UNSIGNED NOT NULL,
    vehicle_seat_id     BIGINT UNSIGNED NOT NULL,
    seat_number         SMALLINT UNSIGNED NOT NULL,
    seat_label          VARCHAR(10) NOT NULL,
    is_blocked          TINYINT(1) NOT NULL DEFAULT 0,
    block_reason        VARCHAR(255) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_trip_seats_number UNIQUE (trip_id, seat_number),
    CONSTRAINT uq_trip_seats_label UNIQUE (trip_id, seat_label),
    CONSTRAINT fk_trip_seats_trip
        FOREIGN KEY (trip_id) REFERENCES trip_instances(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_seats_vehicle_seat
        FOREIGN KEY (vehicle_seat_id) REFERENCES vehicle_seats(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 18. Inventario vivo generado: una fila por asiento y por segmento del viaje.
--     La FK a reservations se agrega después para conservar el orden lógico.
CREATE TABLE trip_seat_segments (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_seat_id        BIGINT UNSIGNED NOT NULL,
    trip_segment_id     BIGINT UNSIGNED NOT NULL,
    state               ENUM('AVAILABLE', 'RESERVED', 'OCCUPIED', 'USED', 'BLOCKED')
                            NOT NULL DEFAULT 'AVAILABLE',
    reservation_id      BIGINT UNSIGNED NULL,
    reserved_at         DATETIME NULL,
    released_at         DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_trip_seat_segment UNIQUE (trip_seat_id, trip_segment_id),
    CONSTRAINT fk_trip_seat_segments_seat
        FOREIGN KEY (trip_seat_id) REFERENCES trip_seats(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_seat_segments_segment
        FOREIGN KEY (trip_segment_id) REFERENCES trip_segments(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- D. TABLAS TRANSACCIONALES / DÉBILES
-- ============================================================================

-- 19. Cabecera de la reserva del trabajador.
CREATE TABLE reservations (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_code            VARCHAR(32) NOT NULL,
    qr_token_hash               CHAR(64) NOT NULL,
    booking_group_uuid          CHAR(36) NULL,
    trip_id                     BIGINT UNSIGNED NOT NULL,
    worker_id                   BIGINT UNSIGNED NOT NULL,
    trip_seat_id                BIGINT UNSIGNED NOT NULL,
    origin_trip_stop_time_id    BIGINT UNSIGNED NOT NULL,
    destination_trip_stop_time_id BIGINT UNSIGNED NOT NULL,
    origin_stop_order           SMALLINT UNSIGNED NOT NULL,
    destination_stop_order      SMALLINT UNSIGNED NOT NULL,
    status                      ENUM('CONFIRMED', 'BOARDED', 'COMPLETED', 'NO_SHOW')
                                    NOT NULL DEFAULT 'CONFIRMED',
    created_by_user_id          BIGINT UNSIGNED NOT NULL,
    confirmed_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    boarded_at                  DATETIME NULL,
    completed_at                DATETIME NULL,
    no_show_at                  DATETIME NULL,
    no_show_by_user_id          BIGINT UNSIGNED NULL,
    no_show_trip_stop_time_id   BIGINT UNSIGNED NULL,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                              ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_reservations_code UNIQUE (reservation_code),
    CONSTRAINT uq_reservations_qr_hash UNIQUE (qr_token_hash),
    CONSTRAINT fk_reservations_trip
        FOREIGN KEY (trip_id) REFERENCES trip_instances(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_worker
        FOREIGN KEY (worker_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_trip_seat
        FOREIGN KEY (trip_seat_id) REFERENCES trip_seats(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_origin
        FOREIGN KEY (origin_trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_destination
        FOREIGN KEY (destination_trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_no_show_by
        FOREIGN KEY (no_show_by_user_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_reservations_no_show_stop
        FOREIGN KEY (no_show_trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE trip_seat_segments
    ADD CONSTRAINT fk_trip_seat_segments_reservation
    FOREIGN KEY (reservation_id) REFERENCES reservations(id)
    ON UPDATE RESTRICT ON DELETE SET NULL;

-- 20. Historial inmutable de los segmentos originalmente asignados.
--     No se borra al liberar el inventario por NO_SHOW.
CREATE TABLE reservation_segments (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_id      BIGINT UNSIGNED NOT NULL,
    trip_segment_id     BIGINT UNSIGNED NOT NULL,
    allocation_status   ENUM('RESERVED', 'OCCUPIED', 'USED', 'RELEASED')
                            NOT NULL DEFAULT 'RESERVED',
    reserved_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at         DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_reservation_segment UNIQUE (reservation_id, trip_segment_id),
    CONSTRAINT fk_reservation_segments_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_reservation_segments_segment
        FOREIGN KEY (trip_segment_id) REFERENCES trip_segments(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 21. Bitácora auditable de cada cambio de la reserva.
CREATE TABLE reservation_events (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_id      BIGINT UNSIGNED NOT NULL,
    event_type          ENUM(
                            'CONFIRMED', 'BOARDED', 'ALIGHTED',
                            'NO_SHOW', 'SEGMENTS_RELEASED'
                        ) NOT NULL,
    trip_stop_time_id   BIGINT UNSIGNED NULL,
    actor_user_id       BIGINT UNSIGNED NOT NULL,
    event_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details             VARCHAR(500) NULL,

    CONSTRAINT fk_reservation_events_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_reservation_events_stop
        FOREIGN KEY (trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_reservation_events_actor
        FOREIGN KEY (actor_user_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 22. Incidencias mínimas del viaje; se conserva porque es operación vital.
CREATE TABLE trip_incidents (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id             BIGINT UNSIGNED NOT NULL,
    reported_by_user_id BIGINT UNSIGNED NOT NULL,
    incident_type       ENUM('BREAKDOWN', 'DELAY', 'ACCIDENT', 'OTHER') NOT NULL,
    description         VARCHAR(1000) NOT NULL,
    status              ENUM('OPEN', 'IN_REVIEW', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
    reported_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at         DATETIME NULL,
    resolution_notes    VARCHAR(1000) NULL,

    CONSTRAINT fk_trip_incidents_trip
        FOREIGN KEY (trip_id) REFERENCES trip_instances(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_trip_incidents_reporter
        FOREIGN KEY (reported_by_user_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------
-- ÍNDICES DE BÚSQUEDA Y OPERACIÓN
-- --------------------------------------------------------------------------

CREATE INDEX idx_routes_direction_active
    ON transport_routes(direction, active);
CREATE INDEX idx_route_stops_stop
    ON route_stops(stop_id, route_id, stop_order);
CREATE INDEX idx_time_profiles_selection
    ON travel_time_profiles(active, valid_from, valid_until, priority);
CREATE INDEX idx_templates_active_route
    ON trip_templates(active, route_id, departure_time);
CREATE INDEX idx_trip_search
    ON trip_instances(service_date, route_id, status, scheduled_start_at);
CREATE INDEX idx_trip_vehicle_schedule
    ON trip_instances(vehicle_id, scheduled_start_at, scheduled_end_at, status);
CREATE INDEX idx_trip_driver_schedule
    ON trip_instances(driver_id, scheduled_start_at, scheduled_end_at, status);
CREATE INDEX idx_trip_stop_lookup
    ON trip_stop_times(trip_id, stop_id, stop_order);
CREATE INDEX idx_trip_segment_lookup
    ON trip_segments(trip_id, segment_order);
CREATE INDEX idx_inventory_state
    ON trip_seat_segments(trip_segment_id, state, trip_seat_id);
CREATE INDEX idx_inventory_reservation
    ON trip_seat_segments(reservation_id, state);
CREATE INDEX idx_reservations_worker_trip
    ON reservations(worker_id, trip_id, status);
CREATE INDEX idx_reservations_trip_status
    ON reservations(trip_id, status, origin_stop_order, destination_stop_order);
CREATE INDEX idx_reservation_events_time
    ON reservation_events(reservation_id, event_at);

-- ============================================================================
-- -- E. VALIDACIONES, FUNCIONES Y MOTOR DE GENERACIÓN
-- -- ============================================================================
-- 
-- DELIMITER $$
-- 
-- -- Una ruta con matriz no cambia silenciosamente de estructura.
-- CREATE TRIGGER trg_route_stops_protect_structure
-- BEFORE UPDATE ON route_stops
-- FOR EACH ROW
-- BEGIN
--     IF (
--         NEW.route_id <> OLD.route_id
--         OR NEW.stop_id <> OLD.stop_id
--         OR NEW.stop_order <> OLD.stop_order
--     ) AND EXISTS (
--         SELECT 1
--           FROM route_segments
--          WHERE from_route_stop_id = OLD.id
--             OR to_route_stop_id = OLD.id
--     ) THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'No se puede reordenar una ruta que ya tiene tramos; cree una nueva versión';
--     END IF;
-- END$$
-- 
-- -- Impide crear un segmento entre paradas no consecutivas o de rutas distintas.
-- CREATE TRIGGER trg_route_segments_validate_insert
-- BEFORE INSERT ON route_segments
-- FOR EACH ROW
-- BEGIN
--     DECLARE v_from_route BIGINT UNSIGNED;
--     DECLARE v_to_route BIGINT UNSIGNED;
--     DECLARE v_from_order SMALLINT UNSIGNED;
--     DECLARE v_to_order SMALLINT UNSIGNED;
-- 
--     SELECT route_id, stop_order
--       INTO v_from_route, v_from_order
--       FROM route_stops
--      WHERE id = NEW.from_route_stop_id;
-- 
--     SELECT route_id, stop_order
--       INTO v_to_route, v_to_order
--       FROM route_stops
--      WHERE id = NEW.to_route_stop_id;
-- 
--     IF v_from_route <> NEW.route_id
--        OR v_to_route <> NEW.route_id
--        OR v_to_order <> v_from_order + 1
--        OR NEW.segment_order <> v_from_order THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El tramo debe unir paradas consecutivas de la misma ruta';
--     END IF;
-- END$$
-- 
-- CREATE TRIGGER trg_route_segments_validate_update
-- BEFORE UPDATE ON route_segments
-- FOR EACH ROW
-- BEGIN
--     DECLARE v_from_route BIGINT UNSIGNED;
--     DECLARE v_to_route BIGINT UNSIGNED;
--     DECLARE v_from_order SMALLINT UNSIGNED;
--     DECLARE v_to_order SMALLINT UNSIGNED;
-- 
--     SELECT route_id, stop_order
--       INTO v_from_route, v_from_order
--       FROM route_stops
--      WHERE id = NEW.from_route_stop_id;
-- 
--     SELECT route_id, stop_order
--       INTO v_to_route, v_to_order
--       FROM route_stops
--      WHERE id = NEW.to_route_stop_id;
-- 
--     IF v_from_route <> NEW.route_id
--        OR v_to_route <> NEW.route_id
--        OR v_to_order <> v_from_order + 1
--        OR NEW.segment_order <> v_from_order THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El tramo debe unir paradas consecutivas de la misma ruta';
--     END IF;
-- END$$
-- 
-- -- Devuelve 1 cuando el calendario opera en una fecha y 0 cuando no opera.
-- -- Una excepción ADD/REMOVE tiene prioridad sobre el patrón semanal.
-- CREATE FUNCTION fn_service_operates(
--     p_calendar_id BIGINT UNSIGNED,
--     p_service_date DATE
-- )
-- RETURNS TINYINT
-- NOT DETERMINISTIC
-- READS SQL DATA
-- BEGIN
--     DECLARE v_active TINYINT DEFAULT 0;
--     DECLARE v_valid_from DATE;
--     DECLARE v_valid_until DATE;
--     DECLARE v_exception VARCHAR(10);
--     DECLARE v_day_enabled TINYINT DEFAULT 0;
-- 
--     SET v_active = COALESCE((
--         SELECT active
--           FROM service_calendars
--          WHERE id = p_calendar_id
--          LIMIT 1
--     ), 0);
-- 
--     IF v_active = 0 OR p_service_date IS NULL THEN
--         RETURN 0;
--     END IF;
-- 
--     SET v_exception = (
--         SELECT operation
--           FROM service_calendar_exceptions
--          WHERE calendar_id = p_calendar_id
--            AND exception_date = p_service_date
--          LIMIT 1
--     );
-- 
--     IF v_exception = 'ADD' THEN
--         RETURN 1;
--     ELSEIF v_exception = 'REMOVE' THEN
--         RETURN 0;
--     END IF;
-- 
--     SELECT valid_from, valid_until,
--            CASE DAYOFWEEK(p_service_date)
--                WHEN 1 THEN sunday
--                WHEN 2 THEN monday
--                WHEN 3 THEN tuesday
--                WHEN 4 THEN wednesday
--                WHEN 5 THEN thursday
--                WHEN 6 THEN friday
--                WHEN 7 THEN saturday
--            END
--       INTO v_valid_from, v_valid_until, v_day_enabled
--       FROM service_calendars
--      WHERE id = p_calendar_id;
-- 
--     IF p_service_date < v_valid_from OR p_service_date > v_valid_until THEN
--         RETURN 0;
--     END IF;
-- 
--     RETURN COALESCE(v_day_enabled, 0);
-- END$$
-- 
-- -- Selecciona automáticamente el perfil más específico aplicable al momento
-- -- del viaje/segmento. Admite perfiles que cruzan medianoche.
-- CREATE FUNCTION fn_select_travel_time_profile(
--     p_route_segment_id BIGINT UNSIGNED,
--     p_reference_at DATETIME
-- )
-- RETURNS BIGINT UNSIGNED
-- NOT DETERMINISTIC
-- READS SQL DATA
-- BEGIN
--     DECLARE v_profile_id BIGINT UNSIGNED;
-- 
--     SET v_profile_id = (
--         SELECT p.id
--           FROM route_segment_travel_times matrix_time
--           JOIN travel_time_profiles p
--             ON p.id = matrix_time.profile_id
--          WHERE matrix_time.route_segment_id = p_route_segment_id
--            AND p.active = 1
--            AND (p.valid_from IS NULL OR DATE(p_reference_at) >= p.valid_from)
--            AND (p.valid_until IS NULL OR DATE(p_reference_at) <= p.valid_until)
--            AND CASE DAYOFWEEK(DATE(p_reference_at))
--                    WHEN 1 THEN p.sunday
--                    WHEN 2 THEN p.monday
--                    WHEN 3 THEN p.tuesday
--                    WHEN 4 THEN p.wednesday
--                    WHEN 5 THEN p.thursday
--                    WHEN 6 THEN p.friday
--                    WHEN 7 THEN p.saturday
--                END = 1
--            AND (
--                 p.is_all_day = 1
--                 OR (
--                     p.start_time < p.end_time
--                     AND TIME(p_reference_at) >= p.start_time
--                     AND TIME(p_reference_at) < p.end_time
--                 )
--                 OR (
--                     p.start_time > p.end_time
--                     AND (
--                         TIME(p_reference_at) >= p.start_time
--                         OR TIME(p_reference_at) < p.end_time
--                     )
--                 )
--            )
--          ORDER BY p.priority DESC, p.is_default ASC, p.id ASC
--          LIMIT 1
--     );
-- 
--     RETURN v_profile_id;
-- END$$
-- 
-- -- Materializa un viaje concreto desde una plantilla y una fecha.
-- -- La aplicación ejecuta este procedimiento por cada plantilla/fecha dentro
-- -- del horizonte deseado (por ejemplo, los próximos 30 días).
-- CREATE PROCEDURE sp_generate_trip_instance(
--     IN p_trip_template_id BIGINT UNSIGNED,
--     IN p_service_date DATE,
--     IN p_generation_run_id BIGINT UNSIGNED
-- )
-- procedure_block: BEGIN
--     DECLARE v_done TINYINT DEFAULT 0;
--     DECLARE v_template_exists INT DEFAULT 0;
--     DECLARE v_existing_trip_id BIGINT UNSIGNED;
--     DECLARE v_route_id BIGINT UNSIGNED;
--     DECLARE v_calendar_id BIGINT UNSIGNED;
--     DECLARE v_vehicle_id BIGINT UNSIGNED;
--     DECLARE v_driver_id BIGINT UNSIGNED;
--     DECLARE v_departure_time TIME;
--     DECLARE v_profile_mode VARCHAR(30);
--     DECLARE v_open_days SMALLINT UNSIGNED;
--     DECLARE v_booking_close_minutes SMALLINT UNSIGNED;
--     DECLARE v_no_show_minutes SMALLINT UNSIGNED;
--     DECLARE v_auto_publish TINYINT;
--     DECLARE v_template_code VARCHAR(50);
--     DECLARE v_driver_role VARCHAR(20);
--     DECLARE v_vehicle_active TINYINT;
--     DECLARE v_vehicle_capacity SMALLINT UNSIGNED;
--     DECLARE v_total_vehicle_seats INT DEFAULT 0;
--     DECLARE v_active_vehicle_seats INT DEFAULT 0;
--     DECLARE v_route_stop_count INT DEFAULT 0;
--     DECLARE v_max_stop_order INT DEFAULT 0;
--     DECLARE v_trip_id BIGINT UNSIGNED;
--     DECLARE v_trip_code VARCHAR(60);
--     DECLARE v_start_at DATETIME;
--     DECLARE v_end_at DATETIME;
--     DECLARE v_booking_opens_at DATETIME;
--     DECLARE v_booking_closes_at DATETIME;
--     DECLARE v_route_stop_id BIGINT UNSIGNED;
--     DECLARE v_stop_id BIGINT UNSIGNED;
--     DECLARE v_stop_order SMALLINT UNSIGNED;
--     DECLARE v_dwell_minutes SMALLINT UNSIGNED;
--     DECLARE v_prev_route_stop_id BIGINT UNSIGNED;
--     DECLARE v_prev_departure_at DATETIME;
--     DECLARE v_route_segment_id BIGINT UNSIGNED;
--     DECLARE v_profile_id BIGINT UNSIGNED;
--     DECLARE v_travel_minutes SMALLINT UNSIGNED;
--     DECLARE v_reference_at DATETIME;
--     DECLARE v_arrival_at DATETIME;
--     DECLARE v_departure_at DATETIME;
--     DECLARE v_conflicts INT DEFAULT 0;
--     DECLARE v_error_message TEXT;
-- 
--     DECLARE route_stop_cursor CURSOR FOR
--         SELECT id, stop_id, stop_order, dwell_minutes
--           FROM route_stops
--          WHERE route_id = v_route_id
--          ORDER BY stop_order;
-- 
--     DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;
--     DECLARE EXIT HANDLER FOR SQLEXCEPTION
--     BEGIN
--         GET DIAGNOSTICS CONDITION 1 v_error_message = MESSAGE_TEXT;
--         ROLLBACK;
-- 
--         IF p_generation_run_id IS NOT NULL THEN
--             UPDATE trip_generation_runs
--                SET failed_count = failed_count + 1,
--                    error_summary = CONCAT_WS(
--                        CHAR(10),
--                        error_summary,
--                        CONCAT(
--                            'Plantilla ', p_trip_template_id,
--                            ', fecha ', COALESCE(CAST(p_service_date AS CHAR), 'NULL'),
--                            ': ', COALESCE(v_error_message, 'Error no especificado')
--                        )
--                    )
--              WHERE id = p_generation_run_id;
--         END IF;
-- 
--         RESIGNAL;
--     END;
-- 
--     IF p_service_date IS NULL THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'La fecha de servicio es obligatoria';
--     END IF;
-- 
--     START TRANSACTION;
-- 
--     SELECT COUNT(*)
--       INTO v_template_exists
--       FROM trip_templates
--      WHERE id = p_trip_template_id
--        AND active = 1;
-- 
--     IF v_template_exists = 0 THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'La plantilla no existe o está inactiva';
--     END IF;
-- 
--     SELECT route_id,
--            service_calendar_id,
--            departure_time,
--            default_vehicle_id,
--            default_driver_id,
--            profile_reference_mode,
--            booking_open_days_before,
--            booking_close_minutes_before,
--            no_show_tolerance_minutes,
--            automatic_publish,
--            code
--       INTO v_route_id,
--            v_calendar_id,
--            v_departure_time,
--            v_vehicle_id,
--            v_driver_id,
--            v_profile_mode,
--            v_open_days,
--            v_booking_close_minutes,
--            v_no_show_minutes,
--            v_auto_publish,
--            v_template_code
--       FROM trip_templates
--      WHERE id = p_trip_template_id;
-- 
--     SET v_existing_trip_id = (
--         SELECT id
--           FROM trip_instances
--          WHERE trip_template_id = p_trip_template_id
--            AND service_date = p_service_date
--          LIMIT 1
--     );
-- 
--     IF v_existing_trip_id IS NOT NULL THEN
--         IF p_generation_run_id IS NOT NULL THEN
--             UPDATE trip_generation_runs
--                SET skipped_count = skipped_count + 1
--              WHERE id = p_generation_run_id;
--         END IF;
-- 
--         COMMIT;
--         SELECT v_existing_trip_id AS trip_id, 'ALREADY_EXISTS' AS generation_result;
--         LEAVE procedure_block;
--     END IF;
-- 
--     IF fn_service_operates(v_calendar_id, p_service_date) = 0 THEN
--         IF p_generation_run_id IS NOT NULL THEN
--             UPDATE trip_generation_runs
--                SET skipped_count = skipped_count + 1
--              WHERE id = p_generation_run_id;
--         END IF;
-- 
--         COMMIT;
--         SELECT NULL AS trip_id, 'SKIPPED_BY_CALENDAR' AS generation_result;
--         LEAVE procedure_block;
--     END IF;
-- 
--     SELECT active, seat_capacity
--       INTO v_vehicle_active, v_vehicle_capacity
--       FROM vehicles
--      WHERE id = v_vehicle_id;
-- 
--     IF v_vehicle_active IS NULL OR v_vehicle_active = 0 THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El vehículo de la plantilla está inactivo';
--     END IF;
-- 
--     SELECT role
--       INTO v_driver_role
--       FROM users
--      WHERE id = v_driver_id
--        AND active = 1;
-- 
--     IF v_driver_role IS NULL OR v_driver_role <> 'DRIVER' THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El conductor de la plantilla no es un DRIVER activo';
--     END IF;
-- 
--     SELECT COUNT(*),
--            COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0)
--       INTO v_total_vehicle_seats, v_active_vehicle_seats
--       FROM vehicle_seats
--      WHERE vehicle_id = v_vehicle_id
--        AND status <> 'RETIRED';
-- 
--     IF v_total_vehicle_seats <> v_vehicle_capacity THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'La capacidad del vehículo no coincide con sus asientos no retirados';
--     END IF;
-- 
--     SELECT COUNT(*), MAX(stop_order)
--       INTO v_route_stop_count, v_max_stop_order
--       FROM route_stops
--      WHERE route_id = v_route_id;
-- 
--     IF v_route_stop_count < 2 OR v_route_stop_count <> v_max_stop_order THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'La ruta debe tener al menos dos paradas con órdenes consecutivos desde 1';
--     END IF;
-- 
--     SET v_start_at = TIMESTAMP(p_service_date, v_departure_time);
--     SET v_end_at = v_start_at;
--     SET v_booking_opens_at = DATE_SUB(v_start_at, INTERVAL v_open_days DAY);
--     SET v_booking_closes_at = DATE_SUB(v_start_at, INTERVAL v_booking_close_minutes MINUTE);
--     SET v_trip_code = CONCAT(
--         'T-', LEFT(v_template_code, 35), '-', DATE_FORMAT(p_service_date, '%Y%m%d')
--     );
-- 
--     INSERT INTO trip_instances (
--         trip_code,
--         source,
--         trip_template_id,
--         generation_run_id,
--         route_id,
--         service_date,
--         scheduled_start_at,
--         scheduled_end_at,
--         booking_opens_at,
--         booking_closes_at,
--         vehicle_id,
--         driver_id,
--         seat_capacity_snapshot,
--         no_show_tolerance_minutes,
--         status
--     ) VALUES (
--         v_trip_code,
--         'GENERATED',
--         p_trip_template_id,
--         p_generation_run_id,
--         v_route_id,
--         p_service_date,
--         v_start_at,
--         v_end_at,
--         v_booking_opens_at,
--         v_booking_closes_at,
--         v_vehicle_id,
--         v_driver_id,
--         v_active_vehicle_seats,
--         v_no_show_minutes,
--         'DRAFT'
--     );
-- 
--     SET v_trip_id = LAST_INSERT_ID();
--     SET v_done = 0;
--     OPEN route_stop_cursor;
-- 
--     route_stop_loop: LOOP
--         FETCH route_stop_cursor
--          INTO v_route_stop_id, v_stop_id, v_stop_order, v_dwell_minutes;
-- 
--         IF v_done = 1 THEN
--             LEAVE route_stop_loop;
--         END IF;
-- 
--         IF v_prev_route_stop_id IS NULL THEN
--             SET v_arrival_at = v_start_at;
--             SET v_departure_at = v_start_at;
--             SET v_profile_id = NULL;
--             SET v_travel_minutes = 0;
--             SET v_dwell_minutes = 0;
--         ELSE
--             SET v_route_segment_id = (
--                 SELECT id
--                   FROM route_segments
--                  WHERE route_id = v_route_id
--                    AND from_route_stop_id = v_prev_route_stop_id
--                    AND to_route_stop_id = v_route_stop_id
--                    AND active = 1
--                  LIMIT 1
--             );
-- 
--             IF v_route_segment_id IS NULL THEN
--                 SET v_error_message = CONCAT(
--                     'Falta el tramo que termina en el orden ', v_stop_order
--                 );
--                 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error_message;
--             END IF;
-- 
--             IF v_profile_mode = 'TRIP_DEPARTURE' THEN
--                 SET v_reference_at = v_start_at;
--             ELSE
--                 SET v_reference_at = v_prev_departure_at;
--             END IF;
-- 
--             SET v_profile_id = fn_select_travel_time_profile(
--                 v_route_segment_id,
--                 v_reference_at
--             );
-- 
--             SET v_travel_minutes = (
--                 SELECT travel_minutes
--                   FROM route_segment_travel_times
--                  WHERE route_segment_id = v_route_segment_id
--                    AND profile_id = v_profile_id
--                  LIMIT 1
--             );
-- 
--             IF v_profile_id IS NULL OR v_travel_minutes IS NULL THEN
--                 SET v_error_message = CONCAT(
--                     'No existe tiempo aplicable para el tramo ',
--                     v_route_segment_id,
--                     ' a las ', DATE_FORMAT(v_reference_at, '%Y-%m-%d %H:%i:%s')
--                 );
--                 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error_message;
--             END IF;
-- 
--             SET v_arrival_at = DATE_ADD(
--                 v_prev_departure_at,
--                 INTERVAL v_travel_minutes MINUTE
--             );
-- 
--             IF v_stop_order = v_max_stop_order THEN
--                 SET v_departure_at = v_arrival_at;
--                 SET v_dwell_minutes = 0;
--             ELSE
--                 SET v_departure_at = DATE_ADD(
--                     v_arrival_at,
--                     INTERVAL v_dwell_minutes MINUTE
--                 );
--             END IF;
--         END IF;
-- 
--         INSERT INTO trip_stop_times (
--             trip_id,
--             route_stop_id,
--             stop_id,
--             stop_order,
--             scheduled_arrival_at,
--             scheduled_departure_at,
--             applied_profile_id,
--             applied_travel_minutes,
--             applied_dwell_minutes
--         ) VALUES (
--             v_trip_id,
--             v_route_stop_id,
--             v_stop_id,
--             v_stop_order,
--             v_arrival_at,
--             v_departure_at,
--             v_profile_id,
--             v_travel_minutes,
--             v_dwell_minutes
--         );
-- 
--         SET v_prev_route_stop_id = v_route_stop_id;
--         SET v_prev_departure_at = v_departure_at;
--         SET v_end_at = v_arrival_at;
--     END LOOP;
-- 
--     CLOSE route_stop_cursor;
-- 
--     INSERT INTO trip_segments (
--         trip_id,
--         segment_order,
--         from_trip_stop_time_id,
--         to_trip_stop_time_id
--     )
--     SELECT current_stop.trip_id,
--            current_stop.stop_order,
--            current_stop.id,
--            next_stop.id
--       FROM trip_stop_times current_stop
--       JOIN trip_stop_times next_stop
--         ON next_stop.trip_id = current_stop.trip_id
--        AND next_stop.stop_order = current_stop.stop_order + 1
--      WHERE current_stop.trip_id = v_trip_id;
-- 
--     INSERT INTO trip_seats (
--         trip_id,
--         vehicle_seat_id,
--         seat_number,
--         seat_label,
--         is_blocked,
--         block_reason
--     )
--     SELECT v_trip_id,
--            seat.id,
--            seat.seat_number,
--            seat.seat_label,
--            CASE WHEN seat.status = 'BLOCKED' THEN 1 ELSE 0 END,
--            seat.block_reason
--       FROM vehicle_seats seat
--      WHERE seat.vehicle_id = v_vehicle_id
--        AND seat.status <> 'RETIRED'
--      ORDER BY seat.seat_number;
-- 
--     INSERT INTO trip_seat_segments (
--         trip_seat_id,
--         trip_segment_id,
--         state
--     )
--     SELECT trip_seat.id,
--            trip_segment.id,
--            CASE WHEN trip_seat.is_blocked = 1 THEN 'BLOCKED' ELSE 'AVAILABLE' END
--       FROM trip_seats trip_seat
--       JOIN trip_segments trip_segment
--         ON trip_segment.trip_id = trip_seat.trip_id
--      WHERE trip_seat.trip_id = v_trip_id;
-- 
--     UPDATE trip_instances
--        SET scheduled_end_at = v_end_at
--      WHERE id = v_trip_id;
-- 
--     -- Evita asignar el mismo vehículo o conductor a viajes solapados.
--     SELECT COUNT(*)
--       INTO v_conflicts
--       FROM trip_instances other_trip
--      WHERE other_trip.id <> v_trip_id
--        AND other_trip.vehicle_id = v_vehicle_id
--        AND other_trip.status <> 'CANCELLED'
--        AND other_trip.scheduled_start_at < v_end_at
--        AND other_trip.scheduled_end_at > v_start_at;
-- 
--     IF v_conflicts > 0 THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El vehículo tiene otro viaje en un horario solapado';
--     END IF;
-- 
--     SELECT COUNT(*)
--       INTO v_conflicts
--       FROM trip_instances other_trip
--      WHERE other_trip.id <> v_trip_id
--        AND other_trip.driver_id = v_driver_id
--        AND other_trip.status <> 'CANCELLED'
--        AND other_trip.scheduled_start_at < v_end_at
--        AND other_trip.scheduled_end_at > v_start_at;
-- 
--     IF v_conflicts > 0 THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El conductor tiene otro viaje en un horario solapado';
--     END IF;
-- 
--     UPDATE trip_instances
--        SET status = CASE WHEN v_auto_publish = 1 THEN 'PUBLISHED' ELSE 'DRAFT' END
--      WHERE id = v_trip_id;
-- 
--     IF p_generation_run_id IS NOT NULL THEN
--         UPDATE trip_generation_runs
--            SET generated_count = generated_count + 1
--          WHERE id = p_generation_run_id;
--     END IF;
-- 
--     COMMIT;
--     SELECT v_trip_id AS trip_id, 'GENERATED' AS generation_result;
-- END$$
-- 
-- -- ============================================================================
-- -- F. BUSCADOR Y DISPONIBILIDAD POR TRAMOS
-- -- ============================================================================
-- 
-- -- Lista viajes que contienen el origen y destino solicitados y calcula cupos
-- -- libres comprobando únicamente los segmentos [orden_origen, orden_destino).
-- CREATE PROCEDURE sp_search_trips(
--     IN p_service_date DATE,
--     IN p_direction VARCHAR(10),
--     IN p_origin_stop_id BIGINT UNSIGNED,
--     IN p_destination_stop_id BIGINT UNSIGNED
-- )
-- BEGIN
--     SELECT trip.id AS trip_id,
--            trip.trip_code,
--            route.code AS route_code,
--            route.name AS route_name,
--            route.direction,
--            origin_stop.stop_order AS origin_order,
--            origin_place.name AS origin_name,
--            origin_stop.scheduled_departure_at AS origin_departure_at,
--            destination_stop.stop_order AS destination_order,
--            destination_place.name AS destination_name,
--            destination_stop.scheduled_arrival_at AS destination_arrival_at,
--            vehicle.internal_code AS vehicle_code,
--            vehicle.plate,
--            trip.booking_opens_at,
--            trip.booking_closes_at,
--            CASE
--                WHEN CURRENT_TIMESTAMP < trip.booking_opens_at THEN 'NOT_OPEN'
--                WHEN CURRENT_TIMESTAMP >= trip.booking_closes_at THEN 'CLOSED'
--                ELSE 'OPEN'
--            END AS booking_state,
--            (
--                SELECT COUNT(*)
--                  FROM trip_seats candidate_seat
--                 WHERE candidate_seat.trip_id = trip.id
--                   AND candidate_seat.is_blocked = 0
--                   AND NOT EXISTS (
--                       SELECT 1
--                         FROM trip_segments requested_segment
--                         JOIN trip_seat_segments inventory
--                           ON inventory.trip_segment_id = requested_segment.id
--                          AND inventory.trip_seat_id = candidate_seat.id
--                        WHERE requested_segment.trip_id = trip.id
--                          AND requested_segment.segment_order >= origin_stop.stop_order
--                          AND requested_segment.segment_order < destination_stop.stop_order
--                          AND inventory.state <> 'AVAILABLE'
--                   )
--            ) AS available_seats
--       FROM trip_instances trip
--       JOIN transport_routes route
--         ON route.id = trip.route_id
--       JOIN trip_stop_times origin_stop
--         ON origin_stop.trip_id = trip.id
--        AND origin_stop.stop_id = p_origin_stop_id
--       JOIN trip_stop_times destination_stop
--         ON destination_stop.trip_id = trip.id
--        AND destination_stop.stop_id = p_destination_stop_id
--       JOIN route_stops origin_rule
--         ON origin_rule.id = origin_stop.route_stop_id
--       JOIN route_stops destination_rule
--         ON destination_rule.id = destination_stop.route_stop_id
--       JOIN transport_stops origin_place
--         ON origin_place.id = origin_stop.stop_id
--       JOIN transport_stops destination_place
--         ON destination_place.id = destination_stop.stop_id
--       JOIN vehicles vehicle
--         ON vehicle.id = trip.vehicle_id
--      WHERE trip.service_date = p_service_date
--        AND route.direction = p_direction
--        AND trip.status = 'PUBLISHED'
--        AND origin_stop.stop_order < destination_stop.stop_order
--        AND origin_rule.pickup_allowed = 1
--        AND destination_rule.dropoff_allowed = 1
--        AND (
--             (route.direction = 'IDA' AND destination_place.stop_type = 'SEDE')
--             OR (route.direction = 'VUELTA' AND origin_place.stop_type = 'SEDE')
--        )
--      ORDER BY origin_stop.scheduled_departure_at;
-- END$$
-- 
-- -- Lista los asientos disponibles para un intervalo concreto del viaje.
-- CREATE PROCEDURE sp_list_trip_seats(
--     IN p_trip_id BIGINT UNSIGNED,
--     IN p_origin_trip_stop_time_id BIGINT UNSIGNED,
--     IN p_destination_trip_stop_time_id BIGINT UNSIGNED
-- )
-- BEGIN
--     DECLARE v_origin_order SMALLINT UNSIGNED;
--     DECLARE v_destination_order SMALLINT UNSIGNED;
-- 
--     SET v_origin_order = (
--         SELECT stop_order
--           FROM trip_stop_times
--          WHERE id = p_origin_trip_stop_time_id
--            AND trip_id = p_trip_id
--          LIMIT 1
--     );
-- 
--     SET v_destination_order = (
--         SELECT stop_order
--           FROM trip_stop_times
--          WHERE id = p_destination_trip_stop_time_id
--            AND trip_id = p_trip_id
--          LIMIT 1
--     );
-- 
--     IF v_origin_order IS NULL
--        OR v_destination_order IS NULL
--        OR v_origin_order >= v_destination_order THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Origen/destino inválidos para el viaje';
--     END IF;
-- 
--     SELECT seat.id AS trip_seat_id,
--            seat.seat_number,
--            seat.seat_label,
--            CASE
--                WHEN seat.is_blocked = 1 THEN 'BLOCKED'
--                WHEN EXISTS (
--                    SELECT 1
--                      FROM trip_segments segment
--                      JOIN trip_seat_segments inventory
--                        ON inventory.trip_segment_id = segment.id
--                       AND inventory.trip_seat_id = seat.id
--                     WHERE segment.trip_id = p_trip_id
--                       AND segment.segment_order >= v_origin_order
--                       AND segment.segment_order < v_destination_order
--                       AND inventory.state <> 'AVAILABLE'
--                ) THEN 'OCCUPIED_IN_REQUESTED_RANGE'
--                ELSE 'AVAILABLE'
--            END AS availability
--       FROM trip_seats seat
--      WHERE seat.trip_id = p_trip_id
--      ORDER BY seat.seat_number;
-- END$$
-- 
-- -- ============================================================================
-- -- G. RESERVA ATÓMICA Y OPERACIÓN DEL VIAJE
-- -- ============================================================================
-- 
-- -- Confirma una reserva y bloquea sólo los segmentos solicitados.
-- -- Dos solicitudes concurrentes por el mismo asiento/tramo se serializan.
-- CREATE PROCEDURE sp_confirm_reservation(
--     IN p_trip_id BIGINT UNSIGNED,
--     IN p_worker_id BIGINT UNSIGNED,
--     IN p_trip_seat_id BIGINT UNSIGNED,
--     IN p_origin_trip_stop_time_id BIGINT UNSIGNED,
--     IN p_destination_trip_stop_time_id BIGINT UNSIGNED,
--     IN p_booking_group_uuid CHAR(36)
-- )
-- BEGIN
--     DECLARE v_trip_exists INT DEFAULT 0;
--     DECLARE v_worker_role VARCHAR(20);
--     DECLARE v_trip_status VARCHAR(30);
--     DECLARE v_direction VARCHAR(10);
--     DECLARE v_booking_opens_at DATETIME;
--     DECLARE v_booking_closes_at DATETIME;
--     DECLARE v_origin_order SMALLINT UNSIGNED;
--     DECLARE v_destination_order SMALLINT UNSIGNED;
--     DECLARE v_origin_type VARCHAR(20);
--     DECLARE v_destination_type VARCHAR(20);
--     DECLARE v_pickup_allowed TINYINT;
--     DECLARE v_dropoff_allowed TINYINT;
--     DECLARE v_expected_segments INT;
--     DECLARE v_inventory_rows INT;
--     DECLARE v_conflicting_segments INT;
--     DECLARE v_reservation_id BIGINT UNSIGNED;
--     DECLARE v_reservation_code VARCHAR(32);
--     DECLARE v_qr_token CHAR(36);
--     DECLARE v_qr_hash CHAR(64);
-- 
--     DECLARE EXIT HANDLER FOR SQLEXCEPTION
--     BEGIN
--         ROLLBACK;
--         RESIGNAL;
--     END;
-- 
--     START TRANSACTION;
-- 
--     SELECT COUNT(*)
--       INTO v_trip_exists
--       FROM trip_instances
--      WHERE id = p_trip_id;
-- 
--     IF v_trip_exists = 0 THEN
--         SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El viaje no existe';
--     END IF;
-- 
--     SELECT worker.role
--       INTO v_worker_role
--       FROM users worker
--      WHERE worker.id = p_worker_id
--        AND worker.active = 1;
-- 
--     IF v_worker_role IS NULL OR v_worker_role <> 'WORKER' THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'La reserva sólo puede asignarse a un WORKER activo';
--     END IF;
-- 
--     SELECT trip.status,
--            route.direction,
--            trip.booking_opens_at,
--            trip.booking_closes_at
--       INTO v_trip_status,
--            v_direction,
--            v_booking_opens_at,
--            v_booking_closes_at
--       FROM trip_instances trip
--       JOIN transport_routes route ON route.id = trip.route_id
--      WHERE trip.id = p_trip_id
--      FOR UPDATE;
-- 
--     IF v_trip_status <> 'PUBLISHED' THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El viaje no está publicado para reservas';
--     END IF;
-- 
--     IF CURRENT_TIMESTAMP < v_booking_opens_at
--        OR CURRENT_TIMESTAMP >= v_booking_closes_at THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'La ventana de reserva no está abierta';
--     END IF;
-- 
--     IF NOT EXISTS (
--         SELECT 1
--           FROM trip_seats
--          WHERE id = p_trip_seat_id
--            AND trip_id = p_trip_id
--            AND is_blocked = 0
--     ) THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El asiento no pertenece al viaje o está bloqueado';
--     END IF;
-- 
--     SELECT origin_time.stop_order,
--            destination_time.stop_order,
--            origin_place.stop_type,
--            destination_place.stop_type,
--            origin_rule.pickup_allowed,
--            destination_rule.dropoff_allowed
--       INTO v_origin_order,
--            v_destination_order,
--            v_origin_type,
--            v_destination_type,
--            v_pickup_allowed,
--            v_dropoff_allowed
--       FROM trip_stop_times origin_time
--       JOIN trip_stop_times destination_time
--         ON destination_time.id = p_destination_trip_stop_time_id
--        AND destination_time.trip_id = origin_time.trip_id
--       JOIN route_stops origin_rule
--         ON origin_rule.id = origin_time.route_stop_id
--       JOIN route_stops destination_rule
--         ON destination_rule.id = destination_time.route_stop_id
--       JOIN transport_stops origin_place
--         ON origin_place.id = origin_time.stop_id
--       JOIN transport_stops destination_place
--         ON destination_place.id = destination_time.stop_id
--      WHERE origin_time.id = p_origin_trip_stop_time_id
--        AND origin_time.trip_id = p_trip_id;
-- 
--     IF v_origin_order IS NULL
--        OR v_destination_order IS NULL
--        OR v_origin_order >= v_destination_order THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El origen y destino no forman un tramo válido';
--     END IF;
-- 
--     IF v_pickup_allowed = 0 OR v_dropoff_allowed = 0 THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'La ruta no permite subir o bajar en los puntos elegidos';
--     END IF;
-- 
--     -- Reglas direccionales proporcionadas por negocio.
--     IF v_direction = 'IDA' AND v_destination_type <> 'SEDE' THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'En IDA, el destino de la reserva debe ser una SEDE';
--     END IF;
-- 
--     IF v_direction = 'VUELTA' AND v_origin_type <> 'SEDE' THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'En VUELTA, el origen de la reserva debe ser una SEDE';
--     END IF;
-- 
--     SET v_expected_segments = v_destination_order - v_origin_order;
-- 
--     -- UPDATE sin cambio funcional: bloquea las filas exactas en InnoDB.
--     UPDATE trip_seat_segments inventory
--     JOIN trip_segments segment
--       ON segment.id = inventory.trip_segment_id
--      SET inventory.updated_at = CURRENT_TIMESTAMP
--    WHERE inventory.trip_seat_id = p_trip_seat_id
--      AND segment.trip_id = p_trip_id
--      AND segment.segment_order >= v_origin_order
--      AND segment.segment_order < v_destination_order;
-- 
--     SELECT COUNT(*),
--            SUM(CASE WHEN inventory.state <> 'AVAILABLE' THEN 1 ELSE 0 END)
--       INTO v_inventory_rows, v_conflicting_segments
--       FROM trip_seat_segments inventory
--       JOIN trip_segments segment
--         ON segment.id = inventory.trip_segment_id
--      WHERE inventory.trip_seat_id = p_trip_seat_id
--        AND segment.trip_id = p_trip_id
--        AND segment.segment_order >= v_origin_order
--        AND segment.segment_order < v_destination_order;
-- 
--     IF v_inventory_rows <> v_expected_segments THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El inventario por segmentos del asiento está incompleto';
--     END IF;
-- 
--     IF COALESCE(v_conflicting_segments, 0) > 0 THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El asiento ya está ocupado en uno o más tramos solicitados';
--     END IF;
-- 
--     SET v_reservation_code = CONCAT(
--         'R-', UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 24))
--     );
--     SET v_qr_token = UUID();
--     SET v_qr_hash = SHA2(v_qr_token, 256);
-- 
--     INSERT INTO reservations (
--         reservation_code,
--         qr_token_hash,
--         booking_group_uuid,
--         trip_id,
--         worker_id,
--         trip_seat_id,
--         origin_trip_stop_time_id,
--         destination_trip_stop_time_id,
--         origin_stop_order,
--         destination_stop_order,
--         status,
--         created_by_user_id
--     ) VALUES (
--         v_reservation_code,
--         v_qr_hash,
--         p_booking_group_uuid,
--         p_trip_id,
--         p_worker_id,
--         p_trip_seat_id,
--         p_origin_trip_stop_time_id,
--         p_destination_trip_stop_time_id,
--         v_origin_order,
--         v_destination_order,
--         'CONFIRMED',
--         p_worker_id
--     );
-- 
--     SET v_reservation_id = LAST_INSERT_ID();
-- 
--     INSERT INTO reservation_segments (
--         reservation_id,
--         trip_segment_id,
--         allocation_status
--     )
--     SELECT v_reservation_id,
--            segment.id,
--            'RESERVED'
--       FROM trip_segments segment
--      WHERE segment.trip_id = p_trip_id
--        AND segment.segment_order >= v_origin_order
--        AND segment.segment_order < v_destination_order;
-- 
--     UPDATE trip_seat_segments inventory
--     JOIN trip_segments segment
--       ON segment.id = inventory.trip_segment_id
--      SET inventory.state = 'RESERVED',
--          inventory.reservation_id = v_reservation_id,
--          inventory.reserved_at = CURRENT_TIMESTAMP,
--          inventory.released_at = NULL
--    WHERE inventory.trip_seat_id = p_trip_seat_id
--      AND segment.trip_id = p_trip_id
--      AND segment.segment_order >= v_origin_order
--      AND segment.segment_order < v_destination_order;
-- 
--     INSERT INTO reservation_events (
--         reservation_id,
--         event_type,
--         trip_stop_time_id,
--         actor_user_id,
--         details
--     ) VALUES (
--         v_reservation_id,
--         'CONFIRMED',
--         p_origin_trip_stop_time_id,
--         p_worker_id,
--         CONCAT(
--             'Asiento bloqueado desde el orden ', v_origin_order,
--             ' hasta antes del orden ', v_destination_order
--         )
--     );
-- 
--     COMMIT;
-- 
--     SELECT id,
--            reservation_code,
--            v_qr_token AS qr_token,
--            status,
--            origin_stop_order,
--            destination_stop_order
--       FROM reservations
--      WHERE id = v_reservation_id;
-- END$$
-- 
-- -- El conductor registra la llegada física que inicia el reloj de tolerancia.
-- CREATE PROCEDURE sp_mark_trip_stop_arrival(
--     IN p_trip_stop_time_id BIGINT UNSIGNED,
--     IN p_driver_id BIGINT UNSIGNED
-- )
-- BEGIN
--     DECLARE v_assigned_driver_id BIGINT UNSIGNED;
--     DECLARE v_trip_status VARCHAR(30);
-- 
--     SELECT trip.driver_id, trip.status
--       INTO v_assigned_driver_id, v_trip_status
--       FROM trip_stop_times stop_time
--       JOIN trip_instances trip ON trip.id = stop_time.trip_id
--      WHERE stop_time.id = p_trip_stop_time_id;
-- 
--     IF v_assigned_driver_id IS NULL OR v_assigned_driver_id <> p_driver_id THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Sólo el conductor asignado puede marcar la llegada';
--     END IF;
-- 
--     IF v_trip_status IN ('COMPLETED', 'CANCELLED') THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'El viaje no admite nuevas marcas de llegada';
--     END IF;
-- 
--     UPDATE trip_stop_times
--        SET actual_arrival_at = COALESCE(actual_arrival_at, CURRENT_TIMESTAMP),
--            arrival_marked_by_user_id = COALESCE(arrival_marked_by_user_id, p_driver_id),
--            status = CASE WHEN status = 'PENDING' THEN 'ARRIVED' ELSE status END
--      WHERE id = p_trip_stop_time_id;
-- END$$
-- 
-- -- Confirma que el trabajador abordó en su parada de origen.
-- CREATE PROCEDURE sp_mark_reservation_boarded(
--     IN p_reservation_id BIGINT UNSIGNED,
--     IN p_driver_id BIGINT UNSIGNED
-- )
-- BEGIN
--     DECLARE v_status VARCHAR(20);
--     DECLARE v_assigned_driver_id BIGINT UNSIGNED;
--     DECLARE v_origin_stop_time_id BIGINT UNSIGNED;
--     DECLARE v_actual_arrival_at DATETIME;
--     DECLARE v_effective_at DATETIME;
-- 
--     DECLARE EXIT HANDLER FOR SQLEXCEPTION
--     BEGIN
--         ROLLBACK;
--         RESIGNAL;
--     END;
-- 
--     START TRANSACTION;
--     SET v_effective_at = CURRENT_TIMESTAMP;
-- 
--     SELECT reservation.status,
--            trip.driver_id,
--            reservation.origin_trip_stop_time_id,
--            origin_stop.actual_arrival_at
--       INTO v_status,
--            v_assigned_driver_id,
--            v_origin_stop_time_id,
--            v_actual_arrival_at
--       FROM reservations reservation
--       JOIN trip_instances trip ON trip.id = reservation.trip_id
--       JOIN trip_stop_times origin_stop
--         ON origin_stop.id = reservation.origin_trip_stop_time_id
--      WHERE reservation.id = p_reservation_id
--      FOR UPDATE;
-- 
--     IF v_status <> 'CONFIRMED' THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'La reserva no está CONFIRMED';
--     END IF;
-- 
--     IF v_assigned_driver_id <> p_driver_id THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Sólo el conductor asignado puede confirmar el abordaje';
--     END IF;
-- 
--     IF v_actual_arrival_at IS NULL OR v_effective_at < v_actual_arrival_at THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Primero debe registrarse la llegada física al punto de subida';
--     END IF;
-- 
--     UPDATE reservations
--        SET status = 'BOARDED',
--            boarded_at = v_effective_at
--      WHERE id = p_reservation_id;
-- 
--     UPDATE trip_seat_segments
--        SET state = 'OCCUPIED'
--      WHERE reservation_id = p_reservation_id
--        AND state = 'RESERVED';
-- 
--     UPDATE reservation_segments
--        SET allocation_status = 'OCCUPIED'
--      WHERE reservation_id = p_reservation_id
--        AND allocation_status = 'RESERVED';
-- 
--     INSERT INTO reservation_events (
--         reservation_id,
--         event_type,
--         trip_stop_time_id,
--         actor_user_id,
--         event_at,
--         details
--     ) VALUES (
--         p_reservation_id,
--         'BOARDED',
--         v_origin_stop_time_id,
--         p_driver_id,
--         v_effective_at,
--         'Abordaje confirmado por el conductor'
--     );
-- 
--     COMMIT;
-- END$$
-- 
-- -- Marca NO_SHOW únicamente después de que el conductor registró la llegada
-- -- al punto de subida y venció la tolerancia. Libera todos los segmentos
-- -- reservados porque el trabajador nunca abordó.
-- CREATE PROCEDURE sp_mark_reservation_no_show(
--     IN p_reservation_id BIGINT UNSIGNED,
--     IN p_driver_id BIGINT UNSIGNED
-- )
-- BEGIN
--     DECLARE v_status VARCHAR(20);
--     DECLARE v_assigned_driver_id BIGINT UNSIGNED;
--     DECLARE v_origin_stop_time_id BIGINT UNSIGNED;
--     DECLARE v_actual_arrival_at DATETIME;
--     DECLARE v_tolerance_minutes SMALLINT UNSIGNED;
--     DECLARE v_effective_at DATETIME;
--     DECLARE v_release_count INT DEFAULT 0;
-- 
--     DECLARE EXIT HANDLER FOR SQLEXCEPTION
--     BEGIN
--         ROLLBACK;
--         RESIGNAL;
--     END;
-- 
--     START TRANSACTION;
--     SET v_effective_at = CURRENT_TIMESTAMP;
-- 
--     SELECT reservation.status,
--            trip.driver_id,
--            reservation.origin_trip_stop_time_id,
--            origin_stop.actual_arrival_at,
--            trip.no_show_tolerance_minutes
--       INTO v_status,
--            v_assigned_driver_id,
--            v_origin_stop_time_id,
--            v_actual_arrival_at,
--            v_tolerance_minutes
--       FROM reservations reservation
--       JOIN trip_instances trip ON trip.id = reservation.trip_id
--       JOIN trip_stop_times origin_stop
--         ON origin_stop.id = reservation.origin_trip_stop_time_id
--      WHERE reservation.id = p_reservation_id
--      FOR UPDATE;
-- 
--     IF v_status <> 'CONFIRMED' THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Sólo una reserva CONFIRMED puede pasar a NO_SHOW';
--     END IF;
-- 
--     IF v_assigned_driver_id <> p_driver_id THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Sólo el conductor asignado puede marcar NO_SHOW';
--     END IF;
-- 
--     IF v_actual_arrival_at IS NULL THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Debe registrarse la llegada física al punto de subida';
--     END IF;
-- 
--     IF v_effective_at < DATE_ADD(
--         v_actual_arrival_at,
--         INTERVAL v_tolerance_minutes MINUTE
--     ) THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Aún no terminó el tiempo de tolerancia de NO_SHOW';
--     END IF;
-- 
--     UPDATE trip_seat_segments
--        SET state = 'AVAILABLE',
--            reservation_id = NULL,
--            released_at = v_effective_at
--      WHERE reservation_id = p_reservation_id
--        AND state = 'RESERVED';
-- 
--     SET v_release_count = ROW_COUNT();
-- 
--     UPDATE reservation_segments
--        SET allocation_status = 'RELEASED',
--            released_at = v_effective_at
--      WHERE reservation_id = p_reservation_id
--        AND allocation_status = 'RESERVED';
-- 
--     UPDATE reservations
--        SET status = 'NO_SHOW',
--            no_show_at = v_effective_at,
--            no_show_by_user_id = p_driver_id,
--            no_show_trip_stop_time_id = v_origin_stop_time_id
--      WHERE id = p_reservation_id;
-- 
--     INSERT INTO reservation_events (
--         reservation_id,
--         event_type,
--         trip_stop_time_id,
--         actor_user_id,
--         event_at,
--         details
--     ) VALUES (
--         p_reservation_id,
--         'NO_SHOW',
--         v_origin_stop_time_id,
--         p_driver_id,
--         v_effective_at,
--         CONCAT('No abordó después de ', v_tolerance_minutes, ' minutos')
--     );
-- 
--     INSERT INTO reservation_events (
--         reservation_id,
--         event_type,
--         trip_stop_time_id,
--         actor_user_id,
--         event_at,
--         details
--     ) VALUES (
--         p_reservation_id,
--         'SEGMENTS_RELEASED',
--         v_origin_stop_time_id,
--         p_driver_id,
--         v_effective_at,
--         CONCAT(v_release_count, ' segmentos liberados por NO_SHOW')
--     );
-- 
--     COMMIT;
-- END$$
-- 
-- -- Cierra la reserva al bajar en su destino. Los segmentos posteriores ya
-- -- estaban disponibles porque nunca se bloquearon.
-- CREATE PROCEDURE sp_mark_reservation_alighted(
--     IN p_reservation_id BIGINT UNSIGNED,
--     IN p_driver_id BIGINT UNSIGNED
-- )
-- BEGIN
--     DECLARE v_status VARCHAR(20);
--     DECLARE v_assigned_driver_id BIGINT UNSIGNED;
--     DECLARE v_destination_stop_time_id BIGINT UNSIGNED;
--     DECLARE v_actual_arrival_at DATETIME;
--     DECLARE v_effective_at DATETIME;
-- 
--     DECLARE EXIT HANDLER FOR SQLEXCEPTION
--     BEGIN
--         ROLLBACK;
--         RESIGNAL;
--     END;
-- 
--     START TRANSACTION;
--     SET v_effective_at = CURRENT_TIMESTAMP;
-- 
--     SELECT reservation.status,
--            trip.driver_id,
--            reservation.destination_trip_stop_time_id,
--            destination_stop.actual_arrival_at
--       INTO v_status,
--            v_assigned_driver_id,
--            v_destination_stop_time_id,
--            v_actual_arrival_at
--       FROM reservations reservation
--       JOIN trip_instances trip ON trip.id = reservation.trip_id
--       JOIN trip_stop_times destination_stop
--         ON destination_stop.id = reservation.destination_trip_stop_time_id
--      WHERE reservation.id = p_reservation_id
--      FOR UPDATE;
-- 
--     IF v_status <> 'BOARDED' THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Sólo una reserva BOARDED puede finalizar';
--     END IF;
-- 
--     IF v_assigned_driver_id <> p_driver_id THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Sólo el conductor asignado puede confirmar la bajada';
--     END IF;
-- 
--     IF v_actual_arrival_at IS NULL OR v_effective_at < v_actual_arrival_at THEN
--         SIGNAL SQLSTATE '45000'
--             SET MESSAGE_TEXT = 'Primero debe registrarse la llegada al destino';
--     END IF;
-- 
--     UPDATE reservations
--        SET status = 'COMPLETED',
--            completed_at = v_effective_at
--      WHERE id = p_reservation_id;
-- 
--     UPDATE trip_seat_segments
--        SET state = 'USED'
--      WHERE reservation_id = p_reservation_id
--        AND state = 'OCCUPIED';
-- 
--     UPDATE reservation_segments
--        SET allocation_status = 'USED'
--      WHERE reservation_id = p_reservation_id
--        AND allocation_status = 'OCCUPIED';
-- 
--     INSERT INTO reservation_events (
--         reservation_id,
--         event_type,
--         trip_stop_time_id,
--         actor_user_id,
--         event_at,
--         details
--     ) VALUES (
--         p_reservation_id,
--         'ALIGHTED',
--         v_destination_stop_time_id,
--         p_driver_id,
--         v_effective_at,
--         'Bajada confirmada en el destino programado'
--     );
-- 
--     COMMIT;
-- END$$
-- 
-- DELIMITER ;

-- ============================================================================
-- H. VISTAS DE APOYO (NO DUPLICAN DATOS)
-- ============================================================================

-- Matriz manual legible para administración.
CREATE VIEW vw_route_time_matrix AS
SELECT route.id AS route_id,
       route.code AS route_code,
       route.name AS route_name,
       route.direction,
       segment.id AS route_segment_id,
       segment.segment_order,
       from_place.code AS from_stop_code,
       from_place.name AS from_stop_name,
       to_place.code AS to_stop_code,
       to_place.name AS to_stop_name,
       profile.id AS profile_id,
       profile.code AS profile_code,
       profile.name AS profile_name,
       matrix_time.travel_minutes,
       profile.priority
  FROM route_segment_travel_times matrix_time
  JOIN route_segments segment
    ON segment.id = matrix_time.route_segment_id
  JOIN transport_routes route
    ON route.id = segment.route_id
  JOIN route_stops from_route_stop
    ON from_route_stop.id = segment.from_route_stop_id
  JOIN route_stops to_route_stop
    ON to_route_stop.id = segment.to_route_stop_id
  JOIN transport_stops from_place
    ON from_place.id = from_route_stop.stop_id
  JOIN transport_stops to_place
    ON to_place.id = to_route_stop.stop_id
  JOIN travel_time_profiles profile
    ON profile.id = matrix_time.profile_id;

-- Permite ver exactamente dónde un asiento está libre u ocupado.
CREATE VIEW vw_trip_segment_seat_availability AS
SELECT trip.id AS trip_id,
       trip.trip_code,
       trip.service_date,
       route.direction,
       seat.id AS trip_seat_id,
       seat.seat_number,
       seat.seat_label,
       segment.segment_order,
       from_place.name AS available_or_occupied_from,
       to_place.name AS available_or_occupied_until,
       inventory.state,
       inventory.reservation_id,
       reservation.reservation_code,
       inventory.reserved_at,
       inventory.released_at
  FROM trip_seat_segments inventory
  JOIN trip_seats seat
    ON seat.id = inventory.trip_seat_id
  JOIN trip_instances trip
    ON trip.id = seat.trip_id
  JOIN transport_routes route
    ON route.id = trip.route_id
  JOIN trip_segments segment
    ON segment.id = inventory.trip_segment_id
  JOIN trip_stop_times from_stop
    ON from_stop.id = segment.from_trip_stop_time_id
  JOIN trip_stop_times to_stop
    ON to_stop.id = segment.to_trip_stop_time_id
  JOIN transport_stops from_place
    ON from_place.id = from_stop.stop_id
  JOIN transport_stops to_place
    ON to_place.id = to_stop.stop_id
  LEFT JOIN reservations reservation
    ON reservation.id = inventory.reservation_id;

-- Detecta asignaciones solapadas sin agregar tablas de bloques al MVP.
CREATE VIEW vw_schedule_conflicts AS
SELECT 'VEHICLE' AS resource_type,
       first_trip.vehicle_id AS resource_id,
       first_trip.id AS first_trip_id,
       second_trip.id AS second_trip_id,
       first_trip.scheduled_start_at AS first_start_at,
       first_trip.scheduled_end_at AS first_end_at,
       second_trip.scheduled_start_at AS second_start_at,
       second_trip.scheduled_end_at AS second_end_at
  FROM trip_instances first_trip
  JOIN trip_instances second_trip
    ON second_trip.id > first_trip.id
   AND second_trip.vehicle_id = first_trip.vehicle_id
   AND second_trip.status <> 'CANCELLED'
   AND first_trip.status <> 'CANCELLED'
   AND second_trip.scheduled_start_at < first_trip.scheduled_end_at
   AND second_trip.scheduled_end_at > first_trip.scheduled_start_at
UNION ALL
SELECT 'DRIVER' AS resource_type,
       first_trip.driver_id AS resource_id,
       first_trip.id AS first_trip_id,
       second_trip.id AS second_trip_id,
       first_trip.scheduled_start_at AS first_start_at,
       first_trip.scheduled_end_at AS first_end_at,
       second_trip.scheduled_start_at AS second_start_at,
       second_trip.scheduled_end_at AS second_end_at
  FROM trip_instances first_trip
  JOIN trip_instances second_trip
    ON second_trip.id > first_trip.id
   AND second_trip.driver_id = first_trip.driver_id
   AND second_trip.status <> 'CANCELLED'
   AND first_trip.status <> 'CANCELLED'
   AND second_trip.scheduled_start_at < first_trip.scheduled_end_at
   AND second_trip.scheduled_end_at > first_trip.scheduled_start_at;
