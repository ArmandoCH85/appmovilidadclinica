-- ============================================================================
-- 0005_user_reservation_activity.up.sql
-- Vista SQL de solo lectura para el reporte de "actividad de reservas por
-- usuario" (#28). Una fila por usuario (solo WORKER y DRIVER; los ADMIN no
-- se incluyen porque no operan reservas en el flujo normal).
--
-- Cada columna refleja la cuenta DISTINCT de reservas del usuario bajo
-- una condicion especifica:
--
--   total_reservations    : todas las reservas donde worker_id = user.id
--   confirmed_by_self     : reservas cuyo evento CONFIRMED fue disparado
--                           por el propio usuario (booking inicial propio)
--   confirmed_by_driver   : reservas cuyo evento BOARDED fue disparado por
--                           el conductor del viaje (actor_user_id = driver)
--   cancelled_by_self     : reservas cuyo evento CANCELLED fue disparado
--                           por el propio usuario (cancelacion voluntaria)
--   not_confirmed         : reservas en estado CONFIRMED cuyo viaje aun no
--                           ocurrio (service_date >= hoy). Son las que
--                           quedaron pendientes sin boarding/no-show/cancel
--
-- El reporte NO distingue entre WORKER y DRIVER en la query (los muestra
-- juntos); el filtro por rol se aplica desde la capa Go para no acoplar
-- la vista a una sola dimension.
--
-- No usa DELIMITER: es CREATE VIEW, no stored procedure. El parser de
-- migraciones (internal/platform/database/migrate.go) corta por ";" cuando
-- no hay DELIMITER activo.
-- ============================================================================

CREATE OR REPLACE VIEW vw_user_reservation_activity AS
SELECT u.id                              AS user_id,
       u.employee_code                   AS employee_code,
       u.document_number                 AS document_number,
       u.full_name                       AS full_name,
       u.role                            AS role,
       u.department                      AS department,
       u.active                          AS active,

       -- Total: cualquier reserva del usuario (estado actual no importa)
       COUNT(DISTINCT r.id)              AS total_reservations,

       -- Confirmadas por si mismo: CONFIRMED event con actor = el usuario.
       -- En el flujo normal cada reserva genera UN evento CONFIRMED al
       -- crearse, y el actor es el created_by_user_id (que suele coincidir
       -- con worker_id cuando el trabajador reserva para si).
       COUNT(DISTINCT CASE
           WHEN re.event_type = 'CONFIRMED'
            AND re.actor_user_id = u.id
           THEN r.id
       END)                              AS confirmed_by_self,

       -- Confirmadas por el conductor: BOARDED event donde el actor es el
       -- driver asignado al viaje. Como reservation_events.actor_user_id
       -- es NOT NULL, joineamos contra users para filtrar por rol DRIVER
       -- y contra trip_instances.driver_id para confirmar que el actor
       -- coincide con el conductor del viaje (evita falsos positivos si
       -- un admin marco el boarding manualmente).
       COUNT(DISTINCT CASE
           WHEN re.event_type = 'BOARDED'
            AND actor_u.role = 'DRIVER'
            AND re.actor_user_id = trip.driver_id
           THEN r.id
       END)                              AS confirmed_by_driver,

       -- Canceladas por si mismo: CANCELLED event con actor = el usuario.
       -- Las cancelaciones de viaje completo (sp_cancel_trip) tambien
       -- generan CANCELLED events por reserva; quedan contadas aqui si
       -- el admin que ejecuto la cancelacion resulto ser el mismo user,
       -- caso raro. Cancelaciones masivas de admin NO se cuentan porque
       -- el actor_user_id seria el admin.
       COUNT(DISTINCT CASE
           WHEN re.event_type = 'CANCELLED'
            AND re.actor_user_id = u.id
           THEN r.id
       END)                              AS cancelled_by_self,

       -- No confirmadas: reservas en estado CONFIRMED cuyo viaje todavia
       -- no ocurrio (CURDATE() evita contar reservas viejas que ya
       -- pasaron y simplemente no se llegaron a abordar; esas pasaron a
       -- NO_SHOW por el job automatico). El JOIN a trip_instances es
       -- necesario para poder filtrar por service_date; sin el, esta
       -- columna no se puede calcular.
       COUNT(DISTINCT CASE
           WHEN r.status = 'CONFIRMED'
            AND trip.service_date >= CURDATE()
           THEN r.id
       END)                              AS not_confirmed

  FROM users u
  LEFT JOIN reservations r
    ON r.worker_id = u.id
  LEFT JOIN reservation_events re
    ON re.reservation_id = r.id
  LEFT JOIN trip_instances trip
    ON trip.id = r.trip_id
  LEFT JOIN users actor_u
    ON actor_u.id = re.actor_user_id
 WHERE u.role IN ('WORKER', 'DRIVER')
 GROUP BY u.id, u.employee_code, u.document_number, u.full_name, u.role,
          u.department, u.active;