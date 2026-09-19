// Tests de GET /driver/trips/{id} (driverService.GetTrip).
//
// Cubre las tres ramas del servicio: conductor asignado (200), conductor no
// asignado (403) y viaje inexistente (404). El mock de GetTripByID ya existia
// en service_test.go pero ningun test lo usaba.
package driver

import (
	"database/sql"
	"errors"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

func TestGetTrip_DriverAssigned_ReturnsTrip(t *testing.T) {
	repo := &mockDriverRepo{
		tripDriverID: 77,
		tripByID:     DriverTrip{ID: 233, TripCode: "T-TPL-20260922", Status: "PUBLISHED"},
	}
	svc := NewService(repo)

	trip, err := svc.GetTrip(ctxWithDriver(t, 77), 233)

	require.NoError(t, err)
	require.Equal(t, int64(233), trip.ID)
	require.Equal(t, "PUBLISHED", trip.Status)
}

// El conductor 78 pide un viaje asignado al 77: no debe verlo.
func TestGetTrip_DriverNotAssigned_ReturnsForbidden(t *testing.T) {
	repo := &mockDriverRepo{
		tripDriverID: 77,
		tripByID:     DriverTrip{ID: 233},
	}
	svc := NewService(repo)

	_, err := svc.GetTrip(ctxWithDriver(t, 78), 233)

	require.Error(t, err)
	var forbidden apperror.ForbiddenError
	require.True(t, errors.As(err, &forbidden),
		"se esperaba apperror.ForbiddenError (HTTP 403); se obtuvo %T: %v", err, err)
}

// El rol tambien se valida antes de mirar el viaje.
func TestGetTrip_NonDriverRole_Rejected(t *testing.T) {
	repo := &mockDriverRepo{tripDriverID: 77}
	svc := NewService(repo)

	_, err := svc.GetTrip(ctxWithRole(t, 77, "WORKER"), 233)

	require.Error(t, err)
	var forbidden apperror.ForbiddenError
	require.True(t, errors.As(err, &forbidden),
		"se esperaba apperror.ForbiddenError (HTTP 403); se obtuvo %T: %v", err, err)
}

func TestGetTrip_NotFound_ReturnsNotFound(t *testing.T) {
	repo := &mockDriverRepo{
		tripDriverID: 77,
		tripByIDErr:  sql.ErrNoRows,
	}
	svc := NewService(repo)

	_, err := svc.GetTrip(ctxWithDriver(t, 77), 999999)

	require.Error(t, err)
	var notFound apperror.NotFoundError
	require.True(t, errors.As(err, &notFound),
		"se esperaba apperror.NotFoundError (HTTP 404); se obtuvo %T: %v", err, err)
}
