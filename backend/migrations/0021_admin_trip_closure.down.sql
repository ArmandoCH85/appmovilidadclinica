-- ============================================================================
-- 0021_admin_trip_closure.down.sql
-- Revierte la 0021: elimina sp_admin_close_trip, restaura sp_cancel_trip al
-- cuerpo de 0002 (sin rama de invitados) y devuelve guest_occupants.status a
-- ENUM('BOARDED','COMPLETED').
--
-- OJO: los invitados que quedaron en CANCELLED se reabren a BOARDED antes de
-- angostar el ENUM (MariaDB rechaza el ALTER si hay filas con un valor que no
-- existe en el ENUM destino). Sus segmentos de asiento NO se devuelven a
-- OCCUPIED: quedan como esten.
--
-- OJO 2: al revertir, cerrar o cancelar un viaje desde el panel vuelve a
-- saltearse el dominio (el bug que la 0021 cierra).
--
-- Nota: el cuerpo de sp_cancel_trip es el de 0002_cancel_sps.up.sql copiado
-- aqui a proposito (el runner no ejecuta `source`).
-- ============================================================================

UPDATE guest_occupants
   SET status = 'BOARDED'
 WHERE status = 'CANCELLED';

ALTER TABLE guest_occupants
    MODIFY COLUMN status ENUM('BOARDED','COMPLETED') NOT NULL DEFAULT 'BOARDED';

DROP PROCEDURE IF EXISTS sp_admin_close_trip;
DROP PROCEDURE IF EXISTS sp_cancel_trip;

DELIMITER $$

CREATE PROCEDURE sp_cancel_trip(
    IN p_trip_id BIGINT UNSIGNED,
    IN p_reason VARCHAR(255),
    IN p_actor_user_id BIGINT UNSIGNED
)
proc_block: BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_reservation_id BIGINT UNSIGNED;
    DECLARE v_trip_status VARCHAR(30);

    DECLARE reservation_cursor CURSOR FOR
        SELECT id
          FROM reservations
         WHERE trip_id = p_trip_id
           AND status IN ('CONFIRMED', 'BOARDED');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT status INTO v_trip_status
      FROM trip_instances
     WHERE id = p_trip_id
     FOR UPDATE;

    IF v_trip_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no existe';
    END IF;

    IF v_trip_status = 'CANCELLED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje ya esta cancelado';
    END IF;

    OPEN reservation_cursor;
    read_loop: LOOP
        FETCH reservation_cursor INTO v_reservation_id;
        IF v_done = 1 THEN
            LEAVE read_loop;
        END IF;

        -- Misma logica que sp_cancel_reservation, inlineada para evitar
        -- transacciones anidadas.
        UPDATE trip_seat_segments
           SET state = 'AVAILABLE',
               reservation_id = NULL,
               released_at = CURRENT_TIMESTAMP
         WHERE reservation_id = v_reservation_id
           AND state IN ('RESERVED', 'OCCUPIED');

        UPDATE reservation_segments
           SET allocation_status = 'RELEASED',
               released_at = CURRENT_TIMESTAMP
         WHERE reservation_id = v_reservation_id
           AND allocation_status IN ('RESERVED', 'OCCUPIED');

        UPDATE reservations
           SET status = 'CANCELLED'
         WHERE id = v_reservation_id;

        INSERT INTO reservation_events (
            reservation_id, event_type, actor_user_id, details
        ) VALUES (
            v_reservation_id, 'CANCELLED', p_actor_user_id, 'Viaje cancelado'
        );
    END LOOP;
    CLOSE reservation_cursor;

    UPDATE trip_instances
       SET status = 'CANCELLED',
           cancellation_reason = p_reason
     WHERE id = p_trip_id;

    COMMIT;
END$$

DELIMITER ;
