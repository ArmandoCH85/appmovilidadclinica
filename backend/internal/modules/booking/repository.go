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

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
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

// ReservationIdentity es lo mínimo de una reserva para validar ownership y
// estado en ReportPassengerIncident.
type ReservationIdentity struct {
	WorkerID int64
	TripID   int64
	Status   string
}

// JourneyState es el read-model de GET /reservations/{id}/journey (polling).
type JourneyState struct {
	ReservationID         int64           `json:"reservation_id"`
	ReservationStatus     string          `json:"reservation_status"`
	TripID                int64           `json:"trip_id"`
	TripStatus            string          `json:"trip_status"`
	LastDepartedStopOrder *int            `json:"last_departed_stop_order"`
	DestinationStopOrder  int             `json:"destination_stop_order"`
	Stops                 []JourneyStop   `json:"stops"`
	CanExtend             bool            `json:"can_extend"`
	Extension             *ExtensionOffer `json:"extension,omitempty"`
}

// JourneyStop es una parada del cronograma con su estado real.
type JourneyStop struct {
	TripStopTimeID       int64      `json:"trip_stop_time_id"`
	StopID               int64      `json:"stop_id"`
	StopName             string     `json:"stop_name"`
	StopOrder            int        `json:"stop_order"`
	ScheduledArrivalAt   time.Time  `json:"scheduled_arrival_at"`
	ScheduledDepartureAt time.Time  `json:"scheduled_departure_at"`
	Status               string     `json:"status"`
	ActualArrivalAt      *time.Time `json:"actual_arrival_at,omitempty"`
	ActualDepartureAt    *time.Time `json:"actual_departure_at,omitempty"`
}

// ExtensionOffer describe la oferta de extensión cuando can_extend = true.
type ExtensionOffer struct {
	CurrentSeatFree bool            `json:"current_seat_free"`
	RemainingStops  []ExtensionStop `json:"remaining_stops"`
}

// ExtensionStop es un paradero al que el pasajero puede extender.
type ExtensionStop struct {
	TripStopTimeID int64  `json:"trip_stop_time_id"`
	StopName       string `json:"stop_name"`
	StopOrder      int    `json:"stop_order"`
}

// SelfCheckinResult es el resultado de sp_mark_reservation_boarded_self.
// Solo expone lo que la app necesita para actualizar la UI (status +
// boarded_at), sin filtrar el id interno.
type SelfCheckinResult struct {
	ReservationID int64     `json:"reservation_id"`
	Status        string    `json:"status"`
	BoardedAt     time.Time `json:"boarded_at"`
}

// ExtendParams agrupa los campos para sp_extend_reservation.
// TripSeatID = 0 significa "usar el asiento actual".
type ExtendParams struct {
	ReservationID                int64
	WorkerID                     int64
	NewDestinationTripStopTimeID int64
	TripSeatID                   int64
}

// ExtendResult es el result set de sp_extend_reservation.
type ExtendResult struct {
	ReservationID        int64  `json:"reservation_id"`
	DestinationStopOrder int    `json:"destination_stop_order"`
	TripSeatID           int64  `json:"trip_seat_id"`
	SeatLabel            string `json:"seat_label"`
	Status               string `json:"status"`
}

// BookingRepository abstrae el acceso a BD del modulo.
type BookingRepository interface {
	CheckActiveReservation(ctx context.Context, workerID, tripID int64) (bool, error)
	ConfirmReservation(ctx context.Context, params ConfirmParams) (ConfirmResult, error)
	CancelReservation(ctx context.Context, reservationID, actorUserID int64) error
	VerifyQRToken(ctx context.Context, tokenHash string) (Reservation, error)
	// SelfCheckin invoca sp_mark_reservation_boarded_self. El SP valida
	// ownership (worker_id == reservation.worker_id), status=CONFIRMED y
	// ventana de tiempo alrededor de la salida. Aqui solo se traduce el
	// SIGNAL '45000' a ConflictError via dberr.TranslateSP.
	SelfCheckin(ctx context.Context, reservationID, workerID int64) (SelfCheckinResult, error)
	// ListReservationsByWorker devuelve todas las reservas del worker, sin
	// filtrar por status (la UI distingue CONFIRMED / BOARDED / COMPLETED /
	// NO_SHOW / CANCELLED). JOIN con trip_instances para incluir el
	// contexto minimo del viaje (trip_code + service_date) que la UI
	// necesita para mostrar el resumen.
	ListReservationsByWorker(ctx context.Context, workerID int64) ([]ReservationListItem, error)
	// GetReservationIdentity devuelve worker_id, trip_id y status de una
	// reserva. Usado por ReportPassengerIncident para validar ownership y
	// estado activo sin cargar toda la fila.
	GetReservationIdentity(ctx context.Context, reservationID int64) (ReservationIdentity, error)
	// InsertIncident inserta una incidencia de pasajero en trip_incidents y
	// devuelve su id. El trip_id ya viene resuelto por el servicio desde la
	// reserva (el cliente nunca lo manda).
	InsertIncident(ctx context.Context, tripID, reporterUserID int64, incidentType, description string) (int64, error)
	GetJourneyState(ctx context.Context, reservationID, workerID int64) (JourneyState, error)
	ExtendReservation(ctx context.Context, params ExtendParams) (ExtendResult, error)
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

// SelfCheckin llama a sp_mark_reservation_boarded_self (variante self del
// sp_mark_reservation_boarded: no exige driver_id coincidente, pero valida
// ownership real worker_id y ventana de tiempo). El SP actualiza el status a
// BOARDED, marca boarded_at = CURRENT_TIMESTAMP y deja reservation_events
// con event_type='BOARDED' y actor_user_id=worker_id para auditoria.
func (r *bookingRepository) SelfCheckin(ctx context.Context, reservationID, workerID int64) (SelfCheckinResult, error) {
	var res SelfCheckinResult
	err := r.db.QueryRowContext(ctx, "CALL sp_mark_reservation_boarded_self(?, ?)",
		reservationID, workerID).Scan(&res.ReservationID, &res.Status, &res.BoardedAt)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return SelfCheckinResult{}, spErr
		}
		return SelfCheckinResult{}, fmt.Errorf("llamando sp_mark_reservation_boarded_self: %w", err)
	}
	return res, nil
}

// ReservationListItem es la fila enriquecida de ListReservationsByWorker.
// Incluye el contexto del viaje (trip_code, scheduled_start_at), el nombre
// de las paradas de origen/destino y el seat_label — todo lo que la UI
// de "Mis reservas" necesita sin pedir un segundo roundtrip al detalle.
type ReservationListItem struct {
	ID                        int64     `json:"id"`
	ReservationCode           string    `json:"reservation_code"`
	TripID                    int64     `json:"trip_id"`
	TripSeatID                int64     `json:"trip_seat_id"`
	OriginTripStopTimeID      int64     `json:"origin_trip_stop_time_id"`
	DestinationTripStopTimeID int64     `json:"destination_trip_stop_time_id"`
	Status                    string    `json:"status"`
	ConfirmedAt               time.Time `json:"confirmed_at"`
	TripCode                  string    `json:"trip_code"`
	ScheduledStartAt          time.Time `json:"scheduled_start_at"`
	// OriginDepartureAt es la salida programada de la PARADA DE ORIGEN de la
	// reserva (origin_tst.scheduled_departure_at), no el inicio del viaje.
	// La ventana de self-checkin (±30 min) se calcula sobre este valor, asi
	// que para paradas que no son la primera scheduled_start_at no sirve.
	OriginDepartureAt time.Time `json:"origin_departure_at"`
	OriginName        string    `json:"origin_name"`
	DestinationName   string    `json:"destination_name"`
	SeatLabel         string    `json:"seat_label"`
	VehicleCode       string    `json:"vehicle_code"`
	Plate             string    `json:"plate"`
}

// ListReservationsByWorker devuelve todas las reservas (cualquier status) del
// worker con el contexto del viaje (nombre de ruta, paradas, asiento) ya
// joined, para que la UI de "Mis reservas" muestre info legible sin un
// segundo roundtrip al detalle.
//
// IMPORTANTE: NO devolvemos qr_token_hash ni nada que permita regenerar el
// QR — el qr_token crudo solo se entrega una vez al confirmar, y
// qr_token_hash es inutil sin el crudo. Las reservas sincronizadas desde
// este endpoint aparecern en la app sin QR visible; el usuario debera
// cancelar y reconfirmar si quiere ver el QR.
func (r *bookingRepository) ListReservationsByWorker(ctx context.Context, workerID int64) ([]ReservationListItem, error) {
	const q = `
        SELECT r.id, r.reservation_code, r.trip_id, r.trip_seat_id,
               r.origin_trip_stop_time_id, r.destination_trip_stop_time_id,
               r.status, r.confirmed_at,
               t.trip_code, t.scheduled_start_at,
               origin_tst.scheduled_departure_at,
               origin_stop.name, dest_stop.name,
               ts.seat_label,
               v.internal_code, v.plate
          FROM reservations r
          JOIN trip_instances t            ON t.id = r.trip_id
          JOIN trip_stop_times origin_tst  ON origin_tst.id = r.origin_trip_stop_time_id
          JOIN transport_stops origin_stop ON origin_stop.id = origin_tst.stop_id
          JOIN trip_stop_times dest_tst    ON dest_tst.id = r.destination_trip_stop_time_id
          JOIN transport_stops dest_stop   ON dest_stop.id = dest_tst.stop_id
          JOIN trip_seats ts               ON ts.id = r.trip_seat_id
          JOIN vehicles v                  ON v.id = t.vehicle_id
         WHERE r.worker_id = ?
         ORDER BY r.confirmed_at DESC`
	rows, err := r.db.QueryContext(ctx, q, workerID)
	if err != nil {
		return nil, fmt.Errorf("listando reservas del worker: %w", err)
	}
	defer rows.Close()

	items := make([]ReservationListItem, 0)
	for rows.Next() {
		var it ReservationListItem
		if err := rows.Scan(
			&it.ID, &it.ReservationCode, &it.TripID, &it.TripSeatID,
			&it.OriginTripStopTimeID, &it.DestinationTripStopTimeID,
			&it.Status, &it.ConfirmedAt,
			&it.TripCode, &it.ScheduledStartAt,
			&it.OriginDepartureAt,
			&it.OriginName, &it.DestinationName,
			&it.SeatLabel,
			&it.VehicleCode, &it.Plate,
		); err != nil {
			return nil, fmt.Errorf("escaneando reserva: %w", err)
		}
		items = append(items, it)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return items, nil
}

// GetReservationIdentity lee worker_id, trip_id y status de una reserva.
// Reserva inexistente → dberr.NotFound ("reserva ..."), igual que
// GetUserByIdentifier en el módulo auth.
func (r *bookingRepository) GetReservationIdentity(ctx context.Context, reservationID int64) (ReservationIdentity, error) {
	const q = `SELECT worker_id, trip_id, status FROM reservations WHERE id = ?`
	var ident ReservationIdentity
	err := r.db.QueryRowContext(ctx, q, reservationID).Scan(&ident.WorkerID, &ident.TripID, &ident.Status)
	if err != nil {
		if nfErr := dberr.NotFound(err, "reserva", reservationID); nfErr != err {
			return ReservationIdentity{}, nfErr
		}
		return ReservationIdentity{}, fmt.Errorf("buscando reserva por id: %w", err)
	}
	return ident, nil
}

// InsertIncident inserta en trip_incidents. Mismo INSERT que
// driverRepository.ReportIncident (módulo driver), pero el trip_id llega
// resuelto desde la reserva del pasajero.
func (r *bookingRepository) InsertIncident(ctx context.Context, tripID, reporterUserID int64, incidentType, description string) (int64, error) {
	const q = `INSERT INTO trip_incidents (trip_id, reported_by_user_id, incident_type, description) VALUES (?, ?, ?, ?)`
	res, err := r.db.ExecContext(ctx, q, tripID, reporterUserID, incidentType, description)
	if err != nil {
		return 0, fmt.Errorf("insertando incidencia de pasajero: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return 0, fmt.Errorf("leyendo id de incidencia: %w", err)
	}
	return id, nil
}

// GetJourneyState arma el estado de polling del pasajero: cronograma con su
// semáforo y, si corresponde, la oferta de extensión. La elegibilidad NO se
// persiste: se deriva en cada llamada.
func (r *bookingRepository) GetJourneyState(ctx context.Context, reservationID, workerID int64) (JourneyState, error) {
	const resQ = `
        SELECT worker_id, trip_id, trip_seat_id, destination_trip_stop_time_id,
               destination_stop_order, status
          FROM reservations
         WHERE id = ?`
	var ownerID, tripID, seatID, destStopTimeID int64
	var destOrder int
	var status string
	err := r.db.QueryRowContext(ctx, resQ, reservationID).Scan(
		&ownerID, &tripID, &seatID, &destStopTimeID, &destOrder, &status,
	)
	if err != nil {
		if nfErr := dberr.NotFound(err, "reserva", reservationID); nfErr != err {
			return JourneyState{}, nfErr
		}
		return JourneyState{}, fmt.Errorf("cargando reserva para journey: %w", err)
	}
	if ownerID != workerID {
		return JourneyState{}, apperror.NotFoundError{Entity: "reserva", ID: reservationID}
	}

	var tripStatus string
	if err := r.db.QueryRowContext(ctx,
		`SELECT status FROM trip_instances WHERE id = ?`, tripID).Scan(&tripStatus); err != nil {
		return JourneyState{}, fmt.Errorf("cargando estado del viaje: %w", err)
	}

	const stopsQ = `
        SELECT tst.id, tst.stop_id, ts.name, tst.stop_order,
               tst.scheduled_arrival_at, tst.scheduled_departure_at,
               tst.status, tst.actual_arrival_at, tst.actual_departure_at
          FROM trip_stop_times tst
          JOIN transport_stops ts ON ts.id = tst.stop_id
         WHERE tst.trip_id = ?
         ORDER BY tst.stop_order`
	rows, err := r.db.QueryContext(ctx, stopsQ, tripID)
	if err != nil {
		return JourneyState{}, fmt.Errorf("cargando paradas del journey: %w", err)
	}
	defer rows.Close()

	stops := make([]JourneyStop, 0)
	for rows.Next() {
		var s JourneyStop
		var arr, dep sql.NullTime
		if err := rows.Scan(&s.TripStopTimeID, &s.StopID, &s.StopName, &s.StopOrder,
			&s.ScheduledArrivalAt, &s.ScheduledDepartureAt, &s.Status, &arr, &dep); err != nil {
			return JourneyState{}, fmt.Errorf("escaneando parada del journey: %w", err)
		}
		if arr.Valid {
			t := arr.Time
			s.ActualArrivalAt = &t
		}
		if dep.Valid {
			t := dep.Time
			s.ActualDepartureAt = &t
		}
		stops = append(stops, s)
	}
	if err := rows.Err(); err != nil {
		return JourneyState{}, err
	}

	lastDepartedOrder := -1
	for _, s := range stops {
		if s.Status == "DEPARTED" && s.StopOrder > lastDepartedOrder {
			lastDepartedOrder = s.StopOrder
		}
	}
	var lastDeparted *int
	if lastDepartedOrder >= 0 {
		v := lastDepartedOrder
		lastDeparted = &v
	}

	state := JourneyState{
		ReservationID:         reservationID,
		ReservationStatus:     status,
		TripID:                tripID,
		TripStatus:            tripStatus,
		LastDepartedStopOrder: lastDeparted,
		DestinationStopOrder:  destOrder,
		Stops:                 stops,
	}

	if status != "BOARDED" || tripStatus != "IN_PROGRESS" || lastDeparted == nil ||
		destOrder != *lastDeparted+1 {
		return state, nil
	}

	remaining := make([]ExtensionStop, 0)
	for _, s := range stops {
		if s.StopOrder > destOrder && s.Status != "DEPARTED" {
			remaining = append(remaining, ExtensionStop{
				TripStopTimeID: s.TripStopTimeID,
				StopName:       s.StopName,
				StopOrder:      s.StopOrder,
			})
		}
	}
	if len(remaining) == 0 {
		return state, nil
	}

	seats, err := r.listSeatAvailability(ctx, tripID, destStopTimeID, remaining[0].TripStopTimeID)
	if err != nil {
		return JourneyState{}, err
	}
	anyFree := false
	currentSeatFree := false
	for _, s := range seats {
		if s.availability == "AVAILABLE" {
			anyFree = true
			if s.tripSeatID == seatID {
				currentSeatFree = true
			}
		}
	}
	if !anyFree {
		return state, nil
	}

	state.CanExtend = true
	state.Extension = &ExtensionOffer{CurrentSeatFree: currentSeatFree, RemainingStops: remaining}
	return state, nil
}

type seatAvailability struct {
	tripSeatID   int64
	seatLabel    string
	availability string
}

// listSeatAvailability reusa sp_list_trip_seats para un tramo.
func (r *bookingRepository) listSeatAvailability(ctx context.Context, tripID, originID, destID int64) ([]seatAvailability, error) {
	rows, err := r.db.QueryContext(ctx, "CALL sp_list_trip_seats(?, ?, ?)", tripID, originID, destID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return nil, spErr
		}
		return nil, fmt.Errorf("llamando sp_list_trip_seats: %w", err)
	}
	defer rows.Close()

	out := make([]seatAvailability, 0)
	for rows.Next() {
		var s seatAvailability
		var number int
		if err := rows.Scan(&s.tripSeatID, &number, &s.seatLabel, &s.availability); err != nil {
			return nil, fmt.Errorf("escaneando asientos del journey: %w", err)
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

// ExtendReservation llama a sp_extend_reservation.
func (r *bookingRepository) ExtendReservation(ctx context.Context, params ExtendParams) (ExtendResult, error) {
	var res ExtendResult
	err := r.db.QueryRowContext(ctx, "CALL sp_extend_reservation(?, ?, ?, ?)",
		params.ReservationID, params.WorkerID,
		params.NewDestinationTripStopTimeID, params.TripSeatID,
	).Scan(&res.ReservationID, &res.DestinationStopOrder, &res.TripSeatID,
		&res.SeatLabel, &res.Status)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return ExtendResult{}, spErr
		}
		return ExtendResult{}, fmt.Errorf("llamando sp_extend_reservation: %w", err)
	}
	return res, nil
}

// compile-time guard.
var _ BookingRepository = (*bookingRepository)(nil)
