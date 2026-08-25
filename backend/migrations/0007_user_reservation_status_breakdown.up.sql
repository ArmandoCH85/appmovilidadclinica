-- ============================================================================
-- 0007_user_reservation_status_breakdown.up.sql
-- Amplia vw_user_reservation_activity con 4 columnas de ESTADO FINAL de
-- cada reserva: completadas, pendientes, canceladas, no_show.
--
-- Estas son MUTUAMENTE EXCLUYENTES (cada reserva termina en exactamente un
-- status) y SUMAN al total — resuelve la confusion que generaba el reporte
-- anterior donde las columnas eran EVENTOS que se solapaban y no cerraban
-- la cuenta.
--
-- Decision de diseno: Opcion C (sub-secciones en la UI).
--   Seccion 1 "Estado final": muestra el lifecycle de las reservas
--     (Completadas / Pendientes / Canceladas / No show).
--   Seccion 2 "Acciones": muestra quien hizo que evento (el admin
--     quiere ver tambien "quien bookeo / quien marco embarque" para
--     detectar patrones).
--
-- Cambios especificos vs la version anterior (0006):
--   + completadas   (status='COMPLETED')
--   + pendientes     (status='CONFIRMED' AND trip.service_date >= CURDATE())
--   + canceladas     (status='CANCELLED')
--   + no_show        (status='NO_SHOW')
--   - not_confirmed  (era sinonimo exacto de "pendientes"; eliminado por ruido)
-- ============================================================================

CREATE OR REPLACE VIEW vw_user_reservation_activity AS
SELECT u.id                              AS user_id,
       u.employee_code                   AS employee_code,
       u.document_number                 AS document_number,
       u.full_name                       AS full_name,
       u.role                            AS role,
       u.department                      AS department,
       u.active                          AS active,

       -- Total: cada reserva cuenta 1 vez (independiente de su estado)
       COUNT(DISTINCT r.id)              AS total_reservations,

       -- === ESTADO FINAL (mutuamente excluyentes, suman al total) ===
       COUNT(DISTINCT CASE WHEN r.status = 'COMPLETED'
                          THEN r.id END) AS completadas,
       COUNT(DISTINCT CASE WHEN r.status = 'CONFIRMED'
                           AND trip.service_date >= CURDATE()
                          THEN r.id END) AS pendientes,
       COUNT(DISTINCT CASE WHEN r.status = 'CANCELLED'
                          THEN r.id END) AS canceladas,
       COUNT(DISTINCT CASE WHEN r.status = 'NO_SHOW'
                          THEN r.id END) AS no_show,

       -- === ACCIONES (pueden solaparse: una reserva tiene varios eventos) ===
       COUNT(DISTINCT CASE WHEN re.event_type = 'CONFIRMED'
                           AND re.actor_user_id = u.id
                          THEN r.id END) AS confirmed_by_self,
       COUNT(DISTINCT CASE WHEN re.event_type = 'BOARDED'
                           AND actor_u.role = 'DRIVER'
                           AND re.actor_user_id = trip.driver_id
                          THEN r.id END) AS confirmed_by_driver,
       COUNT(DISTINCT CASE WHEN re.event_type = 'CANCELLED'
                           AND re.actor_user_id = u.id
                          THEN r.id END) AS cancelled_by_self,

       -- Timestamp del evento mas reciente del usuario en sus reservas
       MAX(re.event_at)                  AS last_activity_at

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