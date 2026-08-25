-- ============================================================================
-- 0007_user_reservation_status_breakdown.down.sql
-- Revierte al schema de vw_user_reservation_activity previo a 0007
-- (sin completadas / pendientes / canceladas / no_show, con not_confirmed).
--
-- ATENCION: este down deja la vista con las columnas originales, pero el codigo
-- Go (struct UserReservationActivity) ya esta migrado al nuevo esquema. Si se
-- ejecuta este down sin reverter el codigo, el backend fallara al escanear
-- porque no encontrara las columnas nuevas en la query.
--
-- Para un rollback completo: revertir tambien el commit que toca
-- repository.go (struct + SELECT).
-- ============================================================================

DROP VIEW IF EXISTS vw_user_reservation_activity;

CREATE OR REPLACE VIEW vw_user_reservation_activity AS
SELECT u.id                              AS user_id,
       u.employee_code                   AS employee_code,
       u.document_number                 AS document_number,
       u.full_name                       AS full_name,
       u.role                            AS role,
       u.department                      AS department,
       u.active                          AS active,

       COUNT(DISTINCT r.id)              AS total_reservations,

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
       COUNT(DISTINCT CASE WHEN r.status = 'CONFIRMED'
                           AND trip.service_date >= CURDATE()
                          THEN r.id END) AS not_confirmed,

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