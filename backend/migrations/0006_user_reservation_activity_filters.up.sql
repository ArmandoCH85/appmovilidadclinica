-- ============================================================================
-- 0006_user_reservation_activity_filters.up.sql
-- Amplia la vista vw_user_reservation_activity (migration 0005) agregando
-- la columna last_activity_at: timestamp del evento MAS RECIENTE del usuario
-- sobre cualquiera de sus reservas. Es null si nunca tuvo actividad.
--
-- Por que aca y no en 0005: la vista original se pusheo sin esta columna
-- porque el reporte #28 se pidio sin filtro de fechas. Ahora que el admin
-- pide (a) poder acotar por rango de fechas y (b) ver "cuando fue la
-- ultima vez que movio algo", necesitamos el timestamp.
--
-- El filtro de fechas por service_date NO se aplica en la vista (no se
-- pueden parametrizar vistas en MySQL/MariaDB); vive en la query del
-- repositorio Go (GetUserReservationActivity acepta date_from/date_to
-- y filtra antes de agregar).
-- ============================================================================

CREATE OR REPLACE VIEW vw_user_reservation_activity AS
SELECT u.id                              AS user_id,
       u.employee_code                   AS employee_code,
       u.document_number                 AS document_number,
       u.full_name                       AS full_name,
       u.role                            AS role,
       u.department                      AS department,
       u.active                          AS active,

       COUNT(DISTINCT r.id)              AS total_reservations,
       COUNT(DISTINCT CASE
           WHEN re.event_type = 'CONFIRMED'
            AND re.actor_user_id = u.id
           THEN r.id
       END)                              AS confirmed_by_self,
       COUNT(DISTINCT CASE
           WHEN re.event_type = 'BOARDED'
            AND actor_u.role = 'DRIVER'
            AND re.actor_user_id = trip.driver_id
           THEN r.id
       END)                              AS confirmed_by_driver,
       COUNT(DISTINCT CASE
           WHEN re.event_type = 'CANCELLED'
            AND re.actor_user_id = u.id
           THEN r.id
       END)                              AS cancelled_by_self,
       COUNT(DISTINCT CASE
           WHEN r.status = 'CONFIRMED'
            AND trip.service_date >= CURDATE()
           THEN r.id
       END)                              AS not_confirmed,

       -- NUEVO: timestamp del evento mas reciente del usuario en sus reservas.
       -- Usamos MAX(re.event_at) porque los eventos son append-only y el
       -- maximo es la accion mas reciente. Queda NULL si el usuario nunca
       -- tuvo actividad (= ninguna reserva).
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