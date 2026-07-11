package jobs

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"time"
)

// noshowTickerInterval define cada cuanto corre el marcador automatico de
// NO_SHOW. 60 segundos es suficiente resolucion para un transporte
// corporativo con tolerancia en minutos: el pasajero ve liberado su asiento
// en menos de un minuto tras vencer.
const noshowTickerInterval = 60 * time.Second

// expiredReservation es el resultado del SELECT que busca reservas
// confirmadas cuya tolerancia ya vencio. driverID se conserva como actor del
// evento de auditoria (reservation_events.actor_user_id es NOT NULL).
type expiredReservation struct {
	ID                 int64
	OriginTripStopTime int64
	DriverID           int64
}

// RunNoShowChecker ejecuta UNA pasada del marcador automatico de NO_SHOW.
//
// Busca reservas CONFIRMED cuyo punto de subida ya fue arrival-marked por el
// conductor y cuya tolerancia (trip_instances.no_show_tolerance_minutes)
// vencio. Para cada una, libera los segmentos y la marca NO_SHOW en una
// transaccion Go (sql.Tx).
//
// Por que inline y no sp_mark_reservation_no_show: ese SP valida que
// p_driver_id sea el conductor asignado del viaje. El job automatico no tiene
// un conductor en contexto. Inlinear la logica en una tx Go es el UNICO caso
// donde se justifica: replica exactamente el cuerpo del SP dentro de BEGIN/COMMIT.
//
// actor_user_id del evento se rellena con el driver_id del viaje: el job
// automatiza lo que el conductor podria marcar manualmente, y
// reservation_events.actor_user_id es NOT NULL (no se puede usar NULL).
func RunNoShowChecker(ctx context.Context, db *sql.DB) error {
	rows, err := db.QueryContext(ctx, `
		SELECT r.id,
		       r.origin_trip_stop_time_id,
		       t.driver_id
		  FROM reservations r
		  JOIN trip_instances t  ON t.id = r.trip_id
		  JOIN trip_stop_times tst ON tst.id = r.origin_trip_stop_time_id
		 WHERE r.status = 'CONFIRMED'
		   AND tst.actual_arrival_at IS NOT NULL
		   AND DATE_ADD(tst.actual_arrival_at, INTERVAL t.no_show_tolerance_minutes MINUTE) < NOW()
	`)
	if err != nil {
		return fmt.Errorf("consultando reservas expiradas: %w", err)
	}

	var expired []expiredReservation
	for rows.Next() {
		var e expiredReservation
		if err := rows.Scan(&e.ID, &e.OriginTripStopTime, &e.DriverID); err != nil {
			rows.Close()
			return fmt.Errorf("escaneando reserva expirada: %w", err)
		}
		expired = append(expired, e)
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return fmt.Errorf("iterando reservas expiradas: %w", err)
	}
	rows.Close()

	if len(expired) == 0 {
		return nil
	}

	processed := 0
	failed := 0
	for _, e := range expired {
		if err := markNoShowTx(ctx, db, e); err != nil {
			failed++
			slog.Error("noshow: fallo marcando reserva",
				"reservation_id", e.ID, "error", err)
			continue
		}
		processed++
	}

	slog.Info("noshow: pasada finalizada",
		"expiradas", len(expired),
		"procesadas", processed,
		"fallidas", failed,
	)
	if failed > 0 {
		return fmt.Errorf("%d reservas no pudieron marcarse NO_SHOW", failed)
	}
	return nil
}

// markNoShowTx ejecuta la liberacion + marca NO_SHOW para una reserva dentro
// de una transaccion Go. Repasa el cuerpo de sp_mark_reservation_no_show sin
// la validacion del conductor (el job automatico no tiene conductor actor).
func markNoShowTx(ctx context.Context, db *sql.DB, e expiredReservation) error {
	tx, err := db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("iniciando tx: %w", err)
	}
	// En caso de panico o return temprano nos aseguramos de rollback.
	committed := false
	defer func() {
		if !committed {
			_ = tx.Rollback()
		}
	}()

	// 1. Liberar segmentos de asiento reservados.
	if _, err := tx.ExecContext(ctx, `
		UPDATE trip_seat_segments
		   SET state = 'AVAILABLE',
		       reservation_id = NULL,
		       released_at = NOW()
		 WHERE reservation_id = ?
		   AND state = 'RESERVED'
	`, e.ID); err != nil {
		return fmt.Errorf("liberando trip_seat_segments: %w", err)
	}

	// 2. Marcar segmentos de la reserva como RELEASED en el historial.
	if _, err := tx.ExecContext(ctx, `
		UPDATE reservation_segments
		   SET allocation_status = 'RELEASED',
		       released_at = NOW()
		 WHERE reservation_id = ?
		   AND allocation_status = 'RESERVED'
	`, e.ID); err != nil {
		return fmt.Errorf("liberando reservation_segments: %w", err)
	}

	// 3. Marcar la reserva como NO_SHOW con auditoria del conductor del viaje.
	if _, err := tx.ExecContext(ctx, `
		UPDATE reservations
		   SET status = 'NO_SHOW',
		       no_show_at = NOW(),
		       no_show_by_user_id = ?,
		       no_show_trip_stop_time_id = ?
		 WHERE id = ?
	`, e.DriverID, e.OriginTripStopTime, e.ID); err != nil {
		return fmt.Errorf("actualizando reserva a NO_SHOW: %w", err)
	}

	// 4. Evento NO_SHOW para la bitacora.
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO reservation_events (reservation_id, event_type, trip_stop_time_id, actor_user_id, event_at, details)
		VALUES (?, 'NO_SHOW', ?, ?, NOW(), 'No show automatico tras vencer tolerancia')
	`, e.ID, e.OriginTripStopTime, e.DriverID); err != nil {
		return fmt.Errorf("insertando evento NO_SHOW: %w", err)
	}

	// 5. Evento SEGMENTS_RELEASED para la bitacora.
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO reservation_events (reservation_id, event_type, trip_stop_time_id, actor_user_id, event_at, details)
		VALUES (?, 'SEGMENTS_RELEASED', ?, ?, NOW(), 'Liberacion automatica de segmentos')
	`, e.ID, e.OriginTripStopTime, e.DriverID); err != nil {
		return fmt.Errorf("insertando evento SEGMENTS_RELEASED: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("confirmando tx no_show: %w", err)
	}
	committed = true
	return nil
}

// StartNoShowChecker lanza la goroutine que ejecuta RunNoShowChecker en cada
// tick. Hace catch-up al arranque y luego un ticker cada
// noshowTickerInterval. Se detiene limpiamente cuando ctx se cancela.
func StartNoShowChecker(ctx context.Context, db *sql.DB) {
	slog.Info("noshow: iniciado", "intervalo", noshowTickerInterval.String())

	// Catch-up: marca los vencidos acumulados antes del primer tick.
	if err := RunNoShowChecker(ctx, db); err != nil {
		slog.Error("noshow: fallo en corrida inicial", "error", err)
	}

	ticker := time.NewTicker(noshowTickerInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			slog.Info("noshow: detenido")
			return
		case <-ticker.C:
			if err := RunNoShowChecker(ctx, db); err != nil {
				slog.Error("noshow: fallo en tick", "error", err)
			}
		}
	}
}
