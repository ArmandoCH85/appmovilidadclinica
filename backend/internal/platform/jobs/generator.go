// Package jobs contiene los procesos en segundo plano del backend: el
// generador de instancias de viaje y el marcador automatico de NO_SHOW.
// Ambos corren en goroutines independientes arrancados por main.go y se
// detienen limpiamente via context cancellation en el graceful shutdown.
package jobs

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"time"
)

// generatorWindowDays es la ventana futura que materializa cada corrida del
// generador (30 dias). No se parametriza por config en el MVP: 30 dias cubre
// la reserva anticipada maxima (booking_open_days_before default 14) y
// mantiene acotado el volumen de filas por corrida.
const generatorWindowDays = 30

// generatorTickerHours define cada cuanto corre el generador. 6 horas
// equilibja frescura y coste: las plantillas generan con booking_opens_at
// en el futuro, asi que un retraso de horas noimpacta la disponibilidad.
const generatorTickerHours = 6

// RunTripGenerator ejecuta UNA corrida del generador de instancias de viaje.
//
// Flujo:
//  1. Crea un trip_generation_runs en estado RUNNING con ventana
//     [today, today+generatorWindowDays].
//  2. Lista las plantillas activas (active=1) con su calendar_id.
//  3. Por cada plantilla y cada fecha de la ventana, consulta
//     fn_service_operates(calendar_id, date). Si opera, llama a
//     sp_generate_trip_instance(template_id, date, run_id).
//  4. Al final, marca el run COMPLETED (con errores si los hubo).
//
// Los fallos por template/fecha se contabilizan en failed_count del run y se
// loguean, pero no abortan la corrida: el proposito del job es ser resiliente
// ante una plantilla mala sin dejar de lado el resto.
func RunTripGenerator(ctx context.Context, db *sql.DB) error {
	now := time.Now()
	windowStart := now.Format("2006-01-02")
	windowEnd := now.AddDate(0, 0, generatorWindowDays).Format("2006-01-02")

	res, err := db.ExecContext(ctx, `
		INSERT INTO trip_generation_runs (window_start, window_end, status, triggered_by_user_id)
		VALUES (?, ?, 'RUNNING', NULL)
	`, windowStart, windowEnd)
	if err != nil {
		return fmt.Errorf("creando trip_generation_run: %w", err)
	}
	runID, err := res.LastInsertId()
	if err != nil {
		return fmt.Errorf("obteniendo last_insert_id del run: %w", err)
	}

	rows, err := db.QueryContext(ctx, `
		SELECT id, route_id, service_calendar_id
		  FROM trip_templates
		 WHERE active = 1
	`)
	if err != nil {
		return fmt.Errorf("listando trip_templates activas: %w", err)
	}
	defer rows.Close()

	type template struct {
		id         int64
		routeID    int64
		calendarID int64
	}
	var templates []template
	for rows.Next() {
		var t template
		if err := rows.Scan(&t.id, &t.routeID, &t.calendarID); err != nil {
			return fmt.Errorf("escaneando trip_template: %w", err)
		}
		templates = append(templates, t)
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("iterando trip_templates: %w", err)
	}

	failedCount := 0
	generatedCount := 0
	skippedCount := 0

	for _, t := range templates {
		for i := 0; i < generatorWindowDays; i++ {
			date := now.AddDate(0, 0, i).Format("2006-01-02")

			var operatesInt int
			err := db.QueryRowContext(ctx, `SELECT fn_service_operates(?, ?)`, t.calendarID, date).Scan(&operatesInt)
			if err != nil {
				failedCount++
				slog.Error("generador: fn_service_operates fallo",
					"template_id", t.id, "calendar_id", t.calendarID, "fecha", date, "error", err)
				continue
			}
			if operatesInt == 0 {
				skippedCount++
				continue
			}

			if _, err := db.ExecContext(ctx, `CALL sp_generate_trip_instance(?, ?, ?)`, t.id, date, runID); err != nil {
				failedCount++
				slog.Error("generador: sp_generate_trip_instance fallo",
					"template_id", t.id, "fecha", date, "run_id", runID, "error", err)
				continue
			}
			generatedCount++
		}
	}

	finalStatus := "COMPLETED"
	if failedCount > 0 && generatedCount == 0 {
		finalStatus = "FAILED"
	} else if failedCount > 0 {
		finalStatus = "COMPLETED_WITH_ERRORS"
	}

	if _, err := db.ExecContext(ctx, `
		UPDATE trip_generation_runs
		   SET status = ?,
		       generated_count = ?,
		       skipped_count = ?,
		       failed_count = ?,
		       finished_at = NOW()
		 WHERE id = ?
	`, finalStatus, generatedCount, skippedCount, failedCount, runID); err != nil {
		return fmt.Errorf("actualizando trip_generation_run %d: %w", runID, err)
	}

	slog.Info("generador: corrida finalizada",
		"run_id", runID,
		"ventana", windowStart+".."+windowEnd,
		"generadas", generatedCount,
		"omitidas", skippedCount,
		"fallidas", failedCount,
		"estado", finalStatus,
	)
	return nil
}

// StartTripGenerator lanza la goroutine que ejecuta RunTripGenerator en cada
// tick. Hace catch-up al arranque (corrida inmediata) y luego un ticker cada
// generatorTickerHours. Se detiene limpiamente cuando ctx se cancela.
func StartTripGenerator(ctx context.Context, db *sql.DB) {
	slog.Info("generador: iniciado", "intervalo_horas", generatorTickerHours)

	// Catch-up: genera la ventana actual apenas arranca el proceso.
	if err := RunTripGenerator(ctx, db); err != nil {
		slog.Error("generador: fallo en corrida inicial", "error", err)
	}

	ticker := time.NewTicker(generatorTickerHours * time.Hour)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			slog.Info("generador: detenido")
			return
		case <-ticker.C:
			if err := RunTripGenerator(ctx, db); err != nil {
				slog.Error("generador: fallo en tick", "error", err)
			}
		}
	}
}
