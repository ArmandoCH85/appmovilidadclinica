// Package booking implementa el modulo de reserva: confirmacion atomica de
// asiento, cancelacion y verificacion del token QR del pasajero.
//
// El repositorio llama a sp_confirm_reservation (que devuelve qr_token RAW
// en el result set) y sp_cancel_reservation. El servicio aplica la regla
// 1-reserva-por-viaje-por-trabajador (SELECT EXISTS) antes de confirmar y
// genera el booking_group_uuid con crypto/rand (sin dependencia externa).
package booking

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/dberr"
)

// ConfirmParams agrupa los parametros que el servicio pasa al repositorio
// para llamar a sp_confirm_reservation.
type ConfirmParams struct {
	TripID                    int64
	WorkerID                  int64
	TripSeatID                int64
	OriginTripStopTimeID      int64
	DestinationTripStopTimeID int64
	BookingGroupUUID          string
}

// ConfirmResult refleja el result set final de sp_confirm_reservation:
// id, reservation_code, qr_token (RAW, no hash), status, orden de subida/bajada.
type ConfirmResult struct {
	ReservationID        int64  `json:"reservation_id"`
	ReservationCode      string `json:"reservation_code"`
	QRToken              string `json:"qr_token"`
	Status               string `json:"status"`
	OriginStopOrder      int    `json:"origin_stop_order"`
	DestinationStopOrder int    `json:"destination_stop_order"`
}

// Reservation es la fila de reservations usada por VerifyQR.
type Reservation struct {
	ID                        int64     `json:"id"`
	ReservationCode           string    `json:"reservation_code"`
	TripID                    int64     `json:"trip_id"`
	WorkerID                  int64     `json:"worker_id"`
	TripSeatID                int64     `json:"trip_seat_id"`
	OriginTripStopTimeID      int64     `json:"origin_trip_stop_time_id"`
	DestinationTripStopTimeID int64     `json:"destination_trip_stop_time_id"`
	Status                    string    `json:"status"`
	ConfirmedAt               time.Time `json:"confirmed_at"`
}

// BookingRepository abstrae el acceso a BD del modulo.
type BookingRepository interface {
	CheckActiveReservation(ctx context.Context, workerID, tripID int64) (bool, error)
	ConfirmReservation(ctx context.Context, params ConfirmParams) (ConfirmResult, error)
	CancelReservation(ctx context.Context, reservationID, actorUserID int64) error
	VerifyQRToken(ctx context.Context, tokenHash string) (Reservation, error)
}

// bookingRepository es la implementacion concreta con database/sql.
type bookingRepository struct {
	db *sql.DB
}

// NewRepository construye el repositorio.
func NewRepository(db *sql.DB) BookingRepository {
	return &bookingRepository{db: db}
}

// CheckActiveReservation devuelve true si el trabajador ya tiene una reserva
// activa (CONFIRMED o BOARDED) en el viaje. Es la regla 1-por-viaje que Go
// valida ANTES de llamar sp_confirm_reservation (el SP no la verifica).
func (r *bookingRepository) CheckActiveReservation(ctx context.Context, workerID, tripID int64) (bool, error) {
	const q = `
        SELECT EXISTS(
            SELECT 1 FROM reservations
             WHERE trip_id = ? AND worker_id = ?
               AND status IN ('CONFIRMED', 'BOARDED')
        )`
	var exists bool
	if err := r.db.QueryRowContext(ctx, q, tripID, workerID).Scan(&exists); err != nil {
		return false, fmt.Errorf("verificando reserva activa: %w", err)
	}
	return exists, nil
}

// ConfirmReservation llama a sp_confirm_reservation y escanea el result set
// final que incluye qr_token en crudo (linea 1758 del schema: v_qr_token AS
// qr_token). El token crudo se entrega al movil; el conductor lo escanea y
// Go lo hashea para comparar con qr_token_hash en VerifyQRToken.
func (r *bookingRepository) ConfirmReservation(ctx context.Context, params ConfirmParams) (ConfirmResult, error) {
	var res ConfirmResult
	err := r.db.QueryRowContext(ctx, "CALL sp_confirm_reservation(?, ?, ?, ?, ?, ?)",
		params.TripID, params.WorkerID, params.TripSeatID,
		params.OriginTripStopTimeID, params.DestinationTripStopTimeID,
		params.BookingGroupUUID,
	).Scan(
		&res.ReservationID, &res.ReservationCode, &res.QRToken,
		&res.Status, &res.OriginStopOrder, &res.DestinationStopOrder,
	)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return ConfirmResult{}, spErr
		}
		return ConfirmResult{}, fmt.Errorf("llamando sp_confirm_reservation: %w", err)
	}
	return res, nil
}

// CancelReservation llama a sp_cancel_reservation. El SP es transaccional y
// libera los segmentos; aqui solo se traducen los errores del driver.
func (r *bookingRepository) CancelReservation(ctx context.Context, reservationID, actorUserID int64) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_cancel_reservation(?, ?)", reservationID, actorUserID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_cancel_reservation: %w", err)
	}
	return nil
}

// VerifyQRToken busca una reserva por el hash de su token QR. El servicio ya
// hasheo el token crudo con SHA-256 + hex antes de llamar aqui; el campo
// qr_token_hash en la BD es CHAR(64) (hex de 32 bytes).
func (r *bookingRepository) VerifyQRToken(ctx context.Context, tokenHash string) (Reservation, error) {
	const q = `
        SELECT id, reservation_code, trip_id, worker_id, trip_seat_id,
               origin_trip_stop_time_id, destination_trip_stop_time_id,
               status, confirmed_at
          FROM reservations
         WHERE qr_token_hash = ?`

	var res Reservation
	err := r.db.QueryRowContext(ctx, q, tokenHash).Scan(
		&res.ID, &res.ReservationCode, &res.TripID, &res.WorkerID,
		&res.TripSeatID, &res.OriginTripStopTimeID,
		&res.DestinationTripStopTimeID, &res.Status, &res.ConfirmedAt,
	)
	if err != nil {
		if nfErr := dberr.NotFound(err, "reserva", "qr"); nfErr != err {
			return Reservation{}, nfErr
		}
		return Reservation{}, fmt.Errorf("buscando reserva por qr: %w", err)
	}
	return res, nil
}

// compile-time guard.
var _ BookingRepository = (*bookingRepository)(nil)
