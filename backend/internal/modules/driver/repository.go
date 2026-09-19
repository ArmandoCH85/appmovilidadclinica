// Package driver implementa el modulo del conductor: listado de viajes
// asignados, listado de pasajeros por viaje, marcado de llegada a parada,
// marcado de abordaje/no-show/bajada de pasajeros y reporte de incidencias.
//
// El repositorio es la unica puerta a la BD. Las transiciones de estado de
// reservas y paradas se delegan a SPs que ya validan que el conductor este
// asignado al viaje y respeten la secuencia de estados. El servicio solo
// agrega la validacion de asignacion del conductor (via authctx) antes de
// llamar al repositorio, para fallar temprano con 403 Forbidden.
package driver

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/dberr"
)

// DriverTrip refleja un viaje asignado a un conductor en una fecha concreta.
// Es el resultado del JOIN trip_instances + transport_routes + vehicles.
type DriverTrip struct {
	ID               int64     `json:"id"`
	TripCode         string    `json:"trip_code"`
	RouteID          int64     `json:"route_id"`
	RouteCode        string    `json:"route_code"`
	RouteName        string    `json:"route_name"`
	Direction        string    `json:"direction"`
	ServiceDate      string    `json:"service_date"`
	ScheduledStartAt time.Time `json:"scheduled_start_at"`
	ScheduledEndAt   time.Time `json:"scheduled_end_at"`
	VehicleID        int64     `json:"vehicle_id"`
	VehicleCode      string    `json:"vehicle_code"`
	Plate            string    `json:"plate"`
	SeatCapacity     int       `json:"seat_capacity_snapshot"`
	Status           string    `json:"status"`
}

// Passenger refleja un pasajero confirmado o abordado en un viaje. Une
// reservations + trip_seats + trip_stop_times para que el conductor vea
// nombre, asiento, parada de subida/bajada y estado.
type Passenger struct {
	ReservationID        int64      `json:"reservation_id"`
	ReservationCode      string     `json:"reservation_code"`
	WorkerID             int64      `json:"worker_id"`
	WorkerFullName       string     `json:"worker_full_name"`
	SeatNumber           int        `json:"seat_number"`
	SeatLabel            string     `json:"seat_label"`
	OriginStopOrder      int        `json:"origin_stop_order"`
	OriginStopName       string     `json:"origin_stop_name"`
	DestinationStopOrder int        `json:"destination_stop_order"`
	DestinationStopName  string     `json:"destination_stop_name"`
	Status               string     `json:"status"`
	ConfirmedAt          time.Time  `json:"confirmed_at"`
	BoardedAt            *time.Time `json:"boarded_at,omitempty"`
	IsGuest              bool       `json:"is_guest"`
	GuestDisplayName     string     `json:"guest_display_name,omitempty"`
}

// TripStop refleja una parada del cronograma de un viaje, con hora
// programada y real de llegada/salida. Es el resultado del JOIN
// trip_stop_times + transport_stops.
type TripStop struct {
	ID                   int64      `json:"id"`
	StopName             string     `json:"stop_name"`
	StopOrder            int        `json:"stop_order"`
	ScheduledArrivalAt   time.Time  `json:"scheduled_arrival_at"`
	ScheduledDepartureAt time.Time  `json:"scheduled_departure_at"`
	ActualArrivalAt      *time.Time `json:"actual_arrival_at,omitempty"`
	ActualDepartureAt    *time.Time `json:"actual_departure_at,omitempty"`
	Status               string     `json:"status"`
}

// IncidentParams agrupa los campos para insertar en trip_incidents.
type IncidentParams struct {
	TripID       int64  `json:"trip_id" validate:"required,gt=0"`
	IncidentType string `json:"incident_type" validate:"required,oneof=BREAKDOWN DELAY ACCIDENT OTHER"`
	Description  string `json:"description" validate:"required,max=1000"`
}

// GuestOccupantParams agrupa los campos para sp_register_guest_occupant.
type GuestOccupantParams struct {
	TripID                   int64
	TripSeatID               int64
	OriginTripStopTimeID     int64
	DestinationTripStopTimeID int64
	FirstName                string
	LastName                 string
}

// DriverRepository abstrae el acceso a BD del modulo driver.
type DriverRepository interface {
	// GetDriverTrips lista los viajes asignados a un conductor en una fecha.
	GetDriverTrips(ctx context.Context, driverID int64, serviceDate string) ([]DriverTrip, error)

	// GetTripPassengers lista los pasajeros CONFIRMED o BOARDED de un viaje.
	GetTripPassengers(ctx context.Context, tripID int64) ([]Passenger, error)

	// GetTripStops lista el cronograma de paradas de un viaje, ordenado por
	// stop_order.
	GetTripStops(ctx context.Context, tripID int64) ([]TripStop, error)

	// StartTrip pasa el viaje a IN_PROGRESS. Solo valido desde PUBLISHED o
	// BOARDING.
	StartTrip(ctx context.Context, tripID int64) error

	// CompleteTrip pasa el viaje a COMPLETED y cierra las reservas BOARDED
	// que quedaron arriba. Delega en sp_complete_trip.
	CompleteTrip(ctx context.Context, tripID, driverID int64) error

	// GetTripDriverID devuelve el driver_id asignado a un viaje. Usado por el
	// servicio para validar que el conductor que llama este asignado.
	GetTripDriverID(ctx context.Context, tripID int64) (int64, error)

	// GetTripStopTimeTripID devuelve el trip_id al que pertenece un
	// trip_stop_time. Usado por el servicio para validar asignacion del
	// conductor en MarkArrival.
	GetTripStopTimeTripID(ctx context.Context, tripStopTimeID int64) (int64, error)

	// GetReservationTripID devuelve el trip_id al que pertenece una reserva.
	// Usado por el servicio para validar asignacion del conductor en
	// MarkBoarded, MarkNoShow y MarkAlighted.
	GetReservationTripID(ctx context.Context, reservationID int64) (int64, error)

	// MarkArrival llama a sp_mark_trip_stop_arrival.
	MarkArrival(ctx context.Context, tripStopTimeID, driverID int64) error

	// MarkDeparture llama a sp_mark_trip_stop_departure.
	MarkDeparture(ctx context.Context, tripStopTimeID, driverID int64) error

	// MarkBoarded llama a sp_mark_reservation_boarded.
	MarkBoarded(ctx context.Context, reservationID, driverID int64) error

	// MarkNoShow llama a sp_mark_reservation_no_show.
	MarkNoShow(ctx context.Context, reservationID, driverID int64) error

	// MarkAlighted llama a sp_mark_reservation_alighted.
	MarkAlighted(ctx context.Context, reservationID, driverID int64) error

	// ReportIncident inserta una incidencia en trip_incidents y devuelve su id.
	ReportIncident(ctx context.Context, p IncidentParams, reporterUserID int64) (int64, error)

	// RegisterGuest llama a sp_register_guest_occupant y devuelve el id del
	// invitado creado. El SP valida tramo, asiento libre y nombre duplicado.
	RegisterGuest(ctx context.Context, p GuestOccupantParams, reporterUserID int64) (int64, error)
}

// driverRepository es la implementacion concreta con database/sql.
type driverRepository struct {
	db *sql.DB
}

// NewRepository construye el repositorio.
func NewRepository(db *sql.DB) DriverRepository {
	return &driverRepository{db: db}
}

// GetDriverTrips lista los viajes asignados al conductor en la fecha dada.
// Se filtra por status <> 'CANCELLED' para que el conductor no vea viajes
// cancelados en su lista operativa del dia.
func (r *driverRepository) GetDriverTrips(ctx context.Context, driverID int64, serviceDate string) ([]DriverTrip, error) {
	const q = `
        SELECT trip.id, trip.trip_code, trip.route_id, route.code, route.name,
               route.direction, trip.service_date, trip.scheduled_start_at,
               trip.scheduled_end_at, trip.vehicle_id, vehicle.internal_code,
               vehicle.plate, trip.seat_capacity_snapshot, trip.status
          FROM trip_instances trip
          JOIN transport_routes route ON route.id = trip.route_id
          JOIN vehicles vehicle ON vehicle.id = trip.vehicle_id
         WHERE trip.driver_id = ?
           AND trip.service_date = ?
           AND trip.status <> 'CANCELLED'
         ORDER BY trip.scheduled_start_at`
	rows, err := r.db.QueryContext(ctx, q, driverID, serviceDate)
	if err != nil {
		return nil, fmt.Errorf("listando viajes del conductor: %w", err)
	}
	defer rows.Close()

	var trips []DriverTrip
	for rows.Next() {
		var t DriverTrip
		if err := rows.Scan(&t.ID, &t.TripCode, &t.RouteID, &t.RouteCode,
			&t.RouteName, &t.Direction, &t.ServiceDate, &t.ScheduledStartAt,
			&t.ScheduledEndAt, &t.VehicleID, &t.VehicleCode, &t.Plate,
			&t.SeatCapacity, &t.Status); err != nil {
			return nil, fmt.Errorf("escaneando viaje del conductor: %w", err)
		}
		trips = append(trips, t)
	}
	return trips, rows.Err()
}

// GetTripPassengers lista los pasajeros CONFIRMED o BOARDED de un viaje. El
// JOIN a trip_stop_times trae los nombres de las paradas de subida/bajada y
// el JOIN a users el nombre del trabajador. Los ocupantes invitados
// (guest_occupants, sin app ni reserva) se unen con UNION: nacen BOARDED,
// con reservation_id/worker_id en 0 y el nombre concatenado como
// worker_full_name y guest_display_name.
func (r *driverRepository) GetTripPassengers(ctx context.Context, tripID int64) ([]Passenger, error) {
	const q = `
        SELECT id, reservation_code, worker_id, worker_full_name,
               seat_number, seat_label, origin_stop_order, origin_stop_name,
               destination_stop_order, destination_stop_name,
               status, confirmed_at, boarded_at, is_guest, guest_display_name
          FROM (
                SELECT reservation.id AS id, reservation.reservation_code AS reservation_code,
                       reservation.worker_id AS worker_id,
                       worker.full_name AS worker_full_name,
                       seat.seat_number AS seat_number, seat.seat_label AS seat_label,
                       reservation.origin_stop_order AS origin_stop_order,
                       origin_stop.stop_name AS origin_stop_name,
                       reservation.destination_stop_order AS destination_stop_order,
                       destination_stop.stop_name AS destination_stop_name,
                       reservation.status AS status,
                       reservation.confirmed_at AS confirmed_at, reservation.boarded_at AS boarded_at,
                       FALSE AS is_guest, '' AS guest_display_name
                  FROM reservations reservation
                  JOIN trip_seats seat ON seat.id = reservation.trip_seat_id
                  JOIN users worker ON worker.id = reservation.worker_id
                  JOIN (
                      SELECT tst.id, ts.name AS stop_name
                        FROM trip_stop_times tst
                        JOIN transport_stops ts ON ts.id = tst.stop_id
                  ) origin_stop ON origin_stop.id = reservation.origin_trip_stop_time_id
                  JOIN (
                      SELECT tst.id, ts.name AS stop_name
                        FROM trip_stop_times tst
                        JOIN transport_stops ts ON ts.id = tst.stop_id
                  ) destination_stop ON destination_stop.id = reservation.destination_trip_stop_time_id
                 WHERE reservation.trip_id = ?
                   AND reservation.status IN ('CONFIRMED', 'BOARDED')
                 UNION ALL
                SELECT 0 AS id, '' AS reservation_code, 0 AS worker_id,
                       CONCAT(guest.first_name, ' ', guest.last_name) AS worker_full_name,
                       seat.seat_number AS seat_number, seat.seat_label AS seat_label,
                       guest.origin_stop_order AS origin_stop_order,
                       origin_stop.stop_name AS origin_stop_name,
                       guest.destination_stop_order AS destination_stop_order,
                       destination_stop.stop_name AS destination_stop_name,
                       'BOARDED' AS status,
                       guest.created_at AS confirmed_at, guest.created_at AS boarded_at,
                       TRUE AS is_guest,
                       CONCAT(guest.first_name, ' ', guest.last_name) AS guest_display_name
                  FROM guest_occupants guest
                  JOIN trip_seats seat ON seat.id = guest.trip_seat_id
                  JOIN (
                      SELECT tst.id, ts.name AS stop_name
                        FROM trip_stop_times tst
                        JOIN transport_stops ts ON ts.id = tst.stop_id
                  ) origin_stop ON origin_stop.id = guest.origin_trip_stop_time_id
                  JOIN (
                      SELECT tst.id, ts.name AS stop_name
                        FROM trip_stop_times tst
                        JOIN transport_stops ts ON ts.id = tst.stop_id
                  ) destination_stop ON destination_stop.id = guest.destination_trip_stop_time_id
                 WHERE guest.trip_id = ?
                   AND guest.status = 'BOARDED'
               ) AS combined
         ORDER BY origin_stop_order, seat_number`
	rows, err := r.db.QueryContext(ctx, q, tripID, tripID)
	if err != nil {
		return nil, fmt.Errorf("listando pasajeros: %w", err)
	}
	defer rows.Close()

	var passengers []Passenger
	for rows.Next() {
		var p Passenger
		var boardedAt sql.NullTime
		if err := rows.Scan(&p.ReservationID, &p.ReservationCode, &p.WorkerID,
			&p.WorkerFullName, &p.SeatNumber, &p.SeatLabel,
			&p.OriginStopOrder, &p.OriginStopName,
			&p.DestinationStopOrder, &p.DestinationStopName,
			&p.Status, &p.ConfirmedAt, &boardedAt,
			&p.IsGuest, &p.GuestDisplayName); err != nil {
			return nil, fmt.Errorf("escaneando pasajero: %w", err)
		}
		if boardedAt.Valid {
			t := boardedAt.Time
			p.BoardedAt = &t
		}
		passengers = append(passengers, p)
	}
	return passengers, rows.Err()
}

// GetTripStops lista el cronograma de paradas de un viaje ordenado por
// stop_order. El JOIN a transport_stops trae el nombre de cada parada.
func (r *driverRepository) GetTripStops(ctx context.Context, tripID int64) ([]TripStop, error) {
	const q = `
        SELECT tst.id, ts.name, tst.stop_order, tst.scheduled_arrival_at,
               tst.scheduled_departure_at, tst.actual_arrival_at,
               tst.actual_departure_at, tst.status
          FROM trip_stop_times tst
          JOIN transport_stops ts ON ts.id = tst.stop_id
         WHERE tst.trip_id = ?
         ORDER BY tst.stop_order`
	rows, err := r.db.QueryContext(ctx, q, tripID)
	if err != nil {
		return nil, fmt.Errorf("listando paradas del viaje: %w", err)
	}
	defer rows.Close()

	var stops []TripStop
	for rows.Next() {
		var s TripStop
		var actualArrival, actualDeparture sql.NullTime
		if err := rows.Scan(&s.ID, &s.StopName, &s.StopOrder, &s.ScheduledArrivalAt,
			&s.ScheduledDepartureAt, &actualArrival, &actualDeparture, &s.Status); err != nil {
			return nil, fmt.Errorf("escaneando parada del viaje: %w", err)
		}
		if actualArrival.Valid {
			t := actualArrival.Time
			s.ActualArrivalAt = &t
		}
		if actualDeparture.Valid {
			t := actualDeparture.Time
			s.ActualDepartureAt = &t
		}
		stops = append(stops, s)
	}
	return stops, rows.Err()
}

// getTripStatus devuelve el status actual de un viaje. Uso interno para dar
// un mensaje de conflicto util cuando StartTrip/CompleteTrip rechazan la
// transicion por estado invalido.
func (r *driverRepository) getTripStatus(ctx context.Context, tripID int64) (string, error) {
	const q = `SELECT status FROM trip_instances WHERE id = ?`
	var status string
	err := r.db.QueryRowContext(ctx, q, tripID).Scan(&status)
	if err != nil {
		if nfErr := dberr.NotFound(err, "viaje", tripID); nfErr != err {
			return "", nfErr
		}
		return "", fmt.Errorf("obteniendo estado del viaje: %w", err)
	}
	return status, nil
}

// StartTrip pasa el viaje a IN_PROGRESS y registra actual_start_at, que es
// lo que alimenta los reportes de desviacion de duracion y de demora de salida
// (vw_duration_deviation / vw_delays_by_route_day filtran por esa columna).
// El UPDATE solo afecta filas en PUBLISHED o BOARDING; si no afecta ninguna, se
// resuelve el estado actual para devolver un ConflictError con mensaje util en
// vez de un 404 generico. COALESCE conserva el primer inicio si el viaje se
// reinicia.
func (r *driverRepository) StartTrip(ctx context.Context, tripID int64) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE trip_instances
           SET status = 'IN_PROGRESS',
               actual_start_at = COALESCE(actual_start_at, CURRENT_TIMESTAMP)
         WHERE id = ?
           AND status IN ('PUBLISHED', 'BOARDING')`, tripID)
	if err != nil {
		return fmt.Errorf("iniciando viaje: %w", err)
	}
	n, err := res.RowsAffected()
	if err != nil {
		return fmt.Errorf("verificando filas afectadas al iniciar viaje: %w", err)
	}
	if n == 0 {
		current, cerr := r.getTripStatus(ctx, tripID)
		if cerr != nil {
			return cerr
		}
		return apperror.ConflictError{Msg: fmt.Sprintf("el viaje esta en estado %s, no se puede iniciar", current)}
	}
	return nil
}

// CompleteTrip pasa el viaje a COMPLETED y cierra las reservas BOARDED que
// quedaron arriba. Toda la transicion (estado + liberacion de asientos) vive
// en sp_complete_trip para que sea atomica.
func (r *driverRepository) CompleteTrip(ctx context.Context, tripID, driverID int64) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_complete_trip(?, ?)", tripID, driverID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_complete_trip: %w", err)
	}
	return nil
}

// GetTripDriverID devuelve el driver_id de un viaje. El servicio lo usa para
// validar asignacion antes de autorizar cualquier operacion del conductor.
func (r *driverRepository) GetTripDriverID(ctx context.Context, tripID int64) (int64, error) {
	const q = `SELECT driver_id FROM trip_instances WHERE id = ?`
	var driverID int64
	err := r.db.QueryRowContext(ctx, q, tripID).Scan(&driverID)
	if err != nil {
		if nfErr := dberr.NotFound(err, "viaje", tripID); nfErr != err {
			return 0, nfErr
		}
		return 0, fmt.Errorf("obteniendo conductor del viaje: %w", err)
	}
	return driverID, nil
}

// GetTripStopTimeTripID devuelve el trip_id al que pertenece un
// trip_stop_time. El servicio lo usa para validar asignacion del conductor
// antes de marcar la llegada. La FK trip_stop_times.trip_id garantiza la
// existencia; sql.ErrNoRows se mapea a NotFoundError.
func (r *driverRepository) GetTripStopTimeTripID(ctx context.Context, tripStopTimeID int64) (int64, error) {
	const q = `SELECT trip_id FROM trip_stop_times WHERE id = ?`
	var tripID int64
	err := r.db.QueryRowContext(ctx, q, tripStopTimeID).Scan(&tripID)
	if err != nil {
		if nfErr := dberr.NotFound(err, "parada de viaje", tripStopTimeID); nfErr != err {
			return 0, nfErr
		}
		return 0, fmt.Errorf("obteniendo viaje de la parada: %w", err)
	}
	return tripID, nil
}

// GetReservationTripID devuelve el trip_id al que pertenece una reserva. El
// servicio lo usa para validar asignacion del conductor antes de marcar
// boarded/no-show/alighted.
func (r *driverRepository) GetReservationTripID(ctx context.Context, reservationID int64) (int64, error) {
	const q = `SELECT trip_id FROM reservations WHERE id = ?`
	var tripID int64
	err := r.db.QueryRowContext(ctx, q, reservationID).Scan(&tripID)
	if err != nil {
		if nfErr := dberr.NotFound(err, "reserva", reservationID); nfErr != err {
			return 0, nfErr
		}
		return 0, fmt.Errorf("obteniendo viaje de la reserva: %w", err)
	}
	return tripID, nil
}
func (r *driverRepository) MarkArrival(ctx context.Context, tripStopTimeID, driverID int64) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_mark_trip_stop_arrival(?, ?)", tripStopTimeID, driverID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_mark_trip_stop_arrival: %w", err)
	}
	return nil
}

func (r *driverRepository) MarkDeparture(ctx context.Context, tripStopTimeID, driverID int64) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_mark_trip_stop_departure(?, ?)", tripStopTimeID, driverID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_mark_trip_stop_departure: %w", err)
	}
	return nil
}

// MarkBoarded llama a sp_mark_reservation_boarded.
func (r *driverRepository) MarkBoarded(ctx context.Context, reservationID, driverID int64) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_mark_reservation_boarded(?, ?)", reservationID, driverID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_mark_reservation_boarded: %w", err)
	}
	return nil
}

// MarkNoShow llama a sp_mark_reservation_no_show.
func (r *driverRepository) MarkNoShow(ctx context.Context, reservationID, driverID int64) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_mark_reservation_no_show(?, ?)", reservationID, driverID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_mark_reservation_no_show: %w", err)
	}
	return nil
}

// MarkAlighted llama a sp_mark_reservation_alighted.
func (r *driverRepository) MarkAlighted(ctx context.Context, reservationID, driverID int64) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_mark_reservation_alighted(?, ?)", reservationID, driverID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_mark_reservation_alighted: %w", err)
	}
	return nil
}

// ReportIncident inserta una incidencia en trip_incidents. El
// reported_by_user_id es el conductor autenticado (extraido del JWT).
func (r *driverRepository) ReportIncident(ctx context.Context, p IncidentParams, reporterUserID int64) (int64, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO trip_incidents (trip_id, reported_by_user_id, incident_type, description)
        VALUES (?, ?, ?, ?)`,
		p.TripID, reporterUserID, p.IncidentType, p.Description)
	if err != nil {
		return 0, fmt.Errorf("creando incidencia: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return 0, fmt.Errorf("obteniendo id de incidencia: %w", err)
	}
	return id, nil
}

// RegisterGuest invoca sp_register_guest_occupant. Los SIGNAL '45000' del SP
// se traducen con el mismo tratamiento que el resto de SPs del módulo.
func (r *driverRepository) RegisterGuest(ctx context.Context, p GuestOccupantParams, reporterUserID int64) (int64, error) {
	var id int64
	err := r.db.QueryRowContext(ctx, "CALL sp_register_guest_occupant(?, ?, ?, ?, ?, ?, ?)",
		p.TripID, p.TripSeatID, p.OriginTripStopTimeID, p.DestinationTripStopTimeID,
		p.FirstName, p.LastName, reporterUserID,
	).Scan(&id)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return 0, spErr
		}
		return 0, fmt.Errorf("registrando ocupante invitado: %w", err)
	}
	return id, nil
}

// compile-time guard.
var _ DriverRepository = (*driverRepository)(nil)
