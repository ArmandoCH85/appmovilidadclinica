package driver

import (
	"context"
	"errors"
	"testing"

	"github.com/go-chi/jwtauth/v5"
	"github.com/stretchr/testify/require"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// mockDriverRepo cumple DriverRepository para tests. Mock a mano, sin mockery.
// Se exponen campos por metodo para que cada test configure solo lo necesario.
type mockDriverRepo struct {
	driverTrips       []DriverTrip
	driverTripsErr    error
	passengers        []Passenger
	passengersErr     error
	tripByID          DriverTrip
	tripByIDErr       error
	stops             []TripStop
	stopsErr          error
	startTripErr      error
	completeTripErr   error
	tripDriverID      int64
	tripDriverErr     error
	stopTimeTripID    int64
	stopTimeTripErr   error
	reservationTrip   int64
	reservationErr    error
	markArrivalErr    error
	markDepartureErr  error
	markBoardedErr    error
	markNoShowErr     error
	markAlightedErr   error
	reportIncidentID  int64
	reportIncidentErr error
	guestID           int64
	guestErr          error
	guestCalled       bool
}

func (m *mockDriverRepo) GetDriverTrips(_ context.Context, _ int64, _ string) ([]DriverTrip, error) {
	return m.driverTrips, m.driverTripsErr
}

func (m *mockDriverRepo) GetTripByID(_ context.Context, _ int64) (DriverTrip, error) {
	return m.tripByID, m.tripByIDErr
}

func (m *mockDriverRepo) GetTripPassengers(_ context.Context, _ int64) ([]Passenger, error) {
	return m.passengers, m.passengersErr
}

func (m *mockDriverRepo) GetTripStops(_ context.Context, _ int64) ([]TripStop, error) {
	return m.stops, m.stopsErr
}

func (m *mockDriverRepo) StartTrip(_ context.Context, _ int64) error {
	return m.startTripErr
}

func (m *mockDriverRepo) CompleteTrip(_ context.Context, _, _ int64) error {
	return m.completeTripErr
}

func (m *mockDriverRepo) GetTripDriverID(_ context.Context, _ int64) (int64, error) {
	return m.tripDriverID, m.tripDriverErr
}

func (m *mockDriverRepo) GetTripStopTimeTripID(_ context.Context, _ int64) (int64, error) {
	return m.stopTimeTripID, m.stopTimeTripErr
}

func (m *mockDriverRepo) GetReservationTripID(_ context.Context, _ int64) (int64, error) {
	return m.reservationTrip, m.reservationErr
}

func (m *mockDriverRepo) MarkArrival(_ context.Context, _, _ int64) error {
	return m.markArrivalErr
}

func (m *mockDriverRepo) MarkDeparture(_ context.Context, _, _ int64) error {
	return m.markDepartureErr
}

func (m *mockDriverRepo) MarkBoarded(_ context.Context, _, _ int64) error {
	return m.markBoardedErr
}

func (m *mockDriverRepo) MarkNoShow(_ context.Context, _, _ int64) error {
	return m.markNoShowErr
}

func (m *mockDriverRepo) MarkAlighted(_ context.Context, _, _ int64) error {
	return m.markAlightedErr
}

func (m *mockDriverRepo) ReportIncident(_ context.Context, _ IncidentParams, _ int64) (int64, error) {
	return m.reportIncidentID, m.reportIncidentErr
}

func (m *mockDriverRepo) RegisterGuest(_ context.Context, _ GuestOccupantParams, _ int64) (int64, error) {
	m.guestCalled = true
	return m.guestID, m.guestErr
}

// ctxWithDriver construye un context que simula un JWT valido de rol DRIVER
// con el driver_id dado, tal como lo dejaria jwtauth.Verifier en produccion.
func ctxWithDriver(t *testing.T, driverID int64) context.Context {
	t.Helper()
	return ctxWithRole(t, driverID, RoleDRIVER)
}

// ctxWithRole construye un context que simula un JWT valido con el rol dado.
func ctxWithRole(t *testing.T, userID int64, role string) context.Context {
	t.Helper()
	ja := jwtauth.New("HS256", []byte("driver-test-secret"), nil)
	claims := map[string]any{
		"user_id": float64(userID),
		"role":    role,
	}
	token, _, err := ja.Encode(claims)
	require.NoError(t, err)
	return jwtauth.NewContext(context.Background(), token, nil)
}

func TestMarkArrival_DriverAssigned_Success(t *testing.T) {
	// El conductor 77 marca llegada a la parada 500. El repositorio resuelve:
	//   - stopTime 500 pertenece al viaje 10
	//   - viaje 10 tiene asignado al conductor 77 (coincide con el contexto)
	// MarcaArrival delega al SP sin error.
	repo := &mockDriverRepo{
		stopTimeTripID: 10,
		tripDriverID:   77,
	}
	svc := NewService(repo)

	err := svc.MarkArrival(ctxWithDriver(t, 77), 500)
	require.NoError(t, err)
}

func TestMarkDeparture_DriverAssigned_Success(t *testing.T) {
	// El conductor 77 marca salida de la parada 500. El repositorio resuelve:
	//   - stopTime 500 pertenece al viaje 10
	//   - viaje 10 tiene asignado al conductor 77 (coincide con el contexto)
	// MarkDeparture delega al SP sin error.
	repo := &mockDriverRepo{
		stopTimeTripID: 10,
		tripDriverID:   77,
	}
	svc := NewService(repo)

	err := svc.MarkDeparture(ctxWithDriver(t, 77), 500)
	require.NoError(t, err)
}

func TestMarkDeparture_DriverNotAssigned_ReturnsForbidden(t *testing.T) {
	// El conductor 77 marca salida en el viaje 10, pero ese viaje esta
	// asignado al conductor 999. El servicio valida asignacion antes de
	// delegar al SP y corta con ForbiddenError (403).
	repo := &mockDriverRepo{
		stopTimeTripID: 10,
		tripDriverID:   999,
	}
	svc := NewService(repo)

	err := svc.MarkDeparture(ctxWithDriver(t, 77), 500)
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "conductor no asignado debe mapear a ForbiddenError")
}

func TestMarkDeparture_NotDriver_Unauthorized(t *testing.T) {
	// Sin JWT de conductor: requireDriver corta con UnauthorizedError (401).
	repo := &mockDriverRepo{}
	svc := NewService(repo)

	err := svc.MarkDeparture(context.Background(), 500)
	require.Error(t, err)
	var ue apperror.UnauthorizedError
	require.True(t, errors.As(err, &ue), "sin JWT valido debe mapear a UnauthorizedError")
}

func TestMarkArrival_DriverNotAssigned_ReturnsForbidden(t *testing.T) {
	// El conductor 77 marca llegada en el viaje 10, pero ese viaje esta
	// asignado al conductor 999. El servicio valida asignacion antes de
	// delegar al SP y corta con ForbiddenError (403) en lugar de esperar
	// un SIGNAL 45000 del SP que se mapearia a 409.
	repo := &mockDriverRepo{
		stopTimeTripID: 10,
		tripDriverID:   999,
	}
	svc := NewService(repo)

	err := svc.MarkArrival(ctxWithDriver(t, 77), 500)
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "conductor no asignado debe mapear a ForbiddenError")
}

func TestMarkBoarded_Success(t *testing.T) {
	// El conductor 77 marca abordaje de la reserva 1000. El repositorio
	// resuelve el trip_id desde la reserva y valida asignacion del conductor.
	repo := &mockDriverRepo{
		reservationTrip: 10,
		tripDriverID:    77,
	}
	svc := NewService(repo)

	err := svc.MarkBoarded(ctxWithDriver(t, 77), 1000)
	require.NoError(t, err)
}

func TestMarkBoarded_DriverNotAssigned_ReturnsForbidden(t *testing.T) {
	// El conductor 77 intenta abordar en un viaje asignado a 999: 403.
	repo := &mockDriverRepo{
		reservationTrip: 10,
		tripDriverID:    999,
	}
	svc := NewService(repo)

	err := svc.MarkBoarded(ctxWithDriver(t, 77), 1000)
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "conductor no asignado debe mapear a ForbiddenError")
}

// Asegura que la asercion de compilacion del mock funcione (sin uso directo,
// evita "declared and not used" en campos no consumidos por estos tests).
var _ DriverRepository = (*mockDriverRepo)(nil)

func TestRegisterGuestOccupant_Success_ReturnsID(t *testing.T) {
	// El conductor 77 registra un invitado en su viaje 10. El viaje esta
	// asignado a 77 y el repo devuelve el id 700 del invitado creado.
	repo := &mockDriverRepo{
		tripDriverID: 77,
		guestID:      700,
	}
	svc := NewService(repo)
	req := RegisterGuestRequest{
		TripSeatID:                5,
		OriginTripStopTimeID:      11,
		DestinationTripStopTimeID: 14,
		FirstName:                 "Juan",
		LastName:                  "Perez",
	}

	id, err := svc.RegisterGuestOccupant(ctxWithDriver(t, 77), 10, req)
	require.NoError(t, err)
	require.Equal(t, int64(700), id)
	require.True(t, repo.guestCalled, "el repo debe llamarse cuando el conductor esta asignado")
}

func TestRegisterGuestOccupant_NotAssigned_ReturnsForbidden(t *testing.T) {
	// El conductor 77 registra un invitado en el viaje 10, pero ese viaje
	// esta asignado al conductor 999. El servicio corta con ForbiddenError
	// (403) antes de tocar el repositorio.
	repo := &mockDriverRepo{
		tripDriverID: 999,
		guestID:      700,
	}
	svc := NewService(repo)
	req := RegisterGuestRequest{
		TripSeatID:                5,
		OriginTripStopTimeID:      11,
		DestinationTripStopTimeID: 14,
		FirstName:                 "Juan",
		LastName:                  "Perez",
	}

	_, err := svc.RegisterGuestOccupant(ctxWithDriver(t, 77), 10, req)
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "conductor no asignado debe mapear a ForbiddenError")
	require.False(t, repo.guestCalled, "el repo no debe llamarse si el conductor no esta asignado")
}

func TestRegisterGuestOccupant_NonDriverRole_ReturnsForbidden(t *testing.T) {
	// Un JWT valido pero con rol WORKER intenta registrar un invitado:
	// requireDriver corta con ForbiddenError (403) sin tocar el repo.
	repo := &mockDriverRepo{
		tripDriverID: 77,
		guestID:      700,
	}
	svc := NewService(repo)
	req := RegisterGuestRequest{
		TripSeatID:                5,
		OriginTripStopTimeID:      11,
		DestinationTripStopTimeID: 14,
		FirstName:                 "Juan",
		LastName:                  "Perez",
	}

	_, err := svc.RegisterGuestOccupant(ctxWithRole(t, 77, "WORKER"), 10, req)
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "rol no DRIVER debe mapear a ForbiddenError")
	require.False(t, repo.guestCalled, "el repo no debe llamarse si el rol no es DRIVER")
}

func TestRegisterGuestOccupant_RepoConflict_BubblesUp(t *testing.T) {
	// El conductor esta asignado pero el SP rechaza (asiento ocupado en el
	// tramo o nombre duplicado en el viaje): el ConflictError del repo se
	// propaga tal cual al caller.
	repo := &mockDriverRepo{
		tripDriverID: 77,
		guestErr:     apperror.ConflictError{Msg: "El asiento ya esta ocupado en uno o mas tramos solicitados"},
	}
	svc := NewService(repo)
	req := RegisterGuestRequest{
		TripSeatID:                5,
		OriginTripStopTimeID:      11,
		DestinationTripStopTimeID: 14,
		FirstName:                 "Juan",
		LastName:                  "Perez",
	}

	_, err := svc.RegisterGuestOccupant(ctxWithDriver(t, 77), 10, req)
	require.Error(t, err)
	var ce apperror.ConflictError
	require.True(t, errors.As(err, &ce), "el conflicto del repo debe propagarse al caller")
	require.True(t, repo.guestCalled, "el repo debe haberse llamado")
}
