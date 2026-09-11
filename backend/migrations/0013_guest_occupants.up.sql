-- =============================================================
-- 0013_guest_occupants.up.sql
-- Ocupantes invitados: pasajeros sin app (solo nombre+apellido)
-- registrados por el conductor. Nacen abordados.
-- Portado de feat/user-extra-fields (0d6f9c1) a migracion INCREMENTAL:
-- en la rama vivia dentro de 0001_schema.up.sql (que se re-ejecuta en
-- cada arranque); en main 0001 ya esta aplicada y schema_migrations la
-- salta, por eso la tabla debe crearse aca.
-- =============================================================

-- 22b. Ocupantes invitados: pasajeros sin app registrados por el conductor
--     (solo nombre + apellido, sin usuario ni DNI). Nacen abordados.
CREATE TABLE guest_occupants (
    id                              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id                         BIGINT UNSIGNED NOT NULL,
    trip_seat_id                    BIGINT UNSIGNED NOT NULL,
    origin_trip_stop_time_id        BIGINT UNSIGNED NOT NULL,
    destination_trip_stop_time_id   BIGINT UNSIGNED NOT NULL,
    origin_stop_order               SMALLINT UNSIGNED NOT NULL,
    destination_stop_order          SMALLINT UNSIGNED NOT NULL,
    first_name                      VARCHAR(100) NOT NULL,
    last_name                       VARCHAR(100) NOT NULL,
    registered_by_user_id           BIGINT UNSIGNED NOT NULL,
    status                          ENUM('BOARDED') NOT NULL DEFAULT 'BOARDED',
    created_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_guest_occupants_trip
        FOREIGN KEY (trip_id) REFERENCES trip_instances(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_guest_occupants_seat
        FOREIGN KEY (trip_seat_id) REFERENCES trip_seats(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_guest_occupants_origin
        FOREIGN KEY (origin_trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_guest_occupants_destination
        FOREIGN KEY (destination_trip_stop_time_id) REFERENCES trip_stop_times(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_guest_occupants_registered_by
        FOREIGN KEY (registered_by_user_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- La columna vive en el inventario pero su FK exige que guest_occupants
-- exista primero: por eso este ALTER va aquí y no junto a trip_seat_segments.
ALTER TABLE trip_seat_segments
    ADD COLUMN guest_occupant_id BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_trip_seat_segments_guest
        FOREIGN KEY (guest_occupant_id) REFERENCES guest_occupants(id)
        ON UPDATE RESTRICT ON DELETE SET NULL;


DELIMITER $$
-- Registra un pasajero sin app (solo nombre + apellido) y ocupa el asiento
-- en los segmentos solicitados. Espejo de sp_confirm_reservation sin QR.
CREATE PROCEDURE sp_register_guest_occupant(
    IN p_trip_id BIGINT UNSIGNED,
    IN p_trip_seat_id BIGINT UNSIGNED,
    IN p_origin_trip_stop_time_id BIGINT UNSIGNED,
    IN p_destination_trip_stop_time_id BIGINT UNSIGNED,
    IN p_first_name VARCHAR(100),
    IN p_last_name VARCHAR(100),
    IN p_registered_by_user_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_origin_order SMALLINT UNSIGNED;
    DECLARE v_destination_order SMALLINT UNSIGNED;
    DECLARE v_expected_segments INT;
    DECLARE v_inventory_rows INT;
    DECLARE v_conflicting_segments INT;
    DECLARE v_duplicate INT;
    DECLARE v_guest_id BIGINT UNSIGNED;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    IF NOT EXISTS (
        SELECT 1 FROM trip_seats
         WHERE id = p_trip_seat_id
           AND trip_id = p_trip_id
           AND is_blocked = 0
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El asiento no pertenece al viaje o está bloqueado';
    END IF;

    SELECT origin_time.stop_order, destination_time.stop_order
      INTO v_origin_order, v_destination_order
      FROM trip_stop_times origin_time
      JOIN trip_stop_times destination_time
        ON destination_time.id = p_destination_trip_stop_time_id
       AND destination_time.trip_id = origin_time.trip_id
     WHERE origin_time.id = p_origin_trip_stop_time_id
       AND origin_time.trip_id = p_trip_id;

    IF v_origin_order IS NULL
       OR v_destination_order IS NULL
       OR v_origin_order >= v_destination_order THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El origen y destino no forman un tramo válido';
    END IF;

    SELECT COUNT(*)
      INTO v_duplicate
      FROM guest_occupants
     WHERE trip_id = p_trip_id
       AND LOWER(first_name) = LOWER(p_first_name)
       AND LOWER(last_name) = LOWER(p_last_name);

    IF v_duplicate > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Ese pasajero ya está registrado en este viaje';
    END IF;

    UPDATE trip_seat_segments inventory
    JOIN trip_segments segment
      ON segment.id = inventory.trip_segment_id
     SET inventory.updated_at = CURRENT_TIMESTAMP
   WHERE inventory.trip_seat_id = p_trip_seat_id
     AND segment.trip_id = p_trip_id
     AND segment.segment_order >= v_origin_order
     AND segment.segment_order < v_destination_order;

    SELECT COUNT(*),
           SUM(CASE WHEN inventory.state <> 'AVAILABLE' THEN 1 ELSE 0 END)
      INTO v_inventory_rows, v_conflicting_segments
      FROM trip_seat_segments inventory
      JOIN trip_segments segment
        ON segment.id = inventory.trip_segment_id
     WHERE inventory.trip_seat_id = p_trip_seat_id
       AND segment.trip_id = p_trip_id
       AND segment.segment_order >= v_origin_order
       AND segment.segment_order < v_destination_order;

    SET v_expected_segments = v_destination_order - v_origin_order;

    IF v_inventory_rows <> v_expected_segments THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El inventario por segmentos del asiento está incompleto';
    END IF;

    IF COALESCE(v_conflicting_segments, 0) > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El asiento ya está ocupado en uno o más tramos solicitados';
    END IF;

    INSERT INTO guest_occupants (
        trip_id, trip_seat_id,
        origin_trip_stop_time_id, destination_trip_stop_time_id,
        origin_stop_order, destination_stop_order,
        first_name, last_name, registered_by_user_id, status
    ) VALUES (
        p_trip_id, p_trip_seat_id,
        p_origin_trip_stop_time_id, p_destination_trip_stop_time_id,
        v_origin_order, v_destination_order,
        p_first_name, p_last_name, p_registered_by_user_id, 'BOARDED'
    );

    SET v_guest_id = LAST_INSERT_ID();

    UPDATE trip_seat_segments inventory
    JOIN trip_segments segment
      ON segment.id = inventory.trip_segment_id
     SET inventory.state = 'OCCUPIED',
         inventory.guest_occupant_id = v_guest_id,
         inventory.reserved_at = CURRENT_TIMESTAMP,
         inventory.released_at = NULL
   WHERE inventory.trip_seat_id = p_trip_seat_id
     AND segment.trip_id = p_trip_id
     AND segment.segment_order >= v_origin_order
     AND segment.segment_order < v_destination_order;

    COMMIT;

    SELECT v_guest_id AS id;
END$$

DELIMITER ;
