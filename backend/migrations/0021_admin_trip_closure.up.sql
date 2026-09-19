-- ============================================================================
-- 0021_admin_trip_closure.up.sql
-- Cerrar un viaje desde el panel de administracion deja de saltearse el
-- dominio.
--
-- Bug: POST /admin/trips/{id}/status hacia un UPDATE pelado de
-- trip_instances.status (admin/repository.go), asi que un ADMIN podia marcar
-- COMPLETED o CANCELLED sin ejecutar el cierre del manifiesto:
--   - COMPLETED: no seteaba actual_end_at, no cerraba las reservas BOARDED ni
--     los invitados, y sus segmentos de asiento quedaban OCCUPIED (asiento
--     fantasma). Evidencia en produccion: viajes 133 y 195 quedaron asi, y son
--     el origen de los invitados huerfanos que se backfillearon y de las 3
--     reservas BOARDED del viaje 195.
--   - CANCELLED: se salteaba sp_cancel_trip, dejando las reservas activas sin
--     cancelar y los asientos sin liberar.
--
-- Ademas, sp_cancel_trip solo recorria `reservations`: los invitados de la
-- 0013 no tienen reserva y quedaban BOARDED con el asiento OCCUPIED para
-- siempre. Se agrega esa rama.
--
-- Que trae esta migracion:
--   1. guest_occupants.status pasa a ENUM('BOARDED','COMPLETED','CANCELLED').
--   2. sp_admin_close_trip(trip_id, actor_id): cierre por ADMIN. Se usa una SP
--      propia en vez de sp_complete_trip porque esa exige que el viaje este
--      IN_PROGRESS y que el actor sea el conductor asignado: el ADMIN debe
--      poder cerrar desde PUBLISHED/BOARDING/IN_PROGRESS y la auditoria no
--      debe atribuirle la accion al conductor.
--   3. sp_cancel_trip recreada con la rama de invitados.
--
-- Cuerpo base de sp_cancel_trip: 0002_cancel_sps.up.sql (con la rama nueva
-- insertada antes del UPDATE final del viaje).
-- ============================================================================

ALTER TABLE guest_occupants
    MODIFY COLUMN status ENUM('BOARDED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'BOARDED';

DROP PROCEDURE IF EXISTS sp_cancel_trip;
DROP PROCEDURE IF EXISTS sp_admin_close_trip;

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

    -- 0021: los invitados (0013) no tienen reserva, asi que el cursor de
    -- arriba no los alcanza. Sin esto quedaban BOARDED y con el asiento
    -- OCCUPIED para siempre en un viaje cancelado.
    -- Los segmentos se liberan ANTES de cerrar al invitado, porque el
    -- subquery filtra por guest_occupants.status = 'BOARDED'.
    UPDATE trip_seat_segments
       SET state = 'AVAILABLE',
           guest_occupant_id = NULL,
           released_at = CURRENT_TIMESTAMP
     WHERE state IN ('RESERVED', 'OCCUPIED')
       AND guest_occupant_id IN (
           SELECT id FROM guest_occupants
            WHERE trip_id = p_trip_id
              AND status = 'BOARDED'
       );

    UPDATE guest_occupants
       SET status = 'CANCELLED'
     WHERE trip_id = p_trip_id
       AND status = 'BOARDED';

    UPDATE trip_instances
       SET status = 'CANCELLED',
           cancellation_reason = p_reason
     WHERE id = p_trip_id;

    COMMIT;
END$$

-- Cierre de viaje desde el panel de administracion. Cierra reservas e
-- invitados BOARDED y marca sus segmentos de asiento como USED, igual que
-- sp_complete_trip (0017) y sp_complete_trip + invitados (0018), pero sin
-- exigir conductor ni estado IN_PROGRESS.
CREATE PROCEDURE sp_admin_close_trip(
    IN p_trip_id BIGINT UNSIGNED,
    IN p_actor_user_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_status VARCHAR(30);
    DECLARE v_effective_at DATETIME;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SET v_effective_at = CURRENT_TIMESTAMP;

    SELECT status
      INTO v_status
      FROM trip_instances
     WHERE id = p_trip_id
     FOR UPDATE;

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El viaje no existe';
    END IF;

    IF v_status IN ('COMPLETED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El viaje ya esta cerrado';
    END IF;

    UPDATE trip_instances
       SET status = 'COMPLETED',
           actual_end_at = COALESCE(actual_end_at, v_effective_at)
     WHERE id = p_trip_id;

    -- Reservas BOARDED que quedaron arriba (mismo criterio que 0017).
    INSERT INTO reservation_events (
        reservation_id, event_type, actor_user_id, event_at, details
    )
    SELECT r.id, 'ALIGHTED', p_actor_user_id, v_effective_at,
           'Cierre del viaje desde el panel de administracion'
      FROM reservations r
     WHERE r.trip_id = p_trip_id
       AND r.status = 'BOARDED';

    UPDATE trip_seat_segments
       SET state = 'USED'
     WHERE state = 'OCCUPIED'
       AND reservation_id IN (
           SELECT id FROM reservations
            WHERE trip_id = p_trip_id AND status = 'BOARDED'
       );

    UPDATE reservation_segments
       SET allocation_status = 'USED'
     WHERE allocation_status = 'OCCUPIED'
       AND reservation_id IN (
           SELECT id FROM reservations
            WHERE trip_id = p_trip_id AND status = 'BOARDED'
       );

    UPDATE reservations
       SET status = 'COMPLETED',
           completed_at = v_effective_at
     WHERE trip_id = p_trip_id
       AND status = 'BOARDED';

    -- Invitados (0018): mismo cierre.
    UPDATE trip_seat_segments
       SET state = 'USED'
     WHERE state = 'OCCUPIED'
       AND guest_occupant_id IN (
           SELECT id FROM guest_occupants
            WHERE trip_id = p_trip_id AND status = 'BOARDED'
       );

    UPDATE guest_occupants
       SET status = 'COMPLETED',
           completed_at = v_effective_at
     WHERE trip_id = p_trip_id
       AND status = 'BOARDED';

    COMMIT;
END$$

DELIMITER ;
