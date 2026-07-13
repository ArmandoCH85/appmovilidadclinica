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
	stops             []TripStop
	stopsErr          error
	tripDriverID      int64
	tripDriverErr     error
	stopTimeTripID    int64
	stopTimeTripErr   error
	reservationTrip   int64
	reservationErr    error
	markArrivalErr    error
	markBoardedErr    error
	markNoShowErr     error
	markAlightedErr   error
	reportIncidentID  int64
	reportIncidentErr error
}

func (m *mockDriverRepo) GetDriverTrips(_ context.Context, _ int64, _ string) ([]DriverTrip, error) {
	return m.driverTrips, m.driverTripsErr
}

func (m *mockDriverRepo) GetTripPassengers(_ context.Context, _ int64) ([]Passenger, error) {
	return m.passengers, m.passengersErr
}

func (m *mockDriverRepo) GetTripStops(_ context.Context, _ int64) ([]TripStop, error) {
	return m.stops, m.stopsErr
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

// ctxWithDriver construye un context que simula un JWT valido de rol DRIVER
// con el driver_id dado, tal como lo dejaria jwtauth.Verifier en produccion.
func ctxWithDriver(t *testing.T, driverID int64) context.Context {
	t.Helper()
	ja := jwtauth.New("HS256", []byte("driver-test-secret"), nil)
	claims := map[string]any{
		"user_id": float64(driverID),
		"role":    RoleDRIVER,
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
