// Package trips implementa el modulo de consulta de viajes: busqueda,
// detalle y listado de asientos disponibles. Sigue la arquitectura de 3
// capas; el repositorio es la unica puerta a la BD y llama a los SPs de
// consulta del schema (sp_search_trips, sp_list_trip_seats).
package trips

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/dberr"
)

// TripSearchResult refleja el result set de sp_search_trips. Los alias del
// SELECT del SP mapean 1:1 con estos campos via Scan.
type TripSearchResult struct {
	TripID               int64     `json:"trip_id"`
	TripCode             string    `json:"trip_code"`
	RouteCode            string    `json:"route_code"`
	RouteName            string    `json:"route_name"`
	Direction            string    `json:"direction"`
	OriginOrder          int       `json:"origin_order"`
	OriginName           string    `json:"origin_name"`
	OriginDepartureAt    time.Time `json:"origin_departure_at"`
	DestinationOrder     int       `json:"destination_order"`
	DestinationName      string    `json:"destination_name"`
	DestinationArrivalAt time.Time `json:"destination_arrival_at"`
	VehicleCode          string    `json:"vehicle_code"`
	Plate                string    `json:"plate"`
	BookingOpensAt       time.Time `json:"booking_opens_at"`
	BookingClosesAt      time.Time `json:"booking_closes_at"`
	BookingState         string    `json:"booking_state"`
	AvailableSeats       int       `json:"available_seats"`
}

// SeatResult refleja el result set de sp_list_trip_seats.
type SeatResult struct {
	TripSeatID   int64  `json:"trip_seat_id"`
	SeatNumber   int    `json:"seat_number"`
	SeatLabel    string `json:"seat_label"`
	Availability string `json:"availability"`
}

// TripDetail son los datos de cabecera de un viaje para GET /trips/{id}.
type TripDetail struct {
	ID                     int64      `json:"id"`
	TripCode               string     `json:"trip_code"`
	Source                 string     `json:"source"`
	TripTemplateID         *int64     `json:"trip_template_id,omitempty"`
	RouteID                int64      `json:"route_id"`
	ServiceDate            string     `json:"service_date"`
	ScheduledStartAt       time.Time  `json:"scheduled_start_at"`
	ScheduledEndAt         time.Time  `json:"scheduled_end_at"`
	BookingOpensAt         time.Time  `json:"booking_opens_at"`
	BookingClosesAt        time.Time  `json:"booking_closes_at"`
	VehicleID              int64      `json:"vehicle_id"`
	DriverID               int64      `json:"driver_id"`
	SeatCapacitySnapshot   int        `json:"seat_capacity_snapshot"`
	NoShowToleranceMinutes int        `json:"no_show_tolerance_minutes"`
	Status                 string     `json:"status"`
	ActualStartAt          *time.Time `json:"actual_start_at,omitempty"`
	ActualEndAt            *time.Time `json:"actual_end_at,omitempty"`
	CancellationReason     *string    `json:"cancellation_reason,omitempty"`
}

// TripStopDetail es una parada del cronograma calculado del viaje.
type TripStopDetail struct {
	ID                   int64      `json:"id"`
	StopID               int64      `json:"stop_id"`
	StopOrder            int        `json:"stop_order"`
	ScheduledArrivalAt   time.Time  `json:"scheduled_arrival_at"`
	ScheduledDepartureAt time.Time  `json:"scheduled_departure_at"`
	ActualArrivalAt      *time.Time `json:"actual_arrival_at,omitempty"`
	ActualDepartureAt    *time.Time `json:"actual_departure_at,omitempty"`
	Status               string     `json:"status"`
	StopName             string     `json:"stop_name"`
	StopType             string     `json:"stop_type"`
}

// Stop refleja una fila de transport_stops para el endpoint publico
// `GET /api/stops`. Solo expone los campos que la app de pasajero necesita
// para los selectores de origen/destino en la pantalla de busqueda; el
// detalle completo (reference_text, lat/lon, active) queda reservado al
// CRUD de `/admin/stops`. Duplicar este tipo evita acoplar el modulo trips
// al modulo admin.
type Stop struct {
	ID       int64  `json:"id"`
	Code     string `json:"code"`
	Name     string `json:"name"`
	StopType string `json:"stop_type"`
}

// TripsRepository abstrae las consultas de viajes.
type TripsRepository interface {
	SearchTrips(ctx context.Context, serviceDate, direction string, originStopID, destStopID int64) ([]TripSearchResult, error)
	ListTripSeats(ctx context.Context, tripID, originStopTimeID, destStopTimeID int64) ([]SeatResult, error)
	GetTripDetail(ctx context.Context, tripID int64) (TripDetail, []TripStopDetail, error)
	// ListStops devuelve el catalogo completo de paradas para cualquier
	// caller autenticado. Sin paginar — el catalogo es chico (ver
	// `desarrollo_pasajero.md` §5.2). A diferencia de `/admin/stops`,
	// no exige rol ADMIN.
	ListStops(ctx context.Context) ([]Stop, error)
}

// tripsRepository es la implementacion concreta con database/sql.
type tripsRepository struct {
	db *sql.DB
}

// NewRepository construye el repositorio.
func NewRepository(db *sql.DB) TripsRepository {
	return &tripsRepository{db: db}
}

// SearchTrips llama a sp_search_trips y escanea el result set. El SP filtra
// por fecha, direccion, paradas y estado PUBLISHED; aqui solo se pasan los
// parametros y se mapea el resultado.
func (r *tripsRepository) SearchTrips(ctx context.Context, serviceDate, direction string, originStopID, destStopID int64) ([]TripSearchResult, error) {
	rows, err := r.db.QueryContext(ctx, "CALL sp_search_trips(?, ?, ?, ?)", serviceDate, direction, originStopID, destStopID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return nil, spErr
		}
		return nil, fmt.Errorf("llamando sp_search_trips: %w", err)
	}
	defer rows.Close()

	var results []TripSearchResult
	for rows.Next() {
		var t TripSearchResult
		if err := rows.Scan(
			&t.TripID, &t.TripCode, &t.RouteCode, &t.RouteName, &t.Direction,
			&t.OriginOrder, &t.OriginName, &t.OriginDepartureAt,
			&t.DestinationOrder, &t.DestinationName, &t.DestinationArrivalAt,
			&t.VehicleCode, &t.Plate, &t.BookingOpensAt, &t.BookingClosesAt,
			&t.BookingState, &t.AvailableSeats,
		); err != nil {
			return nil, fmt.Errorf("escaneando sp_search_trips: %w", err)
		}
		results = append(results, t)
	}
	return results, rows.Err()
}

// ListTripSeats llama a sp_list_trip_seats y devuelve la disponibilidad por
// asiento en el rango de paradas solicitado.
func (r *tripsRepository) ListTripSeats(ctx context.Context, tripID, originStopTimeID, destStopTimeID int64) ([]SeatResult, error) {
	rows, err := r.db.QueryContext(ctx, "CALL sp_list_trip_seats(?, ?, ?)", tripID, originStopTimeID, destStopTimeID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return nil, spErr
		}
		return nil, fmt.Errorf("llamando sp_list_trip_seats: %w", err)
	}
	defer rows.Close()

	var seats []SeatResult
	for rows.Next() {
		var s SeatResult
		if err := rows.Scan(&s.TripSeatID, &s.SeatNumber, &s.SeatLabel, &s.Availability); err != nil {
			return nil, fmt.Errorf("escaneando sp_list_trip_seats: %w", err)
		}
		seats = append(seats, s)
	}
	return seats, rows.Err()
}

// GetTripDetail carga la cabecera del viaje y su cronograma de paradas con
// dos SELECTs planos (sin SP). El primer SELECT trae trip_instances; el
// segundo, trip_stop_times + transport_stops.
func (r *tripsRepository) GetTripDetail(ctx context.Context, tripID int64) (TripDetail, []TripStopDetail, error) {
	const detailQ = `
        SELECT id, trip_code, source, trip_template_id, route_id, service_date,
               scheduled_start_at, scheduled_end_at, booking_opens_at,
               booking_closes_at, vehicle_id, driver_id, seat_capacity_snapshot,
               no_show_tolerance_minutes, status, actual_start_at, actual_end_at,
               cancellation_reason
          FROM trip_instances
         WHERE id = ?`

	var d TripDetail
	var tripTemplateID sql.NullInt64
	var actualStart, actualEnd sql.NullTime
	var cancellationReason sql.NullString
	err := r.db.QueryRowContext(ctx, detailQ, tripID).Scan(
		&d.ID, &d.TripCode, &d.Source, &tripTemplateID, &d.RouteID, &d.ServiceDate,
		&d.ScheduledStartAt, &d.ScheduledEndAt, &d.BookingOpensAt,
		&d.BookingClosesAt, &d.VehicleID, &d.DriverID, &d.SeatCapacitySnapshot,
		&d.NoShowToleranceMinutes, &d.Status, &actualStart, &actualEnd,
		&cancellationReason,
	)
	if err != nil {
		if nfErr := dberr.NotFound(err, "viaje", tripID); nfErr != err {
			return TripDetail{}, nil, nfErr
		}
		return TripDetail{}, nil, fmt.Errorf("cargando viaje: %w", err)
	}
	if tripTemplateID.Valid {
		v := tripTemplateID.Int64
		d.TripTemplateID = &v
	}
	if actualStart.Valid {
		t := actualStart.Time
		d.ActualStartAt = &t
	}
	if actualEnd.Valid {
		t := actualEnd.Time
		d.ActualEndAt = &t
	}
	if cancellationReason.Valid {
		s := cancellationReason.String
		d.CancellationReason = &s
	}

	const stopsQ = `
        SELECT tst.id, tst.stop_id, tst.stop_order, tst.scheduled_arrival_at,
               tst.scheduled_departure_at, tst.actual_arrival_at,
               tst.actual_departure_at, tst.status, ts.name, ts.stop_type
          FROM trip_stop_times tst
          JOIN transport_stops ts ON ts.id = tst.stop_id
         WHERE tst.trip_id = ?
         ORDER BY tst.stop_order`

	rows, err := r.db.QueryContext(ctx, stopsQ, tripID)
	if err != nil {
		return TripDetail{}, nil, fmt.Errorf("cargando paradas del viaje: %w", err)
	}
	defer rows.Close()

	var stops []TripStopDetail
	for rows.Next() {
		var s TripStopDetail
		var actualArr, actualDep sql.NullTime
		if err := rows.Scan(
			&s.ID, &s.StopID, &s.StopOrder, &s.ScheduledArrivalAt,
			&s.ScheduledDepartureAt, &actualArr, &actualDep, &s.Status,
			&s.StopName, &s.StopType,
		); err != nil {
			return TripDetail{}, nil, fmt.Errorf("escaneando paradas: %w", err)
		}
		if actualArr.Valid {
			t := actualArr.Time
			s.ActualArrivalAt = &t
		}
		if actualDep.Valid {
			t := actualDep.Time
			s.ActualDepartureAt = &t
		}
		stops = append(stops, s)
	}
	return d, stops, rows.Err()
}

// ListStops devuelve todas las paradas del catalogo. Sin paginar: el
// catalogo es chico (decenas de paraderos + sedes), y la app de pasajero
// necesita el set completo para los selectores de origen/destino. Devuelve
// slice vacio (nunca nil) si no hay filas, para que el JSON sea `[]` y
// no `null` — comportamiento consistente con el resto de los listados del
// API.
func (r *tripsRepository) ListStops(ctx context.Context) ([]Stop, error) {
	const q = `
        SELECT id, code, name, stop_type
          FROM transport_stops
         ORDER BY id`
	rows, err := r.db.QueryContext(ctx, q)
	if err != nil {
		return nil, fmt.Errorf("listando paradas: %w", err)
	}
	defer rows.Close()

	stops := make([]Stop, 0)
	for rows.Next() {
		var s Stop
		if err := rows.Scan(&s.ID, &s.Code, &s.Name, &s.StopType); err != nil {
			return nil, fmt.Errorf("escaneando parada: %w", err)
		}
		stops = append(stops, s)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return stops, nil
}

// compile-time guard: el repositorio concreto implementa la interfaz.
var _ TripsRepository = (*tripsRepository)(nil)
