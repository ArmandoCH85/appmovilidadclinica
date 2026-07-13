package driver

import (
	"context"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/authctx"
)

// RoleDRIVER es la constante del rol que puede acceder a las operaciones del
// conductor.
const RoleDRIVER = "DRIVER"

// DriverService define las operaciones de dominio del modulo driver. Cada
// metodo valida que el caller sea un DRIVER y que este asignado al viaje
// sobre el que opera; el resto es delegacion al repositorio. Las
// transiciones de estado se validan dentro de los SPs (la BD es la fuente de
// verdad para la secuencia CONFIRMED -> BOARDED -> COMPLETED / NO_SHOW).
type DriverService interface {
	ListTrips(ctx context.Context, serviceDate string) ([]DriverTrip, error)
	ListPassengers(ctx context.Context, tripID int64) ([]Passenger, error)
	ListTripStops(ctx context.Context, tripID int64) ([]TripStop, error)
	StartTrip(ctx context.Context, tripID int64) error
	CompleteTrip(ctx context.Context, tripID int64) error
	MarkArrival(ctx context.Context, tripStopTimeID int64) error
	MarkBoarded(ctx context.Context, reservationID int64) error
	MarkNoShow(ctx context.Context, reservationID int64) error
	MarkAlighted(ctx context.Context, reservationID int64) error
	ReportIncident(ctx context.Context, p IncidentParams) (int64, error)
}

// driverService es la implementacion concreta.
type driverService struct {
	repo DriverRepository
}

// NewService construye el servicio con su repositorio inyectado.
func NewService(repo DriverRepository) DriverService {
	return &driverService{repo: repo}
}

// requireDriver extrae y valida que el caller del contexto tenga rol DRIVER.
// Devuelve el user_id (que es el driver_id en trip_instances.driver_id) para
// que cada metodo lo use en la validacion de asignacion. Devuelve
// UnauthorizedError si no hay claims y ForbiddenError si el rol no es DRIVER.
func requireDriver(ctx context.Context) (int64, error) {
	driverID, err := authctx.UserIDFromContext(ctx)
	if err != nil {
		return 0, apperror.UnauthorizedError{Reason: "token sin identidad de conductor"}
	}
	role, err := authctx.RoleFromContext(ctx)
	if err != nil {
		return 0, apperror.UnauthorizedError{Reason: "token sin rol"}
	}
	if role != RoleDRIVER {
		return 0, apperror.ForbiddenError{Reason: "solo el rol DRIVER puede acceder al modulo del conductor"}
	}
	return driverID, nil
}

// ensureAssigned valida que el driverID del contexto coincida con el
// driver_id asignado al viaje tripID. Devuelve ForbiddenError si no coincide
// (el SP tambien lo validaria, pero fallamos temprano con 403 claro en
// lugar de dejar que el SP emita un SIGNAL 45000 que se mapea a 409).
func (s *driverService) ensureAssigned(ctx context.Context, driverID, tripID int64) error {
	assigned, err := s.repo.GetTripDriverID(ctx, tripID)
	if err != nil {
		return err
	}
	if assigned != driverID {
		return apperror.ForbiddenError{Reason: "el conductor no esta asignado a este viaje"}
	}
	return nil
}

// ListTrips lista los viajes del conductor autenticado para la fecha dada.
func (s *driverService) ListTrips(ctx context.Context, serviceDate string) ([]DriverTrip, error) {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return nil, err
	}
	return s.repo.GetDriverTrips(ctx, driverID, serviceDate)
}

// ListPassengers lista los pasajeros de un viaje. Valida que el conductor
// este asignado al viaje antes de exponer la lista.
func (s *driverService) ListPassengers(ctx context.Context, tripID int64) ([]Passenger, error) {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return nil, err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return nil, err
	}
	return s.repo.GetTripPassengers(ctx, tripID)
}

// ListTripStops lista el cronograma de paradas de un viaje. Valida que el
// conductor este asignado al viaje antes de exponer la lista.
func (s *driverService) ListTripStops(ctx context.Context, tripID int64) ([]TripStop, error) {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return nil, err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return nil, err
	}
	return s.repo.GetTripStops(ctx, tripID)
}

// StartTrip pasa el viaje a IN_PROGRESS. Valida que el conductor este
// asignado antes de delegar al repositorio.
func (s *driverService) StartTrip(ctx context.Context, tripID int64) error {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return err
	}
	return s.repo.StartTrip(ctx, tripID)
}

// CompleteTrip pasa el viaje a COMPLETED. Mismo flujo de validacion que
// StartTrip.
func (s *driverService) CompleteTrip(ctx context.Context, tripID int64) error {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return err
	}
	return s.repo.CompleteTrip(ctx, tripID)
}

// MarkArrival marca la llegada del conductor a una parada. Se valida la
// asignacion del conductor resolviendo el trip_id desde el trip_stop_time_id;
// el SP sp_mark_trip_stop_arrival hace la validacion final de estado.
func (s *driverService) MarkArrival(ctx context.Context, tripStopTimeID int64) error {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return err
	}
	tripID, err := s.repo.GetTripStopTimeTripID(ctx, tripStopTimeID)
	if err != nil {
		return err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return err
	}
	return s.repo.MarkArrival(ctx, tripStopTimeID, driverID)
}

// MarkBoarded marca el abordaje de un pasajero. Se valida la asignacion del
// conductor resolviendo el trip_id desde el reservation_id.
func (s *driverService) MarkBoarded(ctx context.Context, reservationID int64) error {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return err
	}
	tripID, err := s.repo.GetReservationTripID(ctx, reservationID)
	if err != nil {
		return err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return err
	}
	return s.repo.MarkBoarded(ctx, reservationID, driverID)
}

// MarkNoShow marca un pasajero como no presentado. Mismo flujo de validacion
// que MarkBoarded: resolver trip_id desde la reserva y validar asignacion.
func (s *driverService) MarkNoShow(ctx context.Context, reservationID int64) error {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return err
	}
	tripID, err := s.repo.GetReservationTripID(ctx, reservationID)
	if err != nil {
		return err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return err
	}
	return s.repo.MarkNoShow(ctx, reservationID, driverID)
}

// MarkAlighted marca la bajada de un pasajero en su destino. Mismo flujo de
// validacion que MarkBoarded.
func (s *driverService) MarkAlighted(ctx context.Context, reservationID int64) error {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return err
	}
	tripID, err := s.repo.GetReservationTripID(ctx, reservationID)
	if err != nil {
		return err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return err
	}
	return s.repo.MarkAlighted(ctx, reservationID, driverID)
}

// ReportIncident registra una incidencia reportada por el conductor. La
// validacion de asignacion se hace sobre el trip_id del cuerpo de la
// peticion (IncidentParams.TripID).
func (s *driverService) ReportIncident(ctx context.Context, p IncidentParams) (int64, error) {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return 0, err
	}
	if err := s.ensureAssigned(ctx, driverID, p.TripID); err != nil {
		return 0, err
	}
	return s.repo.ReportIncident(ctx, p, driverID)
}

// compile-time guard.
var _ DriverService = (*driverService)(nil)
