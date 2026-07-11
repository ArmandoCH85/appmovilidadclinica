-- ============================================================================
-- 0002_cancel_sps.up.sql
-- Agrega cancelacion voluntaria de reservas y viajes al MVP.
-- Requisito previo: 0001_schema.up.sql aplicado.
--
-- Nota tecnica: sp_cancel_trip inlinea el loop de cancelacion en lugar de
-- hacer CALL sp_cancel_reservation porque MySQL no soporta transacciones
-- anidadas (START TRANSACTION dentro de START TRANSACTION confirma la
-- externa). Inlinear mantiene una sola transaccion atomica para el viaje
-- completo.
-- ============================================================================

-- Amplia el ENUM de estado de reservas para incluir 'CANCELLED'.
ALTER TABLE reservations
    MODIFY COLUMN status ENUM(
        'CONFIRMED', 'BOARDED', 'COMPLETED', 'NO_SHOW', 'CANCELLED'
    ) NOT NULL DEFAULT 'CONFIRMED';

-- Amplia el ENUM de eventos para registrar cancelaciones.
ALTER TABLE reservation_events
    MODIFY COLUMN event_type ENUM(
        'CONFIRMED', 'BOARDED', 'ALIGHTED', 'NO_SHOW',
        'SEGMENTS_RELEASED', 'CANCELLED'
    ) NOT NULL;

-- Limpia versiones previas para que la migracion sea idempotente.
DROP PROCEDURE IF EXISTS sp_cancel_reservation;
DROP PROCEDURE IF EXISTS sp_cancel_trip;

DELIMITER $$

-- Cancela una reserva activa liberando los segmentos ocupados.
-- Requisitos: la reserva debe existir y estar en CONFIRMED o BOARDED.
-- Transaccional: si algo falla, ROLLBACK via EXIT HANDLER.
CREATE PROCEDURE sp_cancel_reservation(
    IN p_reservation_id BIGINT UNSIGNED,
    IN p_actor_user_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_status VARCHAR(20);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT status INTO v_status
      FROM reservations
     WHERE id = p_reservation_id
     FOR UPDATE;

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La reserva no existe';
    END IF;

    IF v_status NOT IN ('CONFIRMED', 'BOARDED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo se pueden cancelar reservas activas (CONFIRMED o BOARDED)';
    END IF;

    -- Libera el inventario vivo: los segmentos vuelven a estar disponibles.
    UPDATE trip_seat_segments
       SET state = 'AVAILABLE',
           reservation_id = NULL,
           released_at = CURRENT_TIMESTAMP
     WHERE reservation_id = p_reservation_id
       AND state IN ('RESERVED', 'OCCUPIED');

    -- Marca el historial de segmentos como liberado (no se borra: es auditable).
    UPDATE reservation_segments
       SET allocation_status = 'RELEASED',
           released_at = CURRENT_TIMESTAMP
     WHERE reservation_id = p_reservation_id
       AND allocation_status IN ('RESERVED', 'OCCUPIED');

    UPDATE reservations
       SET status = 'CANCELLED'
     WHERE id = p_reservation_id;

    INSERT INTO reservation_events (
        reservation_id, event_type, actor_user_id, details
    ) VALUES (
        p_reservation_id, 'CANCELLED', p_actor_user_id, 'Reserva cancelada'
    );

    COMMIT;
END$$

-- Cancela un viaje completo liberando todas sus reservas activas.
-- Inlinea el loop de cancelacion (ver nota al inicio del archivo) para
-- mantener atomicidad en una sola transaccion.
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
