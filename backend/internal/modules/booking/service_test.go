package booking

import (
	"context"
	"errors"
	"testing"

	"github.com/go-chi/jwtauth/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// mockBookingRepo cumple BookingRepository para tests. Mock a mano, sin mockery.
// Solo se rellenan los campos que cada test consume.
type mockBookingRepo struct {
	active      bool
	activeErr   error
	confirm     ConfirmResult
	confirmErr  error
	cancelErr   error
	verifyRes   Reservation
	verifyErr   error
	selfCheckin SelfCheckinResult
	selfCheckinErr error
}

func (m *mockBookingRepo) CheckActiveReservation(_ context.Context, _, _ int64) (bool, error) {
	return m.active, m.activeErr
}

func (m *mockBookingRepo) ConfirmReservation(_ context.Context, _ ConfirmParams) (ConfirmResult, error) {
	return m.confirm, m.confirmErr
}

func (m *mockBookingRepo) CancelReservation(_ context.Context, _, _ int64) error {
	return m.cancelErr
}

func (m *mockBookingRepo) VerifyQRToken(_ context.Context, _ string) (Reservation, error) {
	return m.verifyRes, m.verifyErr
}

func (m *mockBookingRepo) SelfCheckin(_ context.Context, _, _ int64) (SelfCheckinResult, error) {
	return m.selfCheckin, m.selfCheckinErr
}

func (m *mockBookingRepo) ListReservationsByWorker(_ context.Context, _ int64) ([]ReservationListItem, error) {
	return nil, nil
}

// ctxWithWorker construye un context que simula un JWT valido con el worker_id
// dado, tal como lo haria jwtauth.Verifier en produccion.
func ctxWithWorker(t *testing.T, workerID int64) context.Context {
	t.Helper()
	ja := jwtauth.New("HS256", []byte("booking-test-secret"), nil)
	claims := map[string]any{
		"user_id": float64(workerID), // JSON deserializa enteros a float64
		"role":    "WORKER",
	}
	token, _, err := ja.Encode(claims)
	require.NoError(t, err)
	return jwtauth.NewContext(context.Background(), token, nil)
}

func TestConfirm_Success_ReturnsQRToken(t *testing.T) {
	repo := &mockBookingRepo{
		active: false,
		confirm: ConfirmResult{
			ReservationID:   100,
			ReservationCode: "RES-100",
			QRToken:         "raw-qr-token-uuid",
			Status:          "CONFIRMED",
		},
	}
	svc := NewService(repo)

	resp, err := svc.Confirm(ctxWithWorker(t, 77), ConfirmRequest{
		TripID:                    1,
		TripSeatID:                2,
		OriginTripStopTimeID:      3,
		DestinationTripStopTimeID: 4,
	})
	require.NoError(t, err)
	assert.Equal(t, int64(100), resp.ReservationID)
	assert.Equal(t, "RES-100", resp.ReservationCode)
	assert.Equal(t, "raw-qr-token-uuid", resp.QRToken, "el QR crudo debe propagarse desde el SP")
	assert.Equal(t, "CONFIRMED", resp.Status)
}

func TestConfirm_AlreadyReserved_ReturnsConflict(t *testing.T) {
	// CheckActiveReservation devuelve true: el trabajador ya tiene una reserva
	// activa en el viaje y el servicio debe cortocircuitar con ConflictError.
	repo := &mockBookingRepo{active: true}
	svc := NewService(repo)

	_, err := svc.Confirm(ctxWithWorker(t, 77), ConfirmRequest{
		TripID:                    1,
		TripSeatID:                2,
		OriginTripStopTimeID:      3,
		DestinationTripStopTimeID: 4,
	})
	require.Error(t, err)
	var ce apperror.ConflictError
	require.True(t, errors.As(err, &ce), "reserva duplicada debe mapear a ConflictError")
}

func TestConfirm_SPReturnsQRToken(t *testing.T) {
	// Verifica especificamente que el qr_token de la respuesta proviene del
	// result set del SP (ConfirmResult.QRToken), no de un token generado en Go.
	// El servicio no hashea ni genera el QR: lo delega al SP y lo retorna crudo.
	repo := &mockBookingRepo{
		active: false,
		confirm: ConfirmResult{
			ReservationID:   200,
			ReservationCode: "RES-200",
			QRToken:         "sp-generated-qr-xyz",
			Status:          "CONFIRMED",
		},
	}
	svc := NewService(repo)

	resp, err := svc.Confirm(ctxWithWorker(t, 88), ConfirmRequest{
		TripID:                    5,
		TripSeatID:                6,
		OriginTripStopTimeID:      7,
		DestinationTripStopTimeID: 8,
	})
	require.NoError(t, err)
	assert.Equal(t, "sp-generated-qr-xyz", resp.QRToken)
	assert.Equal(t, int64(200), resp.ReservationID)
}
