-- ============================================================================
-- cleanup_out_of_window_marks.sql
-- Limpieza puntual: borra marcas de llegada/salida IMPOSIBLES, es decir,
-- registradas en una fecha ajena al dia de servicio del viaje.
--
-- Sintoma que lo motivó: el reporte "Llegadas por sede/paradero" mostraba
-- desvíos absurdos (p.ej. -3033 min) porque un viaje del 28/09 tenia
-- "llegada real" del 20/09.
--
-- Regla de limpieza (dos condiciones, se limpia si cumple CUALQUIERA):
--   1. El viaje NUNCA se inició: status IN ('DRAFT','PUBLISHED'). Un viaje que
--      no arrancó no puede tener llegadas reales. Esta es la regla principal.
--   2. DATE(actual_arrival_at) fuera de [service_date, service_date + 1 dia]:
--      marca registrada en otra fecha de servicio (la ventana +1 dia cubre los
--      viajes nocturnos que cruzan medianoche).
-- No toca viajes IN_PROGRESS/COMPLETED/CANCELLED (esos sí pudieron operarse).
--
-- Que hace: pone las paradas afectadas como PENDING y limpia sus marcas.
-- Que NO hace: revertir reservas/eventos que el SP pudo haber auto-cerrado a
-- partir de esas marcas (esa reversión es una decisión de negocio aparte).
--
-- Idempotente: correrlo dos veces no cambia nada la segunda vez.
-- Ejecutar con: mysql <db> < cleanup_out_of_window_marks.sql
-- ============================================================================

-- 1) PREVIEW: cuantas filas se van a limpiar.
SELECT COUNT(*) AS filas_a_limpiar
  FROM trip_stop_times tst
  JOIN trip_instances t ON t.id = tst.trip_id
 WHERE tst.actual_arrival_at IS NOT NULL
   AND (t.status IN ('DRAFT','PUBLISHED')
        OR DATE(tst.actual_arrival_at) NOT BETWEEN t.service_date
                                               AND DATE_ADD(t.service_date, INTERVAL 1 DAY));

-- 2) LIMPIEZA en transaccion.
START TRANSACTION;

UPDATE trip_stop_times tst
  JOIN trip_instances t ON t.id = tst.trip_id
   SET tst.actual_arrival_at = NULL,
       tst.actual_departure_at = NULL,
       tst.arrival_marked_by_user_id = NULL,
       tst.status = 'PENDING'
 WHERE tst.actual_arrival_at IS NOT NULL
   AND (t.status IN ('DRAFT','PUBLISHED')
        OR DATE(tst.actual_arrival_at) NOT BETWEEN t.service_date
                                               AND DATE_ADD(t.service_date, INTERVAL 1 DAY));

COMMIT;

-- 3) VERIFICACION: debe dar 0.
SELECT COUNT(*) AS quedan_fuera_de_ventana
  FROM trip_stop_times tst
  JOIN trip_instances t ON t.id = tst.trip_id
 WHERE tst.actual_arrival_at IS NOT NULL
   AND (t.status IN ('DRAFT','PUBLISHED')
        OR DATE(tst.actual_arrival_at) NOT BETWEEN t.service_date
                                               AND DATE_ADD(t.service_date, INTERVAL 1 DAY));
